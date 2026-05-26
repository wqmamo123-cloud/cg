package com.example.chess.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chess.engine.Board
import com.example.chess.engine.CastlingRights
import com.example.chess.engine.ChessGame
import com.example.chess.engine.GameResult
import com.example.chess.model.Move
import com.example.chess.model.Piece
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.model.Position
import com.example.chess.ui.components.ChessBoardView
import com.example.chess.ui.theme.AccentGreen
import com.example.chess.ui.theme.CardPurpleEnd
import com.example.chess.ui.theme.CardPurpleStart
import com.example.chess.ui.theme.Gold500
import com.example.chess.ui.viewmodel.ChessUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ═══════════════════════════════════════════════════════════════
//  LESSON OBJECTIVE TYPES
// ═══════════════════════════════════════════════════════════════

/**
 * Defines the type of objective a lesson expects the user to achieve.
 *
 * Each lesson has exactly one objective type that determines how the
 * AcademyViewModel validates the user's move. This enum ensures that
 * validation logic is centralised and consistent.
 */
enum class LessonObjective {
    /** Any legal Knight move counts as success. Used for the L-Move tutorial. */
    ANY_KNIGHT_MOVE,

    /** A castling move (King moves two squares toward a Rook) counts as success. */
    CASTLING,

    /** An en passant capture counts as success. */
    EN_PASSANT
}

// ═══════════════════════════════════════════════════════════════
//  LESSON DEFINITION
// ═══════════════════════════════════════════════════════════════

/**
 * Immutable definition of a single Chess Academy lesson.
 *
 * Each lesson encapsulates everything needed to set up and validate
 * an interactive tutorial scenario:
 *   - A descriptive [title] and [objective] summary shown in the header card
 *   - Step-by-step [instructions] that guide the user through the lesson
 *   - An [objectiveType] that determines how moves are validated
 *   - A set of [selectablePieceTypes] restricting which pieces the user may tap
 *   - Hint messages for wrong-piece selection and wrong-move attempts
 *   - A [successMessage] displayed upon completing the objective
 *   - A [setupGame] factory that creates a fresh [ChessGame] in the lesson position
 *
 * The [setupGame] lambda is invoked each time the lesson starts or resets,
 * ensuring a clean board state with no leftover move history.
 */
data class AcademyLesson(
    val title: String,
    val objective: String,
    val instructions: List<String>,
    val objectiveType: LessonObjective,
    val selectablePieceTypes: Set<PieceType>,
    val wrongPieceHint: String,
    val wrongMoveHint: String,
    val successMessage: String,
    val setupGame: () -> ChessGame
)

// ═══════════════════════════════════════════════════════════════
//  LESSON INSTANCES
// ═══════════════════════════════════════════════════════════════

/**
 * Lesson 1: The Knight's L-Move
 *
 * Board setup: White King on b1, White Knight on d4, Black King on g8.
 * All castling rights disabled since the kings are not on their original squares.
 *
 * The user must tap the Knight, observe its 8 L-shaped valid moves
 * highlighted on the board, and then move it to any of those squares.
 * Every legal Knight move is accepted, because the sole objective is
 * to demonstrate the unique L-shape movement pattern.
 *
 * Position (from White's perspective, rank 1 at bottom):
 *   8 . . . . . . k .
 *   7 . . . . . . . .
 *   6 . . . . . . . .
 *   5 . . . . . . . .
 *   4 . . . N . . . .
 *   3 . . . . . . . .
 *   2 . . . . . . . .
 *   1 . K . . . . . .
 */
