package com.example.chess.model

/**
 * Represents the color of a chess piece.
 * Each color also provides a convenience method to obtain the opposing color,
 * which is frequently needed during move validation and check detection.
 */
enum class PieceColor {
    WHITE,
    BLACK;

    /** Returns the opponent's color. */
    fun opposite(): PieceColor = when (this) {
        WHITE -> BLACK
        BLACK -> WHITE
    }
}

/**
 * Represents the type of a chess piece.
 * Each type corresponds to a standard chess piece with its unique movement rules.
 */
enum class PieceType {
    KING,
    QUEEN,
    ROOK,
    BISHOP,
    KNIGHT,
    PAWN
}

/**
 * Immutable position on the chessboard using zero-based row and column indices.
 * Row 0 corresponds to rank 1 (White's back rank), row 7 to rank 8 (Black's back rank).
 * Column 0 corresponds to the a-file, column 7 to the h-file.
 *
 * This class provides convenience methods for validity checking and algebraic notation
 * conversion, which are essential for move generation, FEN parsing, and UI display.
 */
data class Position(val row: Int, val col: Int) {

    /** Returns true if this position lies within the 0..7 bounds of an 8×8 board. */
    fun isValid(): Boolean = row in 0..7 && col in 0..7

    /** Converts this position to standard algebraic notation (e.g., "e4", "d7"). */
    fun toAlgebraic(): String {
        val file = 'a' + col
        val rank = '1' + row
        return "$file$rank"
    }

    companion object {
        /**
         * Parses a standard algebraic notation string into a Position.
         * For example, "e4" becomes Position(row=3, col=4).
         */
        fun fromAlgebraic(notation: String): Position {
            require(notation.length == 2) { "Algebraic notation must be exactly 2 characters, got: $notation" }
            val col = notation[0] - 'a'
            val row = notation[1] - '1'
            require(row in 0..7 && col in 0..7) { "Invalid algebraic notation: $notation" }
            return Position(row, col)
        }
    }
}

/**
 * Represents a single chess move from one position to another.
 *
 * The move can be a regular move, a capture, a pawn promotion, an en passant capture,
 * or a castling move. Each special move type is flagged explicitly so that the engine
 * and the UI can handle them correctly without ambiguous heuristics.
 *
 * @property from           The starting position of the moving piece.
 * @property to             The destination position.
 * @property promotionType  If this is a pawn promotion, the piece type the pawn promotes to.
 *                           Null for all non-promotion moves.
 * @property isEnPassant    True if this move is an en passant capture. The captured pawn
 *                           sits on a different square than the destination.
 * @property isCastling     True if this move is a castling maneuver (king moves two squares
 *                           toward a rook, and the rook jumps to the other side).
 */
data class Move(
    val from: Position,
    val to: Position,
    val promotionType: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastling: Boolean = false
)

/**
 * Immutable representation of a single chess piece with its type, color, and movement state.
 *
 * The [hasMoved] flag is essential for castling legality: both the king and the rook
 * must not have moved from their original squares for castling to be permitted.
 * It is also used to determine whether a pawn may advance two squares on its first move.
 *
 * The [imageUrl] property returns the full Wikimedia Commons SVG URL for this piece.
 * These are high-quality, standard chess piece vector images that are rendered by Coil
 * with the SVG decoder. The User-Agent header bypass (configured in ChessApplication)
 * is required because Wikimedia Commons returns HTTP 403 for requests without a
 * recognized browser User-Agent string.
 */
data class Piece(
    val type: PieceType,
    val color: PieceColor,
    val hasMoved: Boolean = false
) {
    /**
     * Returns the Wikimedia Commons SVG URL for this piece.
     *
     * Naming convention on Wikimedia:
     *   - "l" = light (white), "d" = dark (black)
     *   - "t45" = the standard 45-degree perspective tile set
     *   - Example: "Chess_klt45.svg" = King, Light, t45 perspective
     *
     * These URLs are stable, publicly accessible, and widely used in chess applications.
     */
    val imageUrl: String
        get() = when (color) {
            PieceColor.WHITE -> when (type) {
                PieceType.KING   -> "https://upload.wikimedia.org/wikipedia/commons/4/42/Chess_klt45.svg"
                PieceType.QUEEN  -> "https://upload.wikimedia.org/wikipedia/commons/1/15/Chess_qlt45.svg"
                PieceType.ROOK   -> "https://upload.wikimedia.org/wikipedia/commons/7/72/Chess_rlt45.svg"
                PieceType.BISHOP -> "https://upload.wikimedia.org/wikipedia/commons/b/b1/Chess_blt45.svg"
                PieceType.KNIGHT -> "https://upload.wikimedia.org/wikipedia/commons/7/70/Chess_nlt45.svg"
                PieceType.PAWN   -> "https://upload.wikimedia.org/wikipedia/commons/4/45/Chess_plt45.svg"
            }
            PieceColor.BLACK -> when (type) {
                PieceType.KING   -> "https://upload.wikimedia.org/wikipedia/commons/f/f0/Chess_kdt45.svg"
                PieceType.QUEEN  -> "https://upload.wikimedia.org/wikipedia/commons/4/47/Chess_qdt45.svg"
                PieceType.ROOK   -> "https://upload.wikimedia.org/wikipedia/commons/f/ff/Chess_rdt45.svg"
                PieceType.BISHOP -> "https://upload.wikimedia.org/wikipedia/commons/9/98/Chess_bdt45.svg"
                PieceType.KNIGHT -> "https://upload.wikimedia.org/wikipedia/commons/e/ef/Chess_ndt45.svg"
                PieceType.PAWN   -> "https://upload.wikimedia.org/wikipedia/commons/c/c7/Chess_pdt45.svg"
            }
        }
}

