package com.example.chess.engine

import com.example.chess.model.ChessBoard
import com.example.chess.model.Move
import com.example.chess.model.Piece
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.model.Position

/**
 * Represents the overall result of a chess game.
 * This is distinct from the tactical [GameState] in MoveGenerator, which only
 * describes the current position's check/checkmate/stalemate status. [GameResult]
 * captures the final outcome from a game-management perspective.
 */
enum class GameResult {
    /** The game is still being played. */
    ACTIVE,

    /** White has checkmated Black. */
    WHITE_WON,

    /** Black has checkmated White. */
    BLACK_WON,

    /** The game has ended in a draw by one of the FIDE draw rules. */
    DRAW
}

/**
 * The specific reason a game ended in a draw.
 * Attached to [ChessGame.drawReason] when [GameResult] is [DRAW].
 */
enum class DrawReason {
    /** The side to move has no legal moves but is not in check. */
    STALEMATE,

    /** 50 moves have been made without a pawn move or capture (100 half-moves). */
    FIFTY_MOVE_RULE,

    /** The same position has occurred three times with the same side to move. */
    THREEFOLD_REPETITION,

    /** Neither side has sufficient material to checkmate. */
    INSUFFICIENT_MATERIAL
}

/**
 * Immutable snapshot of castling availability for both sides.
 *
 * Each flag tracks whether a particular castling manoeuvre is still possible.
 * A flag is permanently lost when:
 *   - The king of that colour moves (both sides lost)
 *   - The relevant rook moves from its original square (that side lost)
 *   - The relevant rook is captured on its original square (that side lost)
 *
 * These rights are essential for:
 *   - Move generation (MoveGenerator checks hasMoved on pieces)
 *   - Threefold repetition (castling rights are part of the position identity)
 *   - FEN-style position encoding
 */
data class CastlingRights(
    val whiteKingside: Boolean = true,
    val whiteQueenside: Boolean = true,
    val blackKingside: Boolean = true,
    val blackQueenside: Boolean = true
)

/**
 * Records all information needed to undo a single move.
 *
 * The undo system stores a complete snapshot of the game's mutable state before
 * each move, so that [ChessGame.undoMove] can restore the position exactly.
 * This is crucial for:
 *   - Player undo functionality in the UI
 *   - Threefold repetition detection (positions must be restorable)
 *   - Future features such as move review and analysis
 *
 * @property move                  The move that was executed.
 * @property capturedPiece         The piece that was captured, or null if no capture.
 *                                 For en passant, this is the pawn that was removed.
 * @property previousEnPassantTarget The en passant target square before this move.
 * @property previousCastlingRights  Castling rights before this move.
 * @property previousHalfMoveClock   The half-move clock before this move.
 * @property previousBoard            A copy of the board before this move was applied.
 */
data class MoveRecord(
    val move: Move,
    val capturedPiece: Piece?,
    val previousEnPassantTarget: Position?,
    val previousCastlingRights: CastlingRights,
    val previousHalfMoveClock: Int,
    val previousBoard: Board
)

/**
 * Core chess game state manager.
 *
 * [ChessGame] orchestrates the entire lifecycle of a chess game. It owns the
 * board, manages turns, executes and validates moves, tracks move history,
 * and enforces all FIDE rules including draw conditions.
 *
 * Architecture:
 *   - Board state is stored as a mutable [Board] instance for efficient
 *     in-place updates during move execution.
 *   - Move validation and generation are delegated to [MoveGenerator], which
 *     operates on immutable [ChessBoard] snapshots produced by [Board.toChessBoard].
 *   - This separation ensures the engine layer (mutable, fast) and the model
 *     layer (immutable, Compose-friendly) remain decoupled.
 *
 * Thread safety: This class is NOT thread-safe. If the AI runs on a background
 * thread, the caller must ensure that [executeMove] and [undoMove] are not
 * invoked concurrently. The AI search uses its own [ChessBoard] copies and
 * does not mutate this object.
 *
 * Typical usage:
 * ```
 * val game = ChessGame()
 * val legalMoves = game.getLegalMoves()
 * val bestMove = chessAI.findBestMove(game, AIDifficulty.MEDIUM)
 * game.executeMove(bestMove)
 * ```
 */