private val knightLesson = AcademyLesson(
    title = "The Knight's L-Move",
    objective = "Learn how the Knight moves in its unique L-shaped pattern.",
    instructions = listOf(
        "The Knight is the only piece that can jump over other pieces.",
        "It moves in an 'L' shape: 2 squares in one direction and 1 square perpendicular, or 1 square then 2 squares.",
        "Tap the White Knight in the center of the board to see its valid moves highlighted in green.",
        "Then tap any highlighted square to move the Knight and complete the lesson."
    ),
    objectiveType = LessonObjective.ANY_KNIGHT_MOVE,
    selectablePieceTypes = setOf(PieceType.KNIGHT),
    wrongPieceHint = "Try tapping the White Knight instead. It is the piece shaped like a horse on d4.",
    wrongMoveHint = "", // All Knight moves are correct in this lesson
    successMessage = "Excellent! The Knight always moves in an L-shape: 2 squares in one direction and 1 square perpendicular (or vice versa). No other piece can jump over others!",
    setupGame = {
        val board = Board.empty()
        board.setPiece(0, 1, Piece(PieceType.KING, PieceColor.WHITE, hasMoved = true))
        board.setPiece(3, 3, Piece(PieceType.KNIGHT, PieceColor.WHITE, hasMoved = true))
        board.setPiece(7, 6, Piece(PieceType.KING, PieceColor.BLACK, hasMoved = true))

        ChessGame.fromChessBoard(
            chessBoard = board.toChessBoard(
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
    }
)

/**
 * Lesson 2: The Castling Defense
 *
 * Board setup: White King on e1 (hasMoved=false), White Rook on h1
 * (hasMoved=false), Black King on e8. Kingside castling rights enabled.
 *
 * The user must tap the White King and then move it two squares to g1
 * to perform kingside castling. Regular King moves (e.g., Kf1, Kd1)
 * are valid but do not satisfy the lesson objective; a hint is shown
 * and the move is not executed, keeping the board in its original state
 * so the user can try again immediately.
 *
 * Position:
 *   8 . . . . k . . .
 *   7 . . . . . . . .
 *   6 . . . . . . . .
 *   5 . . . . . . . .
 *   4 . . . . . . . .
 *   3 . . . . . . . .
 *   2 . . . . . . . .
 *   1 . . . . K . . R
 */
private val castlingLesson = AcademyLesson(
    title = "The Castling Defense",
    objective = "Learn how to castle kingside to protect your King and activate your Rook.",
    instructions = listOf(
        "Castling is a special move involving your King and a Rook. It is the only time two pieces move in one turn.",
        "The King moves 2 squares toward a Rook, and the Rook jumps to the square on the other side of the King.",
        "Conditions: Neither piece has moved before, no pieces between them, the King is not in check, and does not pass through check.",
        "Tap the White King on e1, then move it 2 squares to the right (to g1) to castle kingside."
    ),
    objectiveType = LessonObjective.CASTLING,
    selectablePieceTypes = setOf(PieceType.KING),
    wrongPieceHint = "Try tapping the White King on e1. In castling, you always move the King, not the Rook!",
    wrongMoveHint = "That was a regular King move. Castling moves the King exactly 2 squares toward the Rook. Try moving the King to g1 (2 squares to the right)!",
    successMessage = "Brilliant! You performed kingside castling! The King moved 2 squares toward the Rook, and the Rook jumped to the other side. This is a powerful defensive and offensive maneuver!",
    setupGame = {
        val board = Board.empty()
        board.setPiece(0, 4, Piece(PieceType.KING, PieceColor.WHITE, hasMoved = false))
        board.setPiece(0, 7, Piece(PieceType.ROOK, PieceColor.WHITE, hasMoved = false))
        board.setPiece(7, 4, Piece(PieceType.KING, PieceColor.BLACK, hasMoved = true))

        ChessGame.fromChessBoard(
            chessBoard = board.toChessBoard(
                enPassantTarget = null,
                currentTurn = PieceColor.WHITE
            ),
            castlingRights = CastlingRights(
                whiteKingside = true,
                whiteQueenside = false,
                blackKingside = false,
                blackQueenside = false
            ),
            enPassantTarget = null
        )
    }
)

/**
 * Lesson 3: En Passant Explained
 *
 * Board setup: White King on a1, White Pawn on e5, Black King on a8,
 * Black Pawn on d5 (having just double-advanced from d7). The en passant
 * target is set to d6.
 *
 * The user must tap the White Pawn on e5 and capture en passant by moving
 * diagonally to d6, removing the Black pawn on d5. A regular forward
 * pawn advance (e5-e6) is valid but does not satisfy the objective;
 * a hint is shown and the move is not executed.
 *
 * Position:
 *   8 k . . . . . . .
 *   7 . . . . . . . .
 *   6 . . . . . . . .
 *   5 . . . p P . . .   (Black pawn on d5, White pawn on e5)
 *   4 . . . . . . . .
 *   3 . . . . . . . .
 *   2 . . . . . . . .
 *   1 K . . . . . . .
 */
private val enPassantLesson = AcademyLesson(
    title = "En Passant Explained",
    objective = "Master the en passant capture, one of chess's most surprising special moves.",
    instructions = listOf(
        "En passant (French for 'in passing') is a special pawn capture that can occur immediately after an opponent moves a pawn 2 squares forward from its starting position.",
        "If your pawn could have captured the opponent's pawn had it only moved 1 square, you may capture it as if it had moved just 1 square, but only on the very next move.",
        "The Black pawn just advanced from d7 to d5 in a single move. Your White pawn on e5 can capture it en passant by moving diagonally to d6.",
        "Tap the White Pawn on e5, then tap the highlighted diagonal square d6 to perform the en passant capture."
    ),
    objectiveType = LessonObjective.EN_PASSANT,
    selectablePieceTypes = setOf(PieceType.PAWN),
    wrongPieceHint = "Try tapping the White Pawn on e5. It is the only piece that can capture en passant in this position!",
    wrongMoveHint = "That was a regular pawn advance. En passant is a diagonal capture! Move the pawn diagonally to d6 (the square behind the opponent's pawn) to capture en passant.",
    successMessage = "Perfect! You captured en passant! This special move lets you capture a pawn that just double-advanced, as if it had only moved one square. Remember: you must capture en passant immediately, or the opportunity is lost forever!",
    setupGame = {
        val board = Board.empty()
        board.setPiece(0, 0, Piece(PieceType.KING, PieceColor.WHITE, hasMoved = true))
        board.setPiece(4, 4, Piece(PieceType.PAWN, PieceColor.WHITE, hasMoved = true))
        board.setPiece(7, 0, Piece(PieceType.KING, PieceColor.BLACK, hasMoved = true))
        board.setPiece(4, 3, Piece(PieceType.PAWN, PieceColor.BLACK, hasMoved = true))

        ChessGame.fromChessBoard(
            chessBoard = board.toChessBoard(
                enPassantTarget = Position(5, 3),
                currentTurn = PieceColor.WHITE
            ),
            castlingRights = CastlingRights(
                whiteKingside = false,
                whiteQueenside = false,
                blackKingside = false,
                blackQueenside = false
            ),
            enPassantTarget = Position(5, 3)
        )
    }
)

/** Ordered list of all Chess Academy lessons. */
private val ALL_LESSONS = listOf(knightLesson, castlingLesson, enPassantLesson)

// ═══════════════════════════════════════════════════════════════
//  ACADEMY UI STATE
// ═══════════════════════════════════════════════════════════════

/**
 * Immutable snapshot of the Chess Academy screen state.
 *
 * This state drives all composables in the AcademyScreen. It tracks
 * the current lesson, selection state, feedback messages, and which
 * lessons the user has already completed. The ViewModel publishes a
 * new instance of this class after every user interaction.
 *
 * @property currentLessonIndex Zero-based index into [ALL_LESSONS].
 * @property selectedPosition   The board position currently selected by the user, or null.
 * @property validMoves         Set of positions the selected piece can legally move to.
 * @property lastMove           The last move executed on the board, used for highlighting.
 * @property isLessonCompleted  Whether the current lesson's objective has been achieved.
 * @property feedbackMessage    An optional hint or success message to display.
 * @property isFeedbackSuccess  Whether the feedback is a success message (green) or a hint (amber).
 * @property completedLessons   Set of lesson indices that the user has completed.
 */
data class AcademyUiState(
    val currentLessonIndex: Int = 0,
    val selectedPosition: Position? = null,
    val validMoves: Set<Position> = emptySet(),
    val lastMove: Move? = null,
    val isLessonCompleted: Boolean = false,
    val feedbackMessage: String? = null,
    val isFeedbackSuccess: Boolean = false,
    val completedLessons: Set<Int> = emptySet()
)

// ═══════════════════════════════════════════════════════════════
//  ACADEMY VIEW MODEL
// ═══════════════════════════════════════════════════════════════

/**
 * ViewModel that manages the Chess Academy lesson lifecycle.
 *
 * The AcademyViewModel owns a [ChessGame] instance for the current lesson
 * and produces both an [AcademyUiState] (for lesson-specific UI) and a
 * derived [ChessUiState] (for the shared [ChessBoardView] composable).
 *
 * ## Move Validation Strategy
 *
 * The ViewModel restricts piece selection to [AcademyLesson.selectablePieceTypes]
 * so the user can only interact with the lesson-relevant piece. When a move
 * destination is tapped:
 *
 *   1. The matching [Move] is looked up in the legal moves list to obtain
 *      the correct [Move.isCastling] and [Move.isEnPassant] flags.
 *   2. The move is checked against [AcademyLesson.objectiveType]:
 *       - [LessonObjective.ANY_KNIGHT_MOVE]: Any move of a Knight piece passes.
 *       - [LessonObjective.CASTLING]: The move must have [Move.isCastling] = true.
 *       - [LessonObjective.EN_PASSANT]: The move must have [Move.isEnPassant] = true.
 *   3. If the move matches, it is executed and the lesson is marked as completed.
 *   4. If the move does not match, it is NOT executed. A hint message is shown
 *      and the board remains in its original position so the user can try again
 *      without needing to reset.
 *
 * This approach keeps the board stable during learning, avoiding confusing
 * state changes from wrong moves while still providing immediate feedback.
 */
class AcademyViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AcademyUiState())
    val uiState: StateFlow<AcademyUiState> = _uiState.asStateFlow()

    /**
     * 8x8 grid of pieces for the [ChessBoardView] composable.
     * Updated on every state refresh so that the UI always has
     * a current snapshot of the board.
     */
    private val _boardPieces = MutableStateFlow<Array<Array<Piece?>>>(
        Array(8) { arrayOfNulls(8) }
    )
    val boardPieces: StateFlow<Array<Array<Piece?>>> = _boardPieces.asStateFlow()

    /**
     * Derived [ChessUiState] for the shared [ChessBoardView] composable.
     * ChessBoardView only reads selection, valid moves, check, and last-move
     * fields from this state. We keep game-related fields at their defaults
     * since the academy does not use AI, captured pieces, or game-end detection.
     */
    private val _chessUiState = MutableStateFlow(ChessUiState())
    val chessUiState: StateFlow<ChessUiState> = _chessUiState.asStateFlow()

    /** The ChessGame instance for the current lesson position. */
    private var game: ChessGame = ALL_LESSONS[0].setupGame()

    /** Tracks which lessons the user has completed across the session. */
    private val _completedLessons = mutableSetOf<Int>()

    // ──────────────────────────────────────────────────────────
    //  Initialisation
    // ──────────────────────────────────────────────────────────

    init {
        loadLesson(0)
    }

    // ══════════════════════════════════════════════════════════
    //  PUBLIC API — called from Compose UI callbacks
    // ══════════════════════════════════════════════════════════

    /**
     * Handles a click on the square at the given [row] and [col].
     *
     * Implements lesson-specific interaction logic:
     *   - Only pieces of the lesson's [selectablePieceTypes] may be selected.
     *   - Only moves matching the [LessonObjective] are executed.
     *   - Wrong-piece selections and wrong-move attempts produce hint messages.
     *   - After a lesson is completed, further clicks are ignored until reset.
     */
    fun onSquareClick(row: Int, col: Int) {
        val state = _uiState.value
        val lesson = ALL_LESSONS[state.currentLessonIndex]
        val clickedPosition = Position(row, col)

        // Ignore clicks after the lesson is completed
        if (state.isLessonCompleted) return

        if (state.selectedPosition == null) {
            // ── Nothing selected — attempt to select a piece ──
            attemptSelection(clickedPosition, lesson)
        } else {
            // ── Something selected — attempt move or reselect ──
            attemptMoveOrReselect(clickedPosition, lesson, state)
        }
    }

    /**
     * Navigates to the next lesson in the sequence.
     * No-op if the current lesson is the last one.
     */
    fun nextLesson() {
        val currentIndex = _uiState.value.currentLessonIndex
        if (currentIndex < ALL_LESSONS.size - 1) {
            loadLesson(currentIndex + 1)
        }
    }

    /**
     * Navigates to the previous lesson in the sequence.
     * No-op if the current lesson is the first one.
     */
    fun previousLesson() {
        val currentIndex = _uiState.value.currentLessonIndex
        if (currentIndex > 0) {
            loadLesson(currentIndex - 1)
        }
    }

    /**
     * Resets the current lesson to its original board position
     * and clears all feedback messages.
     */
    fun resetLesson() {
        loadLesson(_uiState.value.currentLessonIndex)
    }

    // ══════════════════════════════════════════════════════════
    //  SELECTION LOGIC
    // ══════════════════════════════════════════════════════════

    /**
     * Attempts to select a piece at [position] for the given [lesson].
     *
     * If the clicked square contains a friendly piece of the correct type
     * (as defined by [AcademyLesson.selectablePieceTypes]), it becomes
     * selected and its valid moves are computed and displayed.
     *
     * If the piece is friendly but of the wrong type, a hint message is shown.
     * If the square is empty or contains an opponent piece, nothing happens.
     */
    private fun attemptSelection(position: Position, lesson: AcademyLesson) {
        val piece = game.getPieceAt(position)

        if (piece != null && piece.color == game.getCurrentTurn()) {
            if (piece.type !in lesson.selectablePieceTypes) {
                // Wrong piece type — show hint
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = lesson.wrongPieceHint,
                    isFeedbackSuccess = false
                )
                return
            }

            // Correct piece type — select it
            val validMoves = game.getValidDestinations(position).toSet()
            _uiState.value = _uiState.value.copy(
                selectedPosition = position,
                validMoves = validMoves,
                feedbackMessage = null
            )
            refreshBoard()
        }
    }

    /**
     * Attempts to move the currently selected piece to [targetPosition],
     * or switches selection if the target is another selectable friendly piece.
     *
     * ## Move Validation
     *
     * When the user taps a valid destination, the matching [Move] is looked
     * up in the legal moves list. The move's [Move.isCastling] and
     * [Move.isEnPassant] flags are checked against the lesson objective:
     *
     *   - **Correct move**: The move is executed on the board, the lesson
     *     is marked as completed, and a success message is displayed.
     *   - **Wrong move** (valid but not the objective): The move is NOT
     *     executed. A hint message is shown, selection is cleared, and the
     *     user can try again from the same position.
     *
     * This "reject but don't execute" strategy for wrong moves keeps the
     * board stable so the user can immediately retry without resetting.
     */
    private fun attemptMoveOrReselect(
        targetPosition: Position,
        lesson: AcademyLesson,
        state: AcademyUiState
    ) {
        val selectedPos = state.selectedPosition ?: return

        if (state.validMoves.contains(targetPosition)) {
            // ── Target is a valid move destination ──
            val legalMoves = game.getLegalMoves()
            val matchingMove = legalMoves.find {
                it.from == selectedPos && it.to == targetPosition
            }

            if (matchingMove != null) {
                if (isMoveCorrect(matchingMove, lesson)) {
                    // Correct move — execute it and mark lesson as completed
                    game.executeMove(matchingMove)
                    _completedLessons.add(state.currentLessonIndex)

                    _uiState.value = state.copy(
                        isLessonCompleted = true,
                        feedbackMessage = lesson.successMessage,
                        isFeedbackSuccess = true,
                        selectedPosition = null,
                        validMoves = emptySet(),
                        lastMove = matchingMove,
                        completedLessons = _completedLessons.toSet()
                    )
                } else {
                    // Wrong move (valid but not the objective) — show hint, don't execute
                    _uiState.value = state.copy(
                        feedbackMessage = lesson.wrongMoveHint,
                        isFeedbackSuccess = false,
                        selectedPosition = null,
                        validMoves = emptySet()
                    )
                }
                refreshBoard()
            }
        } else {
            // ── Target is not a valid destination — reselect or deselect ──
            val targetPiece = game.getPieceAt(targetPosition)

            if (targetPiece != null &&
                targetPiece.color == game.getCurrentTurn() &&
                targetPiece.type in lesson.selectablePieceTypes
            ) {
                // Switch selection to another valid piece
                val validMoves = game.getValidDestinations(targetPosition).toSet()
                _uiState.value = state.copy(
                    selectedPosition = targetPosition,
                    validMoves = validMoves,
                    feedbackMessage = null
                )
            } else {
                // Deselect
                _uiState.value = state.copy(
                    selectedPosition = null,
                    validMoves = emptySet()
                )
            }
            refreshBoard()
        }
    }

    // ══════════════════════════════════════════════════════════
    //  MOVE VALIDATION
    // ══════════════════════════════════════════════════════════

    /**
     * Checks whether the given [move] satisfies the [lesson]'s objective.
     *
     * Validation rules per objective type:
     *   - [LessonObjective.ANY_KNIGHT_MOVE]: The moving piece must be a Knight.
     *     (Any legal Knight move is accepted.)
     *   - [LessonObjective.CASTLING]: The move must have [Move.isCastling] = true.
     *   - [LessonObjective.EN_PASSANT]: The move must have [Move.isEnPassant] = true.
     */
    private fun isMoveCorrect(move: Move, lesson: AcademyLesson): Boolean {
        return when (lesson.objectiveType) {
            LessonObjective.ANY_KNIGHT_MOVE -> {
                val piece = game.getPieceAt(move.from)
                piece?.type == PieceType.KNIGHT
            }
            LessonObjective.CASTLING -> move.isCastling
            LessonObjective.EN_PASSANT -> move.isEnPassant
        }
    }

    // ══════════════════════════════════════════════════════════
    //  LESSON LOADING
    // ══════════════════════════════════════════════════════════

    /**
     * Loads the lesson at the given [index], creating a fresh [ChessGame]
     * from the lesson's setup factory and resetting all UI state.
     *
     * Completed-lesson tracking is preserved across lesson switches so
     * the user can see which lessons they have already finished.
     */
    private fun loadLesson(index: Int) {
        game = ALL_LESSONS[index].setupGame()
        _uiState.value = AcademyUiState(
            currentLessonIndex = index,
            completedLessons = _completedLessons.toSet()
        )
        refreshBoard()
    }

    // ══════════════════════════════════════════════════════════
    //  STATE REFRESH
    // ══════════════════════════════════════════════════════════

    /**
     * Reads the current board state from [ChessGame] and publishes fresh
     * snapshots to both [_boardPieces] and [_chessUiState].
     *
     * This method is called after every state-changing operation:
     *   - After a correct move is executed
     *   - After a lesson is loaded or reset
     *   - After selection state changes
     *
     * The derived [ChessUiState] includes only the fields that
     * [ChessBoardView] reads: selection, valid moves, check status,
     * and last-move highlighting.
     */
    private fun refreshBoard() {
        val board = game.getBoard()
        val pieces = Array(8) { row ->
            Array(8) { col ->
                board.getPiece(row, col)
            }
        }
        _boardPieces.value = pieces

        val state = _uiState.value
        _chessUiState.value = ChessUiState(
            currentTurn = game.getCurrentTurn(),
            selectedPosition = state.selectedPosition,
            validMoves = state.validMoves,
            isInCheck = game.isInCheck(),
            gameResult = GameResult.ACTIVE,
            lastMove = state.lastMove
        )
    }

    // ══════════════════════════════════════════════════════════
    //  FACTORY
    // ══════════════════════════════════════════════════════════

    /**
     * Factory for creating [AcademyViewModel] instances.
     * The AcademyViewModel has no constructor dependencies, so the
     * factory simply instantiates it directly.
     */
    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AcademyViewModel() as T
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ACADEMY SCREEN COMPOSABLE
// ═══════════════════════════════════════════════════════════════

