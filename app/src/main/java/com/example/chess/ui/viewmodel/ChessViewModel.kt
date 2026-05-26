package com.example.chess.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chess.engine.AIDifficulty
import com.example.chess.engine.Board
import com.example.chess.engine.CastlingRights
import com.example.chess.engine.ChessAI
import com.example.chess.engine.ChessGame
import com.example.chess.engine.DrawReason
import com.example.chess.engine.GameResult
import com.example.chess.model.Move
import com.example.chess.model.Piece
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.model.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state representation consumed by Compose screens.
 *
 * This is a snapshot of the entire game at a given moment, containing all the
 * information the UI needs to render the board, status text, and dialogs.
 * It is produced by [ChessViewModel] and exposed as a [StateFlow] so that
 * Compose can reactively recompose only when the state actually changes.
 *
 * Design principle: The UI never touches [ChessGame] or [ChessAI] directly.
 * All interaction goes through the ViewModel, which publishes immutable
 * [ChessUiState] objects. This ensures:
 *   - Thread safety (state reads happen only on the main thread)
 *   - Single source of truth (no stale references)
 *   - Testability (the ViewModel can be unit-tested without Compose)
 */
data class ChessUiState(
    /** The colour whose turn it is to move. */
    val currentTurn: PieceColor = PieceColor.WHITE,

    /** The current game result. */
    val gameResult: GameResult = GameResult.ACTIVE,

    /** If the game is a draw, the specific reason. Null otherwise. */
    val drawReason: DrawReason? = null,

    /** Position of the currently selected piece, or null if nothing is selected. */
    val selectedPosition: Position? = null,

    /** Set of positions the selected piece can legally move to. */
    val validMoves: Set<Position> = emptySet(),

    /** The last move that was executed, used for highlighting from/to squares. */
    val lastMove: Move? = null,

    /** Whether the current side to move is in check. */
    val isInCheck: Boolean = false,

    /** Whether the AI is currently computing its move. */
    val isAIThinking: Boolean = false,

    /** Pieces captured by White (Black pieces that White has taken). */
    val capturedByWhite: List<Piece> = emptyList(),

    /** Pieces captured by Black (White pieces that Black has taken). */
    val capturedByBlack: List<Piece> = emptyList(),

    /** The full move history in algebraic-like representation. */
    val moveHistory: List<Move> = emptyList(),

    /** Half-move clock for the 50-move rule display. */
    val halfMoveClock: Int = 0,

    /** Full move number. */
    val fullMoveNumber: Int = 1,

    /** Current castling rights. */
    val castlingRights: CastlingRights = CastlingRights(),

    /** Current en passant target square, or null. */
    val enPassantTarget: Position? = null,

    /** The AI difficulty level currently in use. */
    val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM,

    /** Whether the game is in AI mode (vs AI) or local pass-and-play. */
    val isAIMode: Boolean = false,

    /** Whether a pawn promotion dialog should be shown. Contains the move awaiting promotion choice. */
    val pendingPromotion: PendingPromotion? = null
)

/**
 * Represents a pawn move that requires the user to choose a promotion piece.
 *
 * When a pawn reaches the last rank, the UI must display a promotion dialog
 * before the move can be executed. The [from] and [to] positions are stored
 * here so that the final move can be constructed once the user selects a
 * promotion type.
 */
data class PendingPromotion(
    val from: Position,
    val to: Position,
    val color: PieceColor
)

/**
 * ViewModel that manages the chess game lifecycle, state, and AI interaction.
 *
 * Architecture:
 *   - [ChessGame] owns the authoritative game state (board, history, rules).
 *   - [ChessAI] computes moves asynchronously on [Dispatchers.Default].
 *   - The ViewModel bridges the two by:
 *       1. Reading game state after each move and publishing it as [ChessUiState]
 *       2. Dispatching AI computation to a background thread
 *       3. Applying the AI's chosen move on the main thread when computation completes
 *
 * All state mutations happen on the main thread. The AI computation runs on
 * [Dispatchers.Default] (a pool of CPU-optimised threads) and the result is
 * posted back to the main thread via [viewModelScope], which uses
 * [Dispatchers.Main] by default.
 *
 * Usage in a Compose screen:
 * ```
 * val viewModel: ChessViewModel = viewModel()
 * val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 * ChessBoardView(uiState, viewModel::onSquareClick)
 * ```
 */
