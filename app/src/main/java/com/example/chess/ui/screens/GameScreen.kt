package com.example.chess.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chess.engine.AIDifficulty
import com.example.chess.engine.DrawReason
import com.example.chess.engine.GameResult
import com.example.chess.model.Piece
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.ui.components.ChessBoardView
import com.example.chess.ui.theme.Gold500
import com.example.chess.ui.theme.Navy900
import com.example.chess.ui.viewmodel.ChessUiState
import com.example.chess.ui.viewmodel.ChessViewModel

/**
 * The main game screen hosting the chess board, player info bars,
 * status indicators, and game-over dialogs.
 *
 * Layout (top to bottom):
 *   1. Top Player Info Bar   — opponent (Black) info + captured pieces
 *   2. Chess Board           — 8x8 interactive grid
 *   3. Bottom Player Info Bar — player (White) info + captured pieces
 *   4. Action Buttons        — Undo, New Game, Home
 *
 * The screen also displays:
 *   - Turn indicator with colour-coded dot
 *   - AI thinking state with pulsing animation
 *   - Check indicator in the status text
 *   - End-game [AlertDialog] with result and "Rematch" / "Go Home" options
 *   - Pawn promotion [AlertDialog] with piece selection
 *
 * @param gameMode         Navigation parameter: "friend", "ai", or "puzzle"
 * @param difficultyParam  Navigation parameter: "easy", "medium", or "hard"
 * @param onNavigateHome   Callback to return to the Dashboard screen
 */