class ChessGame {

    // ──────────────────────────────────────────────────────────────
    //  Internal state
    // ──────────────────────────────────────────────────────────────

    private val board: Board = Board.createInitialBoard()
    private val moveGenerator: MoveGenerator = MoveGenerator()

    private var currentTurn: PieceColor = PieceColor.WHITE
    private var enPassantTarget: Position? = null
    private var castlingRights: CastlingRights = CastlingRights()
    private var halfMoveClock: Int = 0
    private var fullMoveNumber: Int = 1

    private val moveHistory: MutableList<MoveRecord> = mutableListOf()
    private val positionHistory: MutableList<String> = mutableListOf()

    private var gameResult: GameResult = GameResult.ACTIVE
    private var drawReason: DrawReason? = null

    // Record the initial position for threefold repetition
    private var initialPositionKey: String

    init {
        initialPositionKey = generatePositionKey()
        positionHistory.add(initialPositionKey)
    }

    // ──────────────────────────────────────────────────────────────
    //  Public read-only accessors
    // ──────────────────────────────────────────────────────────────

    /** The colour whose turn it is to move. */
    fun getCurrentTurn(): PieceColor = currentTurn

    /** The current game result. */
    fun getGameResult(): GameResult = gameResult

    /** If the game is a draw, the specific reason. Null otherwise. */
    fun getDrawReason(): DrawReason? = drawReason

    /** The total number of half-moves (plies) since the last pawn move or capture. */
    fun getHalfMoveClock(): Int = halfMoveClock

    /** The current full move number (starts at 1, increments after Black's move). */
    fun getFullMoveNumber(): Int = fullMoveNumber

    /** Current castling availability for both sides. */
    fun getCastlingRights(): CastlingRights = castlingRights

    /** The current en passant target square, or null if none. */
    fun getEnPassantTarget(): Position? = enPassantTarget

    /** The number of moves in the game's history. */
    fun getMoveCount(): Int = moveHistory.size

    /** A read-only view of all executed moves in chronological order. */
    fun getMoveHistory(): List<Move> = moveHistory.map { it.move }

    /** Returns the piece at the given position on the board, or null. */
    fun getPieceAt(position: Position): Piece? = board.getPiece(position)

    /** Returns the piece at the given row and column on the board, or null. */
    fun getPieceAt(row: Int, col: Int): Piece? = board.getPiece(row, col)

    /** Whether the game has ended (either a win or a draw). */
    fun isGameOver(): Boolean = gameResult != GameResult.ACTIVE

    /** Whether the current side to move is in check. */
    fun isInCheck(): Boolean {
        val chessBoard = board.toChessBoard(enPassantTarget, currentTurn)
        return moveGenerator.isInCheck(chessBoard, currentTurn)
    }

    // ──────────────────────────────────────────────────────────────
    //  Move generation
    // ──────────────────────────────────────────────────────────────

    /**
     * Generates all legal moves for the current side to move.
     *
     * Returns an empty list if the game is over (no moves can be made).
     * The list includes regular moves, captures, en passant, castling,
     * and all four promotion types when applicable.
     */
    fun getLegalMoves(): List<Move> {
        if (gameResult != GameResult.ACTIVE) return emptyList()
        val chessBoard = board.toChessBoard(enPassantTarget, currentTurn)
        return moveGenerator.generateLegalMoves(chessBoard, currentTurn)
    }

    /**
     * Generates legal moves originating from a specific [position].
     * Useful for the UI to highlight valid destination squares when a
     * piece is selected.
     */
    fun getLegalMovesFrom(position: Position): List<Move> {
        return getLegalMoves().filter { it.from == position }
    }