/**
 * Interactive Chess Academy screen with step-by-step lessons.
 *
 * The screen presents a scrollable layout with:
 *   1. A top bar with a back button and "Chess Academy" title
 *   2. A lesson header card showing the title, objective, and step-by-step instructions
 *   3. An interactive chess board using [ChessBoardView]
 *   4. An animated feedback banner for success messages and hints
 *   5. Navigation buttons (Previous, Reset Lesson, Next)
 *   6. A "Back to Dashboard" button at the bottom
 *
 * The user interacts with the board by tapping pieces and destination
 * squares. The [AcademyViewModel] validates each move against the
 * current lesson's objective and provides immediate feedback.
 *
 * @param onNavigateBack Callback to return to the Dashboard screen
 */
@Composable
fun AcademyScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: AcademyViewModel = viewModel(factory = AcademyViewModel.Factory)
    val academyState by viewModel.uiState.collectAsStateWithLifecycle()
    val boardPieces by viewModel.boardPieces.collectAsStateWithLifecycle()
    val chessUiState by viewModel.chessUiState.collectAsStateWithLifecycle()

    val currentLesson = ALL_LESSONS[academyState.currentLessonIndex]

    // Calculate board size to fit the screen with comfortable margins
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val boardSize = (screenWidthDp - 48.dp).coerceAtMost(360.dp)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Top Bar ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Dashboard",
                        tint = Gold500
                    )
                }
                Text(
                    text = "Chess Academy",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Gold500
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Lesson Header Card ───────────────────────────
            LessonHeaderCard(
                lesson = currentLesson,
                lessonIndex = academyState.currentLessonIndex,
                totalLessons = ALL_LESSONS.size,
                isCompleted = academyState.currentLessonIndex in academyState.completedLessons
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Chess Board ──────────────────────────────────
            ChessBoardView(
                uiState = chessUiState,
                boardPieces = boardPieces,
                onSquareClick = viewModel::onSquareClick,
                boardSize = boardSize
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Feedback Banner ──────────────────────────────
            AnimatedVisibility(
                visible = academyState.feedbackMessage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 })
            ) {
                academyState.feedbackMessage?.let { message ->
                    FeedbackBanner(
                        message = message,
                        isSuccess = academyState.isFeedbackSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Navigation Buttons ───────────────────────────
            LessonNavigationRow(
                onPrevious = { viewModel.previousLesson() },
                onReset = { viewModel.resetLesson() },
                onNext = { viewModel.nextLesson() },
                canGoPrevious = academyState.currentLessonIndex > 0,
                canGoNext = academyState.currentLessonIndex < ALL_LESSONS.size - 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Back to Dashboard Button ─────────────────────
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Back to Dashboard")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  LESSON HEADER CARD
// ═══════════════════════════════════════════════════════════════

/**
 * Premium card displaying the current lesson's title, objective, and
 * step-by-step instructions.
 *
 * The card uses a purple gradient background consistent with the
 * "Chess Academy" branding from the Dashboard. A progress indicator
 * ("Lesson X of Y") and a completion checkmark provide context.
 *
 * Instructions are rendered as numbered steps with clear visual
 * separation, making it easy for beginners to follow along.
 *
 * @param lesson       The current lesson definition
 * @param lessonIndex  Zero-based index of the current lesson
 * @param totalLessons Total number of lessons in the academy
 * @param isCompleted  Whether the user has already completed this lesson
 */
@Composable
private fun LessonHeaderCard(
    lesson: AcademyLesson,
    lessonIndex: Int,
    totalLessons: Int,
    isCompleted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(CardPurpleStart, CardPurpleEnd)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Progress row: "Lesson X of Y" + completion check
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lesson ${lessonIndex + 1} of $totalLessons",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Gold500
                    )
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Completed",
                            modifier = Modifier.size(22.dp),
                            tint = AccentGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Lesson title
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Objective
                Text(
                    text = lesson.objective,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Step-by-step instructions
                lesson.instructions.forEachIndexed { index, instruction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Gold500,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  FEEDBACK BANNER
// ═══════════════════════════════════════════════════════════════

/**
 * Animated banner displaying feedback after the user makes a move.
 *
 * Two visual styles:
 *   - **Success** (green background): The user completed the lesson objective.
 *   - **Hint** (amber/gold background): The user made a wrong move or tapped
 *     the wrong piece, and needs guidance.
 *
 * The banner uses a subtle rounded-rectangle shape with a matching
 * text colour for high readability. It animates in/out with a
 * fade and vertical slide.
 *
 * @param message   The feedback text to display
 * @param isSuccess Whether this is a success message (green) or a hint (amber)
 */
@Composable
private fun FeedbackBanner(
    message: String,
    isSuccess: Boolean
) {
    val backgroundColor = if (isSuccess) {
        AccentGreen.copy(alpha = 0.15f)
    } else {
        Color(0xFFFFA726).copy(alpha = 0.15f)
    }

    val textColor = if (isSuccess) {
        AccentGreen
    } else {
        Color(0xFFFFA726)
    }

    val iconTint = if (isSuccess) {
        AccentGreen
    } else {
        Color(0xFFFFA726)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = if (isSuccess) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.Refresh
                },
                contentDescription = if (isSuccess) "Success" else "Hint",
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = textColor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  LESSON NAVIGATION ROW
// ═══════════════════════════════════════════════════════════════

/**
 * Row of navigation buttons for switching between lessons and resetting.
 *
 * Three buttons:
 *   - **Previous**: Navigate to the previous lesson (disabled on the first lesson)
 *   - **Reset Lesson**: Restart the current lesson from its initial position
 *   - **Next**: Navigate to the next lesson (disabled on the last lesson)
 *
 * The "Reset Lesson" button uses a filled tonal style to make it the
 * primary action, while "Previous" and "Next" are outlined for secondary
 * emphasis. All buttons use rounded shapes consistent with the app's
 * design language.
 *
 * @param onPrevious   Callback for the Previous button
 * @param onReset      Callback for the Reset Lesson button
 * @param onNext       Callback for the Next button
 * @param canGoPrevious Whether the Previous button should be enabled
 * @param canGoNext    Whether the Next button should be enabled
 */
@Composable
private fun LessonNavigationRow(
    onPrevious: () -> Unit,
    onReset: () -> Unit,
    onNext: () -> Unit,
    canGoPrevious: Boolean,
    canGoNext: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = canGoPrevious,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Previous")
        }

        FilledTonalButton(
            onClick = onReset,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text("Reset Lesson")
        }

        OutlinedButton(
            onClick = onNext,
            enabled = canGoNext,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Next")
        }
    }
}