@Composable
fun GameScreen(
    gameMode: String,
    difficultyParam: String,
    onNavigateHome: () -> Unit
) {
    val viewModel: ChessViewModel = viewModel(factory = ChessViewModel.Factory())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val boardPieces by viewModel.boardPieces.collectAsStateWithLifecycle()

    // Parse difficulty from navigation parameter
    val difficulty = when (difficultyParam) {
        "easy" -> AIDifficulty.EASY
        "hard" -> AIDifficulty.HARD
        else -> AIDifficulty.MEDIUM
    }

    // Configure the game when the screen first appears
    LaunchedEffect(gameMode, difficultyParam) {
        if (gameMode == "puzzle") {
            viewModel.loadPuzzle()
        } else {
            val isAI = gameMode == "ai"
            viewModel.startNewGame(isAIMode = isAI, difficulty = difficulty)
        }
    }

    // Calculate board size based on screen width
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val boardSize = (screenWidthDp - 32.dp).coerceAtMost(420.dp)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Top Player Info Bar (Black / Opponent) ──────────
            PlayerInfoBar(
                playerName = if (uiState.isAIMode) "Computer" else "Black",
                playerColor = PieceColor.BLACK,
                capturedPieces = uiState.capturedByBlack,
                isCurrentTurn = uiState.currentTurn == PieceColor.BLACK && uiState.gameResult == GameResult.ACTIVE,
                isAIThinking = uiState.isAIThinking,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Status Text ─────────────────────────────────────
            StatusText(
                uiState = uiState,
                gameMode = gameMode
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Chess Board ─────────────────────────────────────
            ChessBoardView(
                uiState = uiState,
                boardPieces = boardPieces,
                onSquareClick = viewModel::onSquareClick,
                boardSize = boardSize
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Bottom Player Info Bar (White / Player) ─────────
            PlayerInfoBar(
                playerName = if (uiState.isAIMode) "You" else "White",
                playerColor = PieceColor.WHITE,
                capturedPieces = uiState.capturedByWhite,
                isCurrentTurn = uiState.currentTurn == PieceColor.WHITE && uiState.gameResult == GameResult.ACTIVE,
                isAIThinking = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Action Buttons ──────────────────────────────────
            ActionButtonRow(
                onUndo = { viewModel.undoMove() },
                onNewGame = {
                    val isAI = gameMode == "ai"
                    viewModel.resetGame(isAIMode = isAI, difficulty = difficulty)
                },
                onHome = onNavigateHome,
                isGameOver = uiState.gameResult != GameResult.ACTIVE,
                canUndo = uiState.moveHistory.isNotEmpty() && !uiState.isAIThinking
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // ── End-Game Dialog ──────────────────────────────────────
    if (uiState.gameResult != GameResult.ACTIVE) {
        EndGameDialog(
            gameResult = uiState.gameResult,
            drawReason = uiState.drawReason,
            onRematch = {
                val isAI = gameMode == "ai"
                viewModel.resetGame(isAIMode = isAI, difficulty = difficulty)
            },
            onHome = onNavigateHome
        )
    }

    // ── Promotion Dialog ─────────────────────────────────────
    if (uiState.pendingPromotion != null) {
        PromotionDialog(
            promotingColor = uiState.pendingPromotion.color,
            onPromotionChosen = viewModel::onPromotionChosen,
            onDismiss = viewModel::onPromotionDismissed
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  PLAYER INFO BAR
// ═══════════════════════════════════════════════════════════════

/**
 * Displays player information including name, turn indicator, and
 * captured pieces as a horizontal row of tiny piece images.
 *
 * The bar is designed to be compact yet informative:
 *   - Left side: Player name and turn indicator dot
 *   - Right side: Flowing row of captured piece thumbnails
 *
 * When it is the player's turn, a glowing gold dot animates beside
 * their name. When the AI is thinking, a pulsing animation appears.
 *
 * @param playerName     Display name for this player
 * @param playerColor    The colour this player is playing as
 * @param capturedPieces List of pieces this player has captured
 * @param isCurrentTurn  Whether it is currently this player's turn
 * @param isAIThinking   Whether the AI is computing a move (Black only)
 * @param modifier       Optional Modifier
 */
@Composable
private fun PlayerInfoBar(
    playerName: String,
    playerColor: PieceColor,
    capturedPieces: List<Piece>,
    isCurrentTurn: Boolean,
    isAIThinking: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Name + Turn indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Turn indicator dot
                if (isCurrentTurn) {
                    val infiniteTransition = rememberInfiniteTransition(label = "turnPulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Gold500)
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = playerName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrentTurn) Gold500 else MaterialTheme.colorScheme.onSurface
                )

                // AI thinking indicator
                if (isAIThinking) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val infiniteTransition = rememberInfiniteTransition(label = "aiThink")
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "aiDotAlpha"
                    )
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha)
                    )
                }
            }

            // Right: Captured pieces
            if (capturedPieces.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    capturedPieces.forEach { piece ->
                        TinyPieceImage(piece = piece)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TINY PIECE IMAGE (for captured piece display)
// ═══════════════════════════════════════════════════════════════

private const val WIKI_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * Renders a tiny chess piece image for the captured pieces display.
 *
 * Uses the same Wikimedia SVG URLs as the board pieces, but at a
 * much smaller size (20dp) and with a dedicated [ImageRequest] that
 * includes the User-Agent header for 403 bypass.
 *
 * @param piece The captured piece to render
 */
@Composable
private fun TinyPieceImage(piece: Piece) {
    val context = LocalContext.current
    val imageUrl = piece.imageUrl

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .addHeader("User-Agent", WIKI_USER_AGENT)
            .crossfade(true)
            .size(60)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = "${piece.color.name} ${piece.type.name}",
        modifier = Modifier.size(20.dp),
        contentScale = ContentScale.Fit
    )
}

// ═══════════════════════════════════════════════════════════════
//  STATUS TEXT
// ═══════════════════════════════════════════════════════════════

/**
 * Displays the current game status: whose turn it is, check warning,
 * or game-over information.
 *
 * The text is colour-coded:
 *   - Normal turn: onSurfaceVariant (muted)
 *   - Check: AccentRed (urgent)
 *   - AI thinking: onSurfaceVariant with pulsing effect
 *
 * @param uiState   Current UI state
 * @param gameMode  Current game mode ("friend", "ai", "puzzle")
 */
@Composable
private fun StatusText(
    uiState: ChessUiState,
    gameMode: String
) {
    val statusText = when {
        uiState.gameResult != GameResult.ACTIVE -> ""
        uiState.isInCheck -> {
            val turnName = if (uiState.currentTurn == PieceColor.WHITE) "White" else "Black"
            "$turnName is in Check!"
        }
        uiState.isAIThinking -> "Computer is thinking..."
        else -> {
            val turnName = if (uiState.currentTurn == PieceColor.WHITE) "White" else "Black"
            "$turnName to move"
        }
    }

    val statusColor = when {
        uiState.isInCheck -> Color(0xFFEF5350)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (statusText.isNotEmpty()) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = statusColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  ACTION BUTTON ROW
// ═══════════════════════════════════════════════════════════════

/**
 * Row of action buttons below the board.
 *
 * Provides:
 *   - Undo: retracts the last move (in AI mode, retracts both the AI
 *     and player moves so the human gets their turn back)
 *   - New Game: resets the board with the same settings
 *   - Home: navigates back to the Dashboard
 *
 * The Undo button is disabled when there is no move history or the
 * AI is currently thinking (to prevent race conditions).
 *
 * @param onUndo     Callback for the Undo button
 * @param onNewGame  Callback for the New Game button
 * @param onHome     Callback for the Home button
 * @param isGameOver Whether the game has ended
 * @param canUndo    Whether undo is currently available
 */
@Composable
private fun ActionButtonRow(
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onHome: () -> Unit,
    isGameOver: Boolean,
    canUndo: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Undo
        OutlinedButton(
            onClick = onUndo,
            enabled = canUndo,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Undo")
        }

        // New Game
        FilledTonalButton(
            onClick = onNewGame,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text("New Game")
        }

        // Home
        OutlinedButton(
            onClick = onHome,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Home")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  END-GAME DIALOG
// ═══════════════════════════════════════════════════════════════

/**
 * Premium dialog displayed when the game ends (checkmate or draw).
 *
 * Shows:
 *   - A large, bold result title (e.g., "Checkmate!")
 *   - A descriptive subtitle (e.g., "Black Wins", "Draw by Stalemate")
 *   - Two action buttons: "Rematch" (resets with same settings) and
 *     "Go Home" (returns to Dashboard)
 *
 * The dialog cannot be dismissed by tapping outside — the player must
 * choose one of the two options. This prevents accidental dismissal
 * of an important game result.
 *
 * @param gameResult The final result of the game
 * @param drawReason If the game ended in a draw, the specific reason
 * @param onRematch  Callback to reset the game with the same settings
 * @param onHome     Callback to navigate back to the Dashboard
 */
@Composable
private fun EndGameDialog(
    gameResult: GameResult,
    drawReason: DrawReason?,
    onRematch: () -> Unit,
    onHome: () -> Unit
) {
    val title = when (gameResult) {
        GameResult.WHITE_WON -> "Checkmate!"
        GameResult.BLACK_WON -> "Checkmate!"
        GameResult.DRAW -> "Draw"
        GameResult.ACTIVE -> ""
    }

    val subtitle = when (gameResult) {
        GameResult.WHITE_WON -> "White Wins"
        GameResult.BLACK_WON -> "Black Wins"
        GameResult.DRAW -> when (drawReason) {
            DrawReason.STALEMATE -> "Draw by Stalemate"
            DrawReason.FIFTY_MOVE_RULE -> "Draw by 50-Move Rule"
            DrawReason.THREEFOLD_REPETITION -> "Draw by Threefold Repetition"
            DrawReason.INSUFFICIENT_MATERIAL -> "Draw by Insufficient Material"
            null -> "Draw"
        }
        GameResult.ACTIVE -> ""
    }

    AlertDialog(
        onDismissRequest = { /* Prevent accidental dismissal */ },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Gold500,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            ElevatedButton(
                onClick = onRematch,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Gold500,
                    contentColor = Navy900
                )
            ) {
                Text(
                    text = "Rematch",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onHome,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Go Home")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ═══════════════════════════════════════════════════════════════
//  PROMOTION DIALOG
// ═══════════════════════════════════════════════════════════════

/**
 * Dialog for selecting a pawn promotion piece.
 *
 * When a pawn reaches the last rank, this dialog presents four
 * piece options — Queen, Rook, Bishop, and Knight — rendered as
 * large, tappable piece images from Wikimedia Commons.
 *
 * The dialog can be dismissed without choosing (which cancels the
 * promotion), though in standard chess a promotion piece must be
 * selected. The dismiss option is provided as a safety net.
 *
 * @param promotingColor   The colour of the promoting pawn
 * @param onPromotionChosen Callback invoked with the selected [PieceType]
 * @param onDismiss         Callback invoked if the dialog is dismissed
 */
@Composable
private fun PromotionDialog(
    promotingColor: PieceColor,
    onPromotionChosen: (PieceType) -> Unit,
    onDismiss: () -> Unit
) {
    val promotionTypes = listOf(
        PieceType.QUEEN,
        PieceType.ROOK,
        PieceType.BISHOP,
        PieceType.KNIGHT
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Promote Pawn",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                promotionTypes.forEach { type ->
                    val piece = Piece(type, promotingColor)
                    val context = LocalContext.current
                    val imageUrl = piece.imageUrl

                    val imageRequest = remember(imageUrl) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .addHeader("User-Agent", WIKI_USER_AGENT)
                            .crossfade(true)
                            .size(120)
                            .build()
                    }

                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        onClick = { onPromotionChosen(type) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "${promotingColor.name} ${type.name}",
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