/**
 * Immutable representation of the full chessboard state at a given point in time.
 *
 * The board is stored as an 8×8 array of nullable [Piece] references. A null entry
 * indicates an empty square. The board is zero-indexed with row 0 = rank 1 (White's
 * back rank) and row 7 = rank 8 (Black's back rank).
 *
 * This class follows a strict immutability contract: every state-changing operation
 * (such as [applyMove]) returns a brand-new [ChessBoard] instance, leaving the original
 * untouched. This design is essential for:
 *   - Safe move validation (the engine can try moves on copies without mutating the real board)
 *   - Undo/redo support (previous board states are preserved)
 *   - Jetpack Compose recomposition (stable state objects prevent unnecessary recompositions)
 *
 * @property board            The 8×8 piece array. Accessed only through [getPiece] to enforce bounds safety.
 * @property enPassantTarget  The square that can be captured via en passant this turn, or null.
 *                             Set when an opponent pawn advances two squares; cleared on every subsequent move.
 * @property currentTurn      The color whose turn it is to move.
 */
class ChessBoard(
    private val board: Array<Array<Piece?>>,
    val enPassantTarget: Position? = null,
    val currentTurn: PieceColor = PieceColor.WHITE
) {
    /**
     * Retrieves the piece at the given [position], or null if the square is empty
     * or the position is out of bounds.
     */
    fun getPiece(position: Position): Piece? {
        if (!position.isValid()) return null
        return board[position.row][position.col]
    }

    /**
     * Creates a deep copy of this board with identical piece positions and state.
     * Each [Piece] is individually copied (they are data classes, so copy() is cheap).
     * The returned board is fully independent — mutations to one will not affect the other.
     */
    fun deepCopy(): ChessBoard {
        val copiedBoard = Array(8) { row ->
            Array(8) { col ->
                board[row][col]?.copy()
            }
        }
        return ChessBoard(copiedBoard, enPassantTarget, currentTurn)
    }

    /**
     * Applies the given [move] to a fresh copy of this board and returns the resulting state.
     *
     * This method handles all special move types:
     *   - **Regular moves & captures**: The piece moves from [Move.from] to [Move.to].
     *   - **Pawn promotion**: The pawn is replaced by the [Move.promotionType] piece.
     *   - **En passant**: The captured pawn (on an adjacent square) is removed.
     *   - **Castling**: Both the king and the rook are moved simultaneously.
     *
     * After the move:
     *   - [currentTurn] is toggled to the opposite color.
     *   - [enPassantTarget] is updated (set if a pawn double-advanced, cleared otherwise).
     *   - The moving piece's [Piece.hasMoved] flag is set to true.
     *
     * The original board is never modified.
     */
    fun applyMove(move: Move): ChessBoard {
        // Create a completely independent copy of the board array
        val newBoardArray = Array(8) { row ->
            Array(8) { col ->
                board[row][col]?.copy()
            }
        }

        // Local helpers for the new array — no ChessBoard wrapper needed here
        fun get(pos: Position): Piece? =
            if (pos.isValid()) newBoardArray[pos.row][pos.col] else null

        fun set(pos: Position, piece: Piece?) {
            if (pos.isValid()) newBoardArray[pos.row][pos.col] = piece
        }

        val piece = get(move.from) ?: return ChessBoard(newBoardArray, null, currentTurn.opposite())

        // ── En passant capture ───────────────────────────────────
        if (move.isEnPassant) {
            val capturedPawnRow = if (piece.color == PieceColor.WHITE) {
                move.to.row - 1
            } else {
                move.to.row + 1
            }
            set(Position(capturedPawnRow, move.to.col), null)
        }

        // ── Castling ─────────────────────────────────────────────
        if (move.isCastling) {
            // Move the king
            set(move.to, piece.copy(hasMoved = true))
            set(move.from, null)

            // Move the rook based on which side (kingside = col 6, queenside = col 2)
            if (move.to.col == 6) {
                // Kingside castling: rook moves from h-file (col 7) to f-file (col 5)
                val rook = get(Position(move.from.row, 7))
                set(Position(move.from.row, 5), rook?.copy(hasMoved = true))
                set(Position(move.from.row, 7), null)
            } else {
                // Queenside castling: rook moves from a-file (col 0) to d-file (col 3)
                val rook = get(Position(move.from.row, 0))
                set(Position(move.from.row, 3), rook?.copy(hasMoved = true))
                set(Position(move.from.row, 0), null)
            }
        } else {
            // ── Regular move or capture (including promotion) ──────
            val movedPiece = if (move.promotionType != null) {
                Piece(move.promotionType, piece.color, hasMoved = true)
            } else {
                piece.copy(hasMoved = true)
            }
            set(move.to, movedPiece)
            set(move.from, null)
        }

        // ── En passant target for the NEXT turn ──────────────────
        val newEnPassantTarget = if (
            piece.type == PieceType.PAWN &&
            kotlin.math.abs(move.to.row - move.from.row) == 2
        ) {
            // The square the pawn "passed through" is now capturable via en passant
            val enPassantRow = (move.from.row + move.to.row) / 2
            Position(enPassantRow, move.from.col)
        } else {
            null
        }

        return ChessBoard(newBoardArray, newEnPassantTarget, currentTurn.opposite())
    }

    /**
     * Finds the position of the king of the given [color] on the board.
     * Returns null if no king is found (should never happen in a valid game,
     * but can occur during engine unit tests or puzzle setup).
     */
    fun findKing(color: PieceColor): Position? {
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board[row][col]
                if (piece != null && piece.type == PieceType.KING && piece.color == color) {
                    return Position(row, col)
                }
            }
        }
        return null
    }

    /**
     * Returns a list of all positions occupied by pieces of the given [color].
     * Useful for iterating over all pieces during move generation.
     */
    fun getAllPiecesOfColor(color: PieceColor): List<Pair<Position, Piece>> {
        val result = mutableListOf<Pair<Position, Piece>>()
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board[row][col]
                if (piece != null && piece.color == color) {
                    result.add(Position(row, col) to piece)
                }
            }
        }
        return result
    }

    companion object {
        /**
         * Creates the standard starting position of a chess game.
         *
         * Layout (row 0 = rank 1, white's side):
         *   Row 0: R  N  B  Q  K  B  N  R   (White pieces)
         *   Row 1: P  P  P  P  P  P  P  P   (White pawns)
         *   Row 2–5: empty
         *   Row 6: p  p  p  p  p  p  p  p   (Black pawns)
         *   Row 7: r  n  b  q  k  b  n  r   (Black pieces)
         */
        fun initial(): ChessBoard {
            val board = Array(8) { arrayOfNulls<Piece>(8) }

            // White back rank
            board[0][0] = Piece(PieceType.ROOK, PieceColor.WHITE)
            board[0][1] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
            board[0][2] = Piece(PieceType.BISHOP, PieceColor.WHITE)
            board[0][3] = Piece(PieceType.QUEEN, PieceColor.WHITE)
            board[0][4] = Piece(PieceType.KING, PieceColor.WHITE)
            board[0][5] = Piece(PieceType.BISHOP, PieceColor.WHITE)
            board[0][6] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
            board[0][7] = Piece(PieceType.ROOK, PieceColor.WHITE)

            // White pawns
            for (col in 0..7) {
                board[1][col] = Piece(PieceType.PAWN, PieceColor.WHITE)
            }

            // Black pawns
            for (col in 0..7) {
                board[6][col] = Piece(PieceType.PAWN, PieceColor.BLACK)
            }

            // Black back rank
            board[7][0] = Piece(PieceType.ROOK, PieceColor.BLACK)
            board[7][1] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
            board[7][2] = Piece(PieceType.BISHOP, PieceColor.BLACK)
            board[7][3] = Piece(PieceType.QUEEN, PieceColor.BLACK)
            board[7][4] = Piece(PieceType.KING, PieceColor.BLACK)
            board[7][5] = Piece(PieceType.BISHOP, PieceColor.BLACK)
            board[7][6] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
            board[7][7] = Piece(PieceType.ROOK, PieceColor.BLACK)

            return ChessBoard(board, enPassantTarget = null, currentTurn = PieceColor.WHITE)
        }

        /**
         * Creates an empty board with no pieces.
         * Useful for constructing custom positions (puzzles, academy scenarios).
         */
        fun empty(): ChessBoard {
            val board = Array(8) { arrayOfNulls<Piece>(8) }
            return ChessBoard(board, enPassantTarget = null, currentTurn = PieceColor.WHITE)
        }

        /**
         * Creates a board from a given 8×8 piece layout.
         * Accepts a lambda that receives a mutable 8×8 array to fill in.
         *
         * Usage example:
         * ```
         * ChessBoard.fromSetup { b ->
         *     b[0][4] = Piece(PieceType.KING, PieceColor.WHITE)
         *     b[7][4] = Piece(PieceType.KING, PieceColor.BLACK)
         * }
         * ```
         */
        fun fromSetup(
            enPassantTarget: Position? = null,
            currentTurn: PieceColor = PieceColor.WHITE,
            setup: (Array<Array<Piece?>>) -> Unit
        ): ChessBoard {
            val board = Array(8) { arrayOfNulls<Piece>(8) }
            setup(board)
            return ChessBoard(board, enPassantTarget, currentTurn)
        }
    }
}