class ChessViewModel(
    private var game: ChessGame = ChessGame(),
    private val ai: ChessAI = ChessAI()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    /**
     * 8x8 grid of pieces for the ChessBoardView composable.
     * Updated on every state refresh so that the UI always has
     * a current snapshot of the board without needing to call
     * game.getBoard() directly (which would break encapsulation).
     */
    private val _boardPieces = MutableStateFlow<Array<Array<Piece?>>>(
        Array(8) { arrayOfNulls(8) }
    )
    val boardPieces: StateFlow<Array<Array<Piece?>>> = _boardPieces.asStateFlow()

    // ──────────────────────────────────────────────────────────────
    //  Captured piece tracking
    // ──────────────────────────────────────────────────────────────

    /**
     * Accumulates captured pieces over the course of the game.
     * Updated incrementally in [recordCapture] whenever a move results in a capture.
     * This is more efficient than scanning the entire move history on every state update.
     */
    private val _capturedByWhite = mutableListOf<Piece>()
    private val _capturedByBlack = mutableListOf<Piece>()

    // ──────────────────────────────────────────────────────────────
    //  Initialisation
    // ──────────────────────────────────────────────────────────────

    init {
        refreshState()
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API — called from Compose UI callbacks
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handles a click on the square at the given [row] and [col].
     *
     * This is the single entry point for all user interaction with the board.
     * The method implements the following logic:
     *
     *   1. If a pawn promotion dialog is open, ignore the click.
     *   2. If the AI is thinking, ignore the click (prevent race conditions).
     *   3. If the game is over, ignore the click.
     *   4. If the AI's turn in AI mode, ignore the click.
     *   5. If nothing is selected:
     *      - Select the clicked piece if it belongs to the current player.
     *   6. If something is already selected:
     *      a. If the click is on a valid destination square, execute the move.
     *      b. If the click is on another friendly piece, switch selection.
     *      c. Otherwise, deselect.
     *
     * For pawn promotions, instead of executing the move immediately, a
     * [PendingPromotion] is stored and the UI shows a promotion dialog.
     */
    fun onSquareClick(row: Int, col: Int) {
        val currentState = _uiState.value

        // Ignore clicks while promotion dialog is open
        if (currentState.pendingPromotion != null) return

        // Ignore clicks while AI is thinking
        if (currentState.isAIThinking) return

        // Ignore clicks if game is over
        if (currentState.gameResult != GameResult.ACTIVE) return

        // Ignore clicks if it's the AI's turn
        if (currentState.isAIMode && currentState.currentTurn == PieceColor.BLACK) return

        val clickedPosition = Position(row, col)

        if (currentState.selectedPosition == null) {
            // Nothing selected — try to select a friendly piece
            attemptSelection(clickedPosition)
        } else {
            // Something is already selected — try to move, re-select, or deselect
            attemptMoveOrReselect(clickedPosition)
        }
    }

    /**
     * Handles the user's promotion piece choice.
     *
     * Called from the promotion dialog when the user taps Queen, Rook, Bishop, or Knight.
     * The pending promotion move is completed and executed on the board.
     *
     * @param promotionType The piece type the user chose for promotion.
     */
    fun onPromotionChosen(promotionType: PieceType) {
        val pending = _uiState.value.pendingPromotion ?: return
        val move = Move(pending.from, pending.to, promotionType = promotionType)

        if (game.executeMove(move)) {
            recordCaptureIfAny(move)
        }

        _uiState.value = _uiState.value.copy(pendingPromotion = null)
        refreshState()
        triggerAIMoveIfNeeded()
    }

    /**
     * Dismisses the promotion dialog without making a move.
     */
    fun onPromotionDismissed() {
        _uiState.value = _uiState.value.copy(pendingPromotion = null)
    }

    /**
     * Starts a new game, resetting all state.
     */
    fun startNewGame(isAIMode: Boolean, difficulty: AIDifficulty) {
        game = ChessGame()
        _capturedByWhite.clear()
        _capturedByBlack.clear()

        _uiState.value = ChessUiState(
            isAIMode = isAIMode,
            aiDifficulty = difficulty
        )
        refreshState()
    }

    /**
     * Resets the game completely with a fresh ChessGame instance.
     * Called when the user explicitly chooses "New Game" from a menu.
     */
    fun resetGame(isAIMode: Boolean, difficulty: AIDifficulty) {
        game = ChessGame()
        _capturedByWhite.clear()
        _capturedByBlack.clear()
        _uiState.value = ChessUiState(
            isAIMode = isAIMode,
            aiDifficulty = difficulty
        )
        refreshState()
    }

    /**
     * Undoes the last move. In AI mode, undoes both the AI's move and the
     * player's move so that the human always gets back to their own turn.
     */
    fun undoMove() {
        val currentState = _uiState.value
        if (currentState.isAIThinking) return

        game.undoMove()

        // In AI mode, also undo the AI's move to give the turn back to the player
        if (currentState.isAIMode && game.getMoveHistory().isNotEmpty()) {
            game.undoMove()
        }

        // Rebuild captured piece lists from the current board state
        rebuildCapturedLists()
        refreshState()
    }

    /**
     * Sets the AI difficulty level for the current game.
     */
    fun setAIDifficulty(difficulty: AIDifficulty) {
        _uiState.value = _uiState.value.copy(aiDifficulty = difficulty)
    }

    // ═══════════════════════════════════════════════════════════════
    //  SELECTION LOGIC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Attempts to select a friendly piece at the given [position].
     * If a piece of the current player's colour is at that position,
     * it becomes the selected piece and its valid moves are computed.
     */
    private fun attemptSelection(position: Position) {
        val piece = game.getPieceAt(position)
        if (piece != null && piece.color == game.getCurrentTurn()) {
            val validMoves = game.getValidDestinations(position).toSet()
            _uiState.value = _uiState.value.copy(
                selectedPosition = position,
                validMoves = validMoves
            )
        }
    }

    /**
     * Attempts to move the currently selected piece to [targetPosition],
     * or switches selection if the target is another friendly piece.
     */
    private fun attemptMoveOrReselect(targetPosition: Position) {
        val currentState = _uiState.value
        val selectedPos = currentState.selectedPosition ?: return

        // Check if the target is a valid move destination
        if (currentState.validMoves.contains(targetPosition)) {
            val piece = game.getPieceAt(selectedPos)

            // Check for pawn promotion
            if (piece?.type == PieceType.PAWN) {
                val promotionRow = if (piece.color == PieceColor.WHITE) 7 else 0
                if (targetPosition.row == promotionRow) {
                    // Show promotion dialog instead of executing the move
                    _uiState.value = currentState.copy(
                        pendingPromotion = PendingPromotion(
                            from = selectedPos,
                            to = targetPosition,
                            color = piece.color
                        )
                    )
                    return
                }
            }

            // Execute the move
            val move = Move(selectedPos, targetPosition)
            if (game.executeMove(move)) {
                recordCaptureIfAny(move)
            }

            // Clear selection
            _uiState.value = _uiState.value.copy(
                selectedPosition = null,
                validMoves = emptySet()
            )
            refreshState()
            triggerAIMoveIfNeeded()
        } else {
            // Check if the target is another friendly piece — switch selection
            val targetPiece = game.getPieceAt(targetPosition)
            if (targetPiece != null && targetPiece.color == game.getCurrentTurn()) {
                val validMoves = game.getValidDestinations(targetPosition).toSet()
                _uiState.value = _uiState.value.copy(
                    selectedPosition = targetPosition,
                    validMoves = validMoves
                )
            } else {
                // Deselect
                _uiState.value = _uiState.value.copy(
                    selectedPosition = null,
                    validMoves = emptySet()
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  AI MOVE COMPUTATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Triggers an asynchronous AI move if the game is in AI mode and it's
     * Black's turn. The computation runs on [Dispatchers.Default] to keep
     * the main thread responsive.
     *
     * The flow is:
     *   1. Set `isAIThinking = true` on the UI state (shows a thinking indicator)
     *   2. Launch a coroutine on [Dispatchers.Default]
     *   3. The AI searches the game tree and returns the best [Move]
     *   4. Back on the main thread, execute the move and update the UI
     *   5. Set `isAIThinking = false`
     *
     * This method is idempotent — if the game is not in AI mode, it's not
     * Black's turn, or the AI is already thinking, it does nothing.
     */
    private fun triggerAIMoveIfNeeded() {
        val currentState = _uiState.value
        if (!currentState.isAIMode) return
        if (currentState.gameResult != GameResult.ACTIVE) return
        if (currentState.currentTurn != PieceColor.BLACK) return
        if (currentState.isAIThinking) return

        _uiState.value = currentState.copy(isAIThinking = true)

        viewModelScope.launch(Dispatchers.Default) {
            val bestMove = ai.findBestMove(game, currentState.aiDifficulty)

            // Switch back to Main thread to update UI
            withContext(Dispatchers.Main) {
                if (bestMove != null && game.getCurrentTurn() == PieceColor.BLACK) {
                    // Check for AI pawn promotion — always promote to Queen
                    val moveToExecute = if (bestMove.promotionType == null) {
                        val piece = game.getPieceAt(bestMove.from)
                        if (piece?.type == PieceType.PAWN) {
                            val promotionRow = if (piece.color == PieceColor.WHITE) 7 else 0
                            if (bestMove.to.row == promotionRow) {
                                Move(bestMove.from, bestMove.to, promotionType = PieceType.QUEEN)
                            } else {
                                bestMove
                            }
                        } else {
                            bestMove
                        }
                    } else {
                        bestMove
                    }

                    if (game.executeMove(moveToExecute)) {
                        recordCaptureIfAny(moveToExecute)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isAIThinking = false,
                    selectedPosition = null,
                    validMoves = emptySet()
                )
                refreshState()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STATE REFRESH
    // ═══════════════════════════════════════════════════════════════

    /**
     * Reads the current state from [ChessGame] and publishes a fresh
     * [ChessUiState] to the [_uiState] flow.
     *
     * This method is called after every state-changing operation:
     *   - After a player move
     *   - After an AI move
     *   - After an undo
     *   - After a game reset
     *
     * It is the single point of truth synchronisation between the engine
     * layer and the UI layer.
     */
    /**
     * Loads a Mate-in-1 puzzle position onto the board.
     *
     * The puzzle is a classic back-rank mate scenario:
     *   White: Kg1, Re1
     *   Black: Kg8, Pf7, Pg7, Ph7
     *   Solution: Re8#
     *
     * All castling rights are disabled because the kings are not on
     * their original squares. White to move.
     */
    fun loadPuzzle() {
        val puzzleBoard = Board.empty()
        puzzleBoard.setPiece(0, 6, Piece(PieceType.KING, PieceColor.WHITE, hasMoved = true))
        puzzleBoard.setPiece(0, 4, Piece(PieceType.ROOK, PieceColor.WHITE, hasMoved = true))
        puzzleBoard.setPiece(7, 6, Piece(PieceType.KING, PieceColor.BLACK, hasMoved = true))
        puzzleBoard.setPiece(6, 5, Piece(PieceType.PAWN, PieceColor.BLACK, hasMoved = true))
        puzzleBoard.setPiece(6, 6, Piece(PieceType.PAWN, PieceColor.BLACK, hasMoved = true))
        puzzleBoard.setPiece(6, 7, Piece(PieceType.PAWN, PieceColor.BLACK, hasMoved = true))

        game = ChessGame.fromChessBoard(
            chessBoard = puzzleBoard.toChessBoard(
                enPassantTarget = null,
                currentTurn = PieceColor.WHITE
            ),
            castlingRights = CastlingRights(
                whiteKingside = false,
                whiteQueenside = false,
                blackKingside = false,
                blackQueenside = false
            ),
            enPassantTarget = null
        )

        _capturedByWhite.clear()
        _capturedByBlack.clear()

        _uiState.value = ChessUiState(
            isAIMode = false,
            aiDifficulty = AIDifficulty.MEDIUM
        )
        refreshState()
    }

    private fun refreshState() {
        // Update the board pieces snapshot for the ChessBoardView
        val board = game.getBoard()
        val pieces = Array(8) { row ->
            Array(8) { col ->
                board.getPiece(row, col)
            }
        }
        _boardPieces.value = pieces

        val gameState = ChessUiState(
            currentTurn = game.getCurrentTurn(),
            gameResult = game.getGameResult(),
            drawReason = game.getDrawReason(),
            selectedPosition = _uiState.value.selectedPosition,
            validMoves = _uiState.value.validMoves,
            lastMove = game.getMoveHistory().lastOrNull(),
            isInCheck = game.isInCheck(),
            isAIThinking = _uiState.value.isAIThinking,
            capturedByWhite = _capturedByWhite.toList(),
            capturedByBlack = _capturedByBlack.toList(),
            moveHistory = game.getMoveHistory(),
            halfMoveClock = game.getHalfMoveClock(),
            fullMoveNumber = game.getFullMoveNumber(),
            castlingRights = game.getCastlingRights(),
            enPassantTarget = game.getEnPassantTarget(),
            aiDifficulty = _uiState.value.aiDifficulty,
            isAIMode = _uiState.value.isAIMode,
            pendingPromotion = _uiState.value.pendingPromotion
        )
        _uiState.value = gameState
    }

    // ═══════════════════════════════════════════════════════════════
    //  CAPTURED PIECE TRACKING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Records a captured piece if the given [move] resulted in a capture.
     * This is called immediately after [ChessGame.executeMove] succeeds.
     *
     * Determining whether a capture occurred:
     *   - For regular moves: the destination square had an opponent piece
     *   - For en passant: the en passant target square had an opponent pawn
     *
     * The captured piece's colour determines which list it goes into:
     *   - A Black piece captured → added to [capturedByWhite]
     *   - A White piece captured → added to [capturedByBlack]
     */
    private fun recordCaptureIfAny(move: Move) {
        // After the move is executed, we can detect captures by checking
        // if the half-move clock was reset (indicating a pawn move or capture)
        // and the moving piece was not a pawn, OR by checking if the move
        // had a destination that was occupied.
        //
        // However, the most reliable approach is to simply rebuild the
        // captured lists from the board state. This handles all edge cases:
        // en passant, captures, and promotions that replace pieces.
        //
        // For incremental tracking (avoiding full rebuild every move),
        // we detect captures by examining the move:

        if (move.isEnPassant) {
            // En passant: the captured pawn's color is the OPPOSITE of the
            // side that just moved (currentTurn has already been toggled).
            val capturedPawnColor = game.getCurrentTurn() // it's now the opponent's turn
            val capturedPawn = Piece(PieceType.PAWN, capturedPawnColor)
            if (capturedPawnColor == PieceColor.BLACK) {
                _capturedByWhite.add(capturedPawn)
            } else {
                _capturedByBlack.add(capturedPawn)
            }
        } else {
            // Regular capture detection: if the half-move clock reset to 0
            // AND the moving piece was not a pawn, it must have been a capture.
            // If it was a pawn move AND the clock reset, we still check if
            // there was a piece on the destination.
            //
            // Simplest reliable check: if the clock is 0 after a non-pawn move,
            // or if it was a pawn move to a square that had a piece.
            // Since we cannot check the pre-move board state after execution,
            // we use the half-move clock as a signal.
            val movingPiece = game.getPieceAt(move.to)
            val wasCapture = game.getHalfMoveClock() == 0 &&
                movingPiece?.type != PieceType.PAWN

            if (wasCapture) {
                // The captured piece was the opposite colour of the moving piece.
                // Since the turn has already switched, the captured piece's colour
                // is the CURRENT turn (the opponent who just lost a piece).
                val capturedColor = game.getCurrentTurn()
                // We don't know the exact piece type, so we reconstruct it
                // by checking what's missing. Fall back to rebuild.
                rebuildCapturedLists()
                return
            }

            // Pawn captures are also possible — a pawn moved diagonally
            // to a square that had an opponent piece.
            if (movingPiece?.type == PieceType.PAWN &&
                move.from.col != move.to.col &&
                game.getHalfMoveClock() == 0
            ) {
                // Diagonal pawn move with clock reset = pawn capture
                val capturedColor = game.getCurrentTurn()
                rebuildCapturedLists()
                return
            }
        }
    }

    /**
     * Rebuilds the captured piece lists from scratch by replaying the
     * move history. Called after an undo operation where we can't easily
     * determine which captures to remove.
     */
    private fun rebuildCapturedLists() {
        _capturedByWhite.clear()
        _capturedByBlack.clear()

        val moveHistory = game.getMoveHistory()
        val board = game.getBoard()

        // For each move in history, check if it was a capture by comparing
        // piece counts. A simpler approach: just iterate through the game
        // state and determine what's missing from the starting position.
        val startingPieces = mutableMapOf<Pair<PieceColor, PieceType>, Int>()
        for (color in listOf(PieceColor.WHITE, PieceColor.BLACK)) {
            for (type in listOf(
                PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP,
                PieceType.ROOK, PieceType.QUEEN, PieceType.KING
            )) {
                startingPieces[color to type] = when (type) {
                    PieceType.PAWN -> 8
                    PieceType.KNIGHT -> 2
                    PieceType.BISHOP -> 2
                    PieceType.ROOK -> 2
                    PieceType.QUEEN -> 1
                    PieceType.KING -> 1
                }
            }
        }

        // Count pieces currently on the board
        val currentPieces = mutableMapOf<Pair<PieceColor, PieceType>, Int>()
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board.getPiece(row, col)
                if (piece != null) {
                    val key = piece.color to piece.type
                    currentPieces[key] = (currentPieces[key] ?: 0) + 1
                }
            }
        }

        // The difference is the captured pieces
        for ((key, startCount) in startingPieces) {
            val currentCount = currentPieces[key] ?: 0
            val captured = startCount - currentCount
            if (captured > 0) {
                val (color, type) = key
                val capturedPiece = Piece(type, color)
                for (i in 0 until captured) {
                    // Pieces of this color/type that are missing were captured
                    // by the OPPOSITE color
                    if (color == PieceColor.BLACK) {
                        _capturedByWhite.add(capturedPiece)
                    } else {
                        _capturedByBlack.add(capturedPiece)
                    }
                }
            }
        }

        // Sort captured lists by piece value (highest first)
        val pieceOrder = listOf(
            PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP,
            PieceType.KNIGHT, PieceType.PAWN
        )
        _capturedByWhite.sortBy { pieceOrder.indexOf(it.type) }
        _capturedByBlack.sortBy { pieceOrder.indexOf(it.type) }
    }

    // ═══════════════════════════════════════════════════════════════
    //  VIEWMODEL CLEANUP
    // ═══════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        // Any cleanup if needed (e.g., cancelling ongoing AI computation
        // is handled automatically by viewModelScope cancellation)
    }

    // ═══════════════════════════════════════════════════════════════
    //  FACTORY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Factory for creating [ChessViewModel] with custom dependencies.
     *
     * Usage:
     * ```
     * val factory = ChessViewModel.Factory()
     * val viewModel: ChessViewModel = viewModel(factory = factory)
     * ```
     */
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChessViewModel() as T
        }
    }
}