    /**
     * Returns the list of destination positions a piece at [from] can
     * legally move to. Convenience method for UI highlighting.
     */
    fun getValidDestinations(from: Position): List<Position> {
        return getLegalMovesFrom(from).map { it.to }
    }

    // ──────────────────────────────────────────────────────────────
    //  Move execution
    // ──────────────────────────────────────────────────────────────

    /**
     * Executes the given [move] on the board and updates all game state.
     *
     * This method performs the following steps:
     *   1. Validates that the move is legal for the current position.
     *   2. Records undo information (captured piece, previous state).
     *   3. Applies the move to the board, handling all special rules.
     *   4. Updates castling rights if a king or rook moved (or rook was captured).
     *   5. Updates the en passant target square.
     *   6. Updates the half-move clock (resets on pawn move or capture).
     *   7. Records the position for threefold repetition detection.
     *   8. Switches the turn to the opponent.
     *   9. Checks for game-ending conditions (checkmate, stalemate, draws).
     *
     * @return true if the move was executed, false if it was illegal or the game is over.
     */
    fun executeMove(move: Move): Boolean {
        if (gameResult != GameResult.ACTIVE) return false

        // Validate legality
        val legalMoves = getLegalMoves()
        val exactMatch = legalMoves.find {
            it.from == move.from &&
                it.to == move.to &&
                it.promotionType == move.promotionType
        }
        if (exactMatch == null) return false

        // Use the fully-resolved move (it may have isEnPassant or isCastling flags
        // that the caller did not set — the move generator always sets them correctly)
        val resolvedMove = exactMatch

        // ── Record undo information ──────────────────────────────
        val capturedPiece = determineCapturedPiece(resolvedMove)
        val record = MoveRecord(
            move = resolvedMove,
            capturedPiece = capturedPiece,
            previousEnPassantTarget = enPassantTarget,
            previousCastlingRights = castlingRights,
            previousHalfMoveClock = halfMoveClock,
            previousBoard = board.copy()
        )
        moveHistory.add(record)

        // ── Apply the move to the board ──────────────────────────
        applyMoveToBoard(resolvedMove)

        // ── Update castling rights ───────────────────────────────
        updateCastlingRights(resolvedMove)

        // ── Update en passant target ─────────────────────────────
        updateEnPassantTarget(resolvedMove)

        // ── Update half-move clock ───────────────────────────────
        val isPawnMove = board.getPiece(resolvedMove.to)?.type == PieceType.PAWN
        val isCapture = capturedPiece != null
        if (isPawnMove || isCapture) {
            halfMoveClock = 0
        } else {
            halfMoveClock++
        }

        // ── Switch turn ──────────────────────────────────────────
        if (currentTurn == PieceColor.BLACK) {
            fullMoveNumber++
        }
        currentTurn = currentTurn.opposite()

        // ── Record position for threefold repetition ─────────────
        val positionKey = generatePositionKey()
        positionHistory.add(positionKey)

        // ── Check game-ending conditions ─────────────────────────
        checkGameEnd()

        return true
    }

    /**
     * Undoes the last move, restoring the game to its previous state.
     *
     * Restores the board, turn, en passant target, castling rights,
     * half-move clock, and game result. The undone move is removed
     * from the history.
     *
     * @return true if a move was undone, false if there is no history.
     */
    fun undoMove(): Boolean {
        if (moveHistory.isEmpty()) return false

        val record = moveHistory.removeAt(moveHistory.lastIndex)

        // Restore the board from the snapshot (more reliable than reverse-applying)
        restoreBoardFromSnapshot(record.previousBoard)

        // Restore all tracked state
        currentTurn = if (record.move.from.isValid()) {
            // Determine whose turn it was before the move
            val movedPiece = record.previousBoard.getPiece(record.move.from)
            movedPiece?.color ?: currentTurn.opposite()
        } else {
            currentTurn.opposite()
        }

        enPassantTarget = record.previousEnPassantTarget
        castlingRights = record.previousCastlingRights
        halfMoveClock = record.previousHalfMoveClock

        // Adjust full move number
        if (currentTurn == PieceColor.BLACK) {
            fullMoveNumber--
        }

        // Remove the position from history
        if (positionHistory.isNotEmpty()) {
            positionHistory.removeAt(positionHistory.lastIndex)
        }

        // Restore game result to ACTIVE (undoing a move reopens the game)
        gameResult = GameResult.ACTIVE
        drawReason = null

        return true
    }

