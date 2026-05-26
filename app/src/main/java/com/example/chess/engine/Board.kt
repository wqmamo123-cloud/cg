package com.example.chess.engine

import com.example.chess.model.ChessBoard
import com.example.chess.model.Piece
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.model.Position

/**
 * Engine-internal, mutable 8×8 chessboard grid.
 *
 * This class provides direct, efficient read/write access to the squares of a
 * chess board. It is the primary board representation used by [ChessGame] and
 * the AI engine for state tracking, move execution, and position evaluation.
 *
 * Design rationale:
 *   - The model-layer [ChessBoard] is immutable and optimised for safe state
 *     snapshots and Compose recomposition. The engine layer, however, needs
 *     fast mutable access for move simulation, undo support, and AI search.
 *   - [Board] fills that role: it can be mutated in-place during move
 *     execution and copied cheaply when the AI needs to branch the search tree.
 *   - A conversion method [toChessBoard] bridges the two layers so that
 *     [MoveGenerator] (which operates on [ChessBoard]) can be reused without
 *     modification.
 *
 * Coordinate system:
 *   - Row 0 = rank 1 (White's back rank)
 *   - Row 7 = rank 8 (Black's back rank)
 *   - Col 0 = a-file, Col 7 = h-file
 */
class Board private constructor(
    private val grid: Array<Array<Piece?>>
) {

    /**
     * Returns the piece at the given [position], or null if the square is empty.
     * Returns null silently if the position is out of bounds.
     */
    fun getPiece(position: Position): Piece? {
        if (!position.isValid()) return null
        return grid[position.row][position.col]
    }

    /**
     * Returns the piece at the given [row] and [col], or null if the square
     * is empty or the coordinates are out of bounds.
     */
    fun getPiece(row: Int, col: Int): Piece? {
        if (row !in 0..7 || col !in 0..7) return null
        return grid[row][col]
    }

    /**
     * Places a piece (or clears a square by passing null) at the given [position].
     * No-op if the position is out of bounds.
     */
    fun setPiece(position: Position, piece: Piece?) {
        if (position.isValid()) {
            grid[position.row][position.col] = piece
        }
    }

    /**
     * Places a piece (or clears a square by passing null) at the given [row] and [col].
     * No-op if the coordinates are out of bounds.
     */
    fun setPiece(row: Int, col: Int, piece: Piece?) {
        if (row in 0..7 && col in 0..7) {
            grid[row][col] = piece
        }
    }

    /**
     * Creates a deep copy of this board. The returned board is fully independent —
     * mutations to one will not affect the other. Each [Piece] (a data class) is
     * individually copied so that hasMoved flags remain isolated.
     */
    fun copy(): Board {
        val copiedGrid = Array(8) { row ->
            Array(8) { col ->
                grid[row][col]?.copy()
            }
        }
        return Board(copiedGrid)
    }

    /**
     * Finds the position of the king of the given [color].
     * Returns null if no king is found (should not happen in a valid game,
     * but can occur during puzzle setup or testing).
     */
    fun findKing(color: PieceColor): Position? {
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = grid[row][col]
                if (piece != null && piece.type == PieceType.KING && piece.color == color) {
                    return Position(row, col)
                }
            }
        }
        return null
    }

    /**
     * Returns a list of all (Position, Piece) pairs for pieces of the given [color].
     * Useful for iterating over all pieces during move generation or evaluation.
     */
    fun getAllPiecesOfColor(color: PieceColor): List<Pair<Position, Piece>> {
        val result = mutableListOf<Pair<Position, Piece>>()
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = grid[row][col]
                if (piece != null && piece.color == color) {
                    result.add(Position(row, col) to piece)
                }
            }
        }
        return result
    }

    /**
     * Converts this engine-internal [Board] to an immutable [ChessBoard] that
     * can be consumed by [MoveGenerator]. The caller must supply the current
     * [enPassantTarget] and [currentTurn] because those are game-level concerns
     * tracked by [ChessGame], not by the grid itself.
     */
    fun toChessBoard(enPassantTarget: Position?, currentTurn: PieceColor): ChessBoard {
        val copiedGrid = Array(8) { row ->
            Array(8) { col ->
                grid[row][col]?.copy()
            }
        }
        return ChessBoard(copiedGrid, enPassantTarget, currentTurn)
    }

    /**
     * Generates a compact, unique string hash of the piece positions on the board.
     * Used for threefold repetition detection. The hash encodes:
     *   - Every piece's type and color at its exact position
     *   - Empty squares are represented by a dot
     *
     * This is intentionally simple and deterministic — it produces the same string
     * for the same piece arrangement regardless of how the position was reached.
     */
    fun positionHash(): String {
        val sb = StringBuilder(64)
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = grid[row][col]
                if (piece == null) {
                    sb.append('.')
                } else {
                    val colorChar = if (piece.color == PieceColor.WHITE) 'w' else 'b'
                    val typeChar = when (piece.type) {
                        PieceType.KING -> 'K'
                        PieceType.QUEEN -> 'Q'
                        PieceType.ROOK -> 'R'
                        PieceType.BISHOP -> 'B'
                        PieceType.KNIGHT -> 'N'
                        PieceType.PAWN -> 'P'
                    }
                    sb.append(colorChar)
                    sb.append(typeChar)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Returns the total material count on the board for the given [color],
     * using standard piece values. Useful for quick material assessment.
     */
    fun materialCount(color: PieceColor): Int {
        var total = 0
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = grid[row][col]
                if (piece != null && piece.color == color) {
                    total += when (piece.type) {
                        PieceType.PAWN -> 1
                        PieceType.KNIGHT -> 3
                        PieceType.BISHOP -> 3
                        PieceType.ROOK -> 5
                        PieceType.QUEEN -> 9
                        PieceType.KING -> 0
                    }
                }
            }
        }
        return total
    }

    companion object {
        /**
         * Creates a new Board with all 32 pieces arranged in the standard
         * starting position:
         *
         *   Row 0: R N B Q K B N R   (White back rank)
         *   Row 1: P P P P P P P P   (White pawns)
         *   Row 2-5: empty
         *   Row 6: p p p p p p p p   (Black pawns)
         *   Row 7: r n b q k b n r   (Black back rank)
         *
         * All pieces are created with hasMoved = false so that castling
         * and the two-square pawn advance are initially available.
         */
        fun createInitialBoard(): Board {
            val grid = Array(8) { arrayOfNulls<Piece>(8) }

            // White back rank
            grid[0][0] = Piece(PieceType.ROOK, PieceColor.WHITE)
            grid[0][1] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
            grid[0][2] = Piece(PieceType.BISHOP, PieceColor.WHITE)
            grid[0][3] = Piece(PieceType.QUEEN, PieceColor.WHITE)
            grid[0][4] = Piece(PieceType.KING, PieceColor.WHITE)
            grid[0][5] = Piece(PieceType.BISHOP, PieceColor.WHITE)
            grid[0][6] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
            grid[0][7] = Piece(PieceType.ROOK, PieceColor.WHITE)

            // White pawns
            for (col in 0..7) {
                grid[1][col] = Piece(PieceType.PAWN, PieceColor.WHITE)
            }

            // Black pawns
            for (col in 0..7) {
                grid[6][col] = Piece(PieceType.PAWN, PieceColor.BLACK)
            }

            // Black back rank
            grid[7][0] = Piece(PieceType.ROOK, PieceColor.BLACK)
            grid[7][1] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
            grid[7][2] = Piece(PieceType.BISHOP, PieceColor.BLACK)
            grid[7][3] = Piece(PieceType.QUEEN, PieceColor.BLACK)
            grid[7][4] = Piece(PieceType.KING, PieceColor.BLACK)
            grid[7][5] = Piece(PieceType.BISHOP, PieceColor.BLACK)
            grid[7][6] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
            grid[7][7] = Piece(PieceType.ROOK, PieceColor.BLACK)

            return Board(grid)
        }

        /**
         * Creates an empty board with no pieces.
         * Useful for constructing custom positions (puzzles, academy scenarios).
         */
        fun empty(): Board {
            return Board(Array(8) { arrayOfNulls(8) })
        }

        /**
         * Creates a Board from an immutable [ChessBoard] instance.
         * This is the inverse of [Board.toChessBoard] and is used when the
         * engine needs to work with a position that originated from the model layer.
         */
        fun fromChessBoard(chessBoard: ChessBoard): Board {
            val grid = Array(8) { row ->
                Array(8) { col ->
                    chessBoard.getPiece(Position(row, col))?.copy()
                }
            }
            return Board(grid)
        }
    }
}