    /**
     * Returns an immutable [ChessBoard] snapshot of the current game position.
     * This is used by the AI engine and for Compose state management.
     */
    fun toChessBoard(): ChessBoard {
        return board.toChessBoard(enPassantTarget, currentTurn)
    }

    // ──────────────────────────────────────────────────────────────
    //  Move execution internals
    // ──────────────────────────────────────────────────────────────

    /**
     * Applies the given [move] to the board, handling all special rules:
     *   - Regular moves and captures
     *   - Castling (both king and rook are moved)
     *   - En passant (the captured pawn is removed from the correct square)
     *   - Pawn promotion (the pawn is replaced by the promoted piece)
     */
    private fun applyMoveToBoard(move: Move) {
        val piece = board.getPiece(move.from) ?: return

        if (move.isCastling) {
            // Move the king
            board.setPiece(move.to, piece.copy(hasMoved = true))
            board.setPiece(move.from, null)

            // Move the rook
            if (move.to.col == 6) {
                // Kingside: rook from h-file to f-file
                val rook = board.getPiece(move.from.row, 7)
                board.setPiece(move.from.row, 5, rook?.copy(hasMoved = true))
                board.setPiece(move.from.row, 7, null)
            } else {
                // Queenside: rook from a-file to d-file
                val rook = board.getPiece(move.from.row, 0)
                board.setPiece(move.from.row, 3, rook?.copy(hasMoved = true))
                board.setPiece(move.from.row, 0, null)
            }
        } else if (move.isEnPassant) {
            // Move the pawn
            board.setPiece(move.to, piece.copy(hasMoved = true))
            board.setPiece(move.from, null)

            // Remove the captured pawn (it sits on the same file as the destination,
            // but on the moving pawn's original rank)
            val capturedPawnRow = if (piece.color == PieceColor.WHITE) {
                move.to.row - 1
            } else {
                move.to.row + 1
            }
            board.setPiece(capturedPawnRow, move.to.col, null)
        } else if (move.promotionType != null) {
            // Replace the pawn with the promoted piece
            val promotedPiece = Piece(move.promotionType, piece.color, hasMoved = true)
            board.setPiece(move.to, promotedPiece)
            board.setPiece(move.from, null)
        } else {
            // Regular move or capture
            board.setPiece(move.to, piece.copy(hasMoved = true))
            board.setPiece(move.from, null)
        }
    }

    /**
     * Determines which piece (if any) is captured by the given [move].
     * For en passant captures, the captured pawn is on a different square
     * than the destination.
     */
    private fun determineCapturedPiece(move: Move): Piece? {
        if (move.isEnPassant) {
            val piece = board.getPiece(move.from) ?: return null
            val capturedPawnRow = if (piece.color == PieceColor.WHITE) {
                move.to.row - 1
            } else {
                move.to.row + 1
            }
            return board.getPiece(capturedPawnRow, move.to.col)
        }
        return board.getPiece(move.to)
    }

    /**
     * Restores the board from a saved snapshot by copying each piece
     * back into the grid. This is more reliable than reverse-applying
     * moves, especially for complex positions involving castling or
     * en passant.
     */
    private fun restoreBoardFromSnapshot(snapshot: Board) {
        for (row in 0..7) {
            for (col in 0..7) {
                board.setPiece(row, col, snapshot.getPiece(row, col)?.copy())
            }
        }
    }

    /**
     * Updates castling rights after a move. Rights are lost when:
     *   - A king moves (both sides lost for that colour)
     *   - A rook moves from its original square (that side lost)
     *   - A rook is captured on its original square (that side lost)
     */
    private fun updateCastlingRights(move: Move) {
        val movedPiece = board.getPiece(move.to)
        var rights = castlingRights

        // King moved — lose both castling rights for that colour
        if (movedPiece?.type == PieceType.KING) {
            rights = when (movedPiece.color) {
                PieceColor.WHITE -> rights.copy(whiteKingside = false, whiteQueenside = false)
                PieceColor.BLACK -> rights.copy(blackKingside = false, blackQueenside = false)
            }
        }

        // Rook moved from or was captured on its original square
        // White kingside rook (h1 = row 0, col 7)
        if (move.from == Position(0, 7) || move.to == Position(0, 7)) {
            rights = rights.copy(whiteKingside = false)
        }
        // White queenside rook (a1 = row 0, col 0)
        if (move.from == Position(0, 0) || move.to == Position(0, 0)) {
            rights = rights.copy(whiteQueenside = false)
        }
        // Black kingside rook (h8 = row 7, col 7)
        if (move.from == Position(7, 7) || move.to == Position(7, 7)) {
            rights = rights.copy(blackKingside = false)
        }
        // Black queenside rook (a8 = row 7, col 0)
        if (move.from == Position(7, 0) || move.to == Position(7, 0)) {
            rights = rights.copy(blackQueenside = false)
        }

        castlingRights = rights
    }

    /**
     * Updates the en passant target square after a move.
     * The target is set when a pawn advances two squares from its starting rank,
     * and is cleared on every other move.
     */
    private fun updateEnPassantTarget(move: Move) {
        val piece = board.getPiece(move.to)
        if (piece?.type == PieceType.PAWN && kotlin.math.abs(move.to.row - move.from.row) == 2) {
            val enPassantRow = (move.from.row + move.to.row) / 2
            enPassantTarget = Position(enPassantRow, move.from.col)
        } else {
            enPassantTarget = null
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Position key generation (for threefold repetition)
    // ──────────────────────────────────────────────────────────────

    /**
     * Generates a unique key string identifying the current position for
     * threefold repetition detection.
     *
     * Per FIDE rules, two positions are considered the same if:
     *   - The same player has the move
     *   - The same pieces of the same colour occupy the same squares
     *   - The same castling rights are available
     *   - The same en passant capture is available (only if a pawn can
     *     actually capture en passant — we simplify by always including
     *     the en passant square if it exists, which is the common approach)
     *
     * The half-move clock and full move number are intentionally excluded
     * because they do not affect position identity.
     */
    private fun generatePositionKey(): String {
        val sb = StringBuilder()
        sb.append(board.positionHash())
        sb.append("|")
        sb.append(if (currentTurn == PieceColor.WHITE) "w" else "b")
        sb.append("|")
        sb.append(if (castlingRights.whiteKingside) "K" else "")
        sb.append(if (castlingRights.whiteQueenside) "Q" else "")
        sb.append(if (castlingRights.blackKingside) "k" else "")
        sb.append(if (castlingRights.blackQueenside) "q" else "")
        sb.append("|")
        sb.append(enPassantTarget?.toAlgebraic() ?: "-")
        return sb.toString()
    }

    // ──────────────────────────────────────────────────────────────
    //  Game end detection
    // ──────────────────────────────────────────────────────────────

    /**
     * Checks all game-ending conditions and updates [gameResult] and [drawReason].
     *
     * The order of checks matters:
     *   1. Checkmate (the game is won by the side that just moved)
     *   2. Stalemate (draw)
     *   3. 50-move rule (draw)
     *   4. Threefold repetition (draw)
     *   5. Insufficient material (draw)
     *
     * Note: Checkmate takes precedence over all draw conditions. If a player
     * is checkmated, the game is lost — even if the 50-move counter has
     * reached 100 or the position has repeated three times.
     */
    private fun checkGameEnd() {
        val chessBoard = board.toChessBoard(enPassantTarget, currentTurn)

        // 1. Checkmate
        if (moveGenerator.isCheckmate(chessBoard, currentTurn)) {
            gameResult = when (currentTurn) {
                PieceColor.WHITE -> GameResult.BLACK_WON
                PieceColor.BLACK -> GameResult.WHITE_WON
            }
            return
        }

        // 2. Stalemate
        if (moveGenerator.isStalemate(chessBoard, currentTurn)) {
            gameResult = GameResult.DRAW
            drawReason = DrawReason.STALEMATE
            return
        }

        // 3. 50-move rule: 100 half-moves without a pawn move or capture
        if (halfMoveClock >= 100) {
            gameResult = GameResult.DRAW
            drawReason = DrawReason.FIFTY_MOVE_RULE
            return
        }

        // 4. Threefold repetition: the same position occurred three times
        val currentPositionKey = positionHistory.last()
        val repetitionCount = positionHistory.count { it == currentPositionKey }
        if (repetitionCount >= 3) {
            gameResult = GameResult.DRAW
            drawReason = DrawReason.THREEFOLD_REPETITION
            return
        }

        // 5. Insufficient material
        if (moveGenerator.isInsufficientMaterial(chessBoard)) {
            gameResult = GameResult.DRAW
            drawReason = DrawReason.INSUFFICIENT_MATERIAL
            return
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Board access for AI and UI
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns a copy of the internal board. The caller receives an independent
     * snapshot that can be freely modified without affecting the game state.
     */
    fun getBoard(): Board = board.copy()

    /**
     * Creates a new [ChessGame] from a custom starting position.
     * This is used for puzzles, academy scenarios, and FEN-based setup.
     *
     * @param board         The starting board position.
     * @param currentTurn   The colour to move first.
     * @param castlingRights Initial castling availability.
     * @param enPassantTarget The initial en passant target, or null.
     */
    fun createFromPosition(
        board: Board,
        currentTurn: PieceColor = PieceColor.WHITE,
        castlingRights: CastlingRights = CastlingRights(),
        enPassantTarget: Position? = null
    ): ChessGame {
        val game = ChessGame()
        // Restore the board from the provided snapshot
        for (row in 0..7) {
            for (col in 0..7) {
                game.board.setPiece(row, col, board.getPiece(row, col)?.copy())
            }
        }
        game.currentTurn = currentTurn
        game.castlingRights = castlingRights
        game.enPassantTarget = enPassantTarget
        game.halfMoveClock = 0
        game.fullMoveNumber = 1
        game.moveHistory.clear()
        game.positionHistory.clear()
        game.gameResult = GameResult.ACTIVE
        game.drawReason = null

        val key = game.generatePositionKey()
        game.initialPositionKey = key
        game.positionHistory.add(key)

        return game
    }

    companion object {
        /**
         * Creates a new ChessGame with the standard starting position.
         */
        fun createNew(): ChessGame = ChessGame()

        /**
         * Creates a ChessGame from a [ChessBoard] instance.
         * Useful when a position originates from the model layer (e.g., FEN parsing).
         */
        fun fromChessBoard(
            chessBoard: ChessBoard,
            castlingRights: CastlingRights = CastlingRights(),
            enPassantTarget: Position? = chessBoard.enPassantTarget
        ): ChessGame {
            val board = Board.fromChessBoard(chessBoard)
            val game = ChessGame()
            for (row in 0..7) {
                for (col in 0..7) {
                    game.board.setPiece(row, col, board.getPiece(row, col)?.copy())
                }
            }
            game.currentTurn = chessBoard.currentTurn
            game.castlingRights = castlingRights
            game.enPassantTarget = enPassantTarget
            game.halfMoveClock = 0
            game.fullMoveNumber = 1
            game.moveHistory.clear()
            game.positionHistory.clear()
            game.gameResult = GameResult.ACTIVE
            game.drawReason = null

            val key = game.generatePositionKey()
            game.initialPositionKey = key
            game.positionHistory.add(key)

            return game
        }
    }
}
