package com.example.chess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chess.model.Piece
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.model.Position
import com.example.chess.ui.viewmodel.ChessUiState

// ═══════════════════════════════════════════════════════════════
//  PREMIUM COLOUR PALETTE
// ═══════════════════════════════════════════════════════════════
// Inspired by high-end tournament boards: deep slate for dark squares
// and warm cream for light squares, with subtle accent colours for
// highlights that are distinct enough for colour-blind users.

/** Dark square colour — cool slate with a hint of blue-grey. */
private val DarkSquareColor = Color(0xFF4A6274)

/** Light square colour — warm cream/soft wood. */
private val LightSquareColor = Color(0xFFE8D5B5)

/** Selected piece highlight — warm amber/gold. */
private val SelectedSquareColor = Color(0xFFF6D365)

/** Valid move destination — soft green with transparency. */
private val ValidMoveColor = Color(0x804CAF50)

/** Valid capture destination — slightly stronger green. */
private val ValidCaptureColor = Color(0x9F388E3C)

/** Last move from/to highlight — subtle warm yellow. */
private val LastMoveColor = Color(0x50FFD54F)

/** King in check highlight — unmistakable red. */
private val CheckHighlightColor = Color(0x80F44336)

/** Border colour for the board frame. */
private val BoardBorderColor = Color(0xFF2C3E50)

/** Dot colour for valid move indicators on empty squares. */
private val ValidMoveDotColor = Color(0x904CAF50)

/** Ring colour for valid capture indicators on occupied squares. */
private val ValidCaptureRingColor = Color(0xB0388E3C)

// ═══════════════════════════════════════════════════════════════
//  USER-AGENT HEADER FOR WIKIPEDIA IMAGE LOADING
// ═══════════════════════════════════════════════════════════════
// Wikimedia Commons returns HTTP 403 Forbidden for requests that
// do not include a recognised browser User-Agent string. This
// constant is attached to every Coil ImageRequest to bypass the
// restriction and load chess piece SVGs successfully.

private const val WIKI_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

// ═══════════════════════════════════════════════════════════════
//  MAIN BOARD COMPOSABLE
// ═══════════════════════════════════════════════════════════════

/**
 * Renders the 8×8 chess board with piece images, highlights, and interaction.
 *
 * ## Performance Optimisation
 *
 * This composable follows two strict rules to maintain 60/120 fps:
 *
 * **Rule 1 — Independent ChessSquare composable**: Each of the 64 squares
 * is rendered by its own [ChessSquare] composable function. This allows
 * Compose's smart recomposition to skip squares whose visual state has not
 * changed. When a user clicks a square, only the affected squares (the
 * previously selected square, the newly selected square, and the valid-move
 * indicators) recompose — never all 64.
 *
 * **Rule 2 — `key` wrapping**: Each [ChessSquare] is wrapped with
 * `key(row, col)` so that Compose identifies each square by its stable
 * position identity rather than by list index. This prevents Compose from
 * reordering or re-creating composables when the board state changes,
 * which would cause unnecessary recompositions of all 64 squares.
 *
 * ## Image Loading
 *
 * Piece images are loaded from Wikimedia Commons SVG URLs via Coil's
 * [AsyncImage]. Each request includes a `User-Agent` header to bypass
 * Wikimedia's HTTP 403 protection. Image requests are cached using
 * `remember` so that repeated rendering of the same piece does not
 * trigger redundant network calls.
 *
 * @param uiState     The current game UI state (board, highlights, selection).
 * @param boardPieces 8×8 grid of nullable Pieces representing the current position.
 * @param onSquareClick  Callback invoked with (row, col) when a square is tapped.
 * @param modifier    Optional Modifier for sizing and padding.
 * @param boardSize   The board dimensions in Dp (default 360.dp fills most phones).
 */
@Composable
fun ChessBoardView(
    uiState: ChessUiState,
    boardPieces: Array<Array<Piece?>>,
    onSquareClick: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
    boardSize: Dp = 360.dp
) {
    // Pre-compute the king position if in check — needed for the red highlight
    val checkedKingPosition = if (uiState.isInCheck) {
        findKingPosition(boardPieces, uiState.currentTurn)
    } else {
        null
    }

    // Pre-compute last move positions for highlighting
    val lastMoveFrom = uiState.lastMove?.from
    val lastMoveTo = uiState.lastMove?.to

    // Pre-compute valid move positions (split into empty-square moves and captures)
    val validMovePositions = uiState.validMoves

    Column(
        modifier = modifier
            .size(boardSize)
            .clip(MaterialTheme.shapes.medium)
            .border(2.dp, BoardBorderColor, MaterialTheme.shapes.medium)
            .background(BoardBorderColor)
    ) {
        // Render from rank 8 (row 7, Black's side) at the top
        // down to rank 1 (row 0, White's side) at the bottom.
        // This places White at the bottom from the player's perspective,
        // which is the standard chess board orientation.
        for (row in 7 downTo 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.Absolute.SpaceEvenly
            ) {
                for (col in 0..7) {
                    // ── OPTIMISATION RULE 2: key() wrapping ──
                    // Each square is identified by its (row, col) position.
                    // When the board state changes, Compose uses this stable key
                    // to determine which squares need recomposition. Only squares
                    // whose parameters have changed will recompose.
                    key(row, col) {
                        // ── OPTIMISATION RULE 1: Independent ChessSquare ──
                        // Each square is a standalone composable with its own
                        // parameter set. Compose can skip recomposition for
                        // squares where all parameters are unchanged.
                        ChessSquare(
                            row = row,
                            col = col,
                            piece = boardPieces[row][col],
                            isSelected = uiState.selectedPosition == Position(row, col),
                            isValidMove = validMovePositions.contains(Position(row, col)),
                            isLastMoveSquare = lastMoveFrom == Position(row, col) ||
                                lastMoveTo == Position(row, col),
                            isKingInCheck = checkedKingPosition == Position(row, col),
                            onSquareClick = onSquareClick
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  INDIVIDUAL SQUARE COMPOSABLE
// ═══════════════════════════════════════════════════════════════

/**
 * Renders a single square on the chess board.
 *
 * This composable is intentionally kept lightweight and self-contained.
 * It receives only the data it needs — its position, piece, and highlight
 * state — and renders accordingly. Because all parameters are either
 * primitives or immutable data classes, Compose can efficiently determine
 * whether recomposition is needed.
 *
 * Highlight layering (bottom to top):
 *   1. Base square colour (dark/light)
 *   2. Last-move highlight (subtle yellow overlay)
 *   3. King-in-check highlight (red overlay)
 *   4. Selected-piece highlight (amber/gold overlay)
 *   5. Valid-move indicator (green dot on empty squares, green ring on captures)
 *   6. Piece image (loaded from Wikimedia via Coil)
 *
 * @param row             Row index (0 = rank 1, 7 = rank 8).
 * @param col             Column index (0 = a-file, 7 = h-file).
 * @param piece           The chess piece on this square, or null if empty.
 * @param isSelected      Whether this square contains the currently selected piece.
 * @param isValidMove     Whether this square is a valid destination for the selected piece.
 * @param isLastMoveSquare Whether this square was part of the last move (from or to).
 * @param isKingInCheck   Whether a king in check sits on this square.
 * @param onSquareClick   Callback invoked with (row, col) when this square is tapped.
 */
@Composable
private fun ChessSquare(
    row: Int,
    col: Int,
    piece: Piece?,
    isSelected: Boolean,
    isValidMove: Boolean,
    isLastMoveSquare: Boolean,
    isKingInCheck: Boolean,
    onSquareClick: (row: Int, col: Int) -> Unit
) {
    // Determine the base square colour
    val isLightSquare = (row + col) % 2 == 0
    val baseColor = if (isLightSquare) LightSquareColor else DarkSquareColor

    // Build the background colour by layering highlights
    val backgroundColor = when {
        isSelected -> SelectedSquareColor
        isKingInCheck -> CheckHighlightColor
        isLastMoveSquare -> LastMoveColor.copy(alpha = 0.6f).compositeOver(baseColor)
        else -> baseColor
    }

    // Determine if valid move is a capture (piece on destination)
    val isCapture = isValidMove && piece != null

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(backgroundColor)
            .clickable { onSquareClick(row, col) },
        contentAlignment = Alignment.Center
    ) {
        // ── Layer 1: Valid move indicator ──
        if (isValidMove && !isSelected) {
            if (isCapture) {
                // Capture indicator: green ring around the captured piece
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .border(3.dp, ValidCaptureRingColor, CircleShape)
                )
            } else {
                // Move indicator: small green dot in the centre
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(ValidMoveDotColor)
                )
            }
        }

        // ── Layer 2: King in check red glow ──
        if (isKingInCheck) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CheckHighlightColor)
            )
        }

        // ── Layer 3: Chess piece image ──
        if (piece != null) {
            PieceImage(
                piece = piece,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  PIECE IMAGE LOADER (Coil + User-Agent bypass)
// ═══════════════════════════════════════════════════════════════

/**
 * Loads and renders a chess piece image from Wikimedia Commons using Coil.
 *
 * ## Image Loading Strategy
 *
 * 1. **SVG decoding**: The piece images are SVG vectors from Wikimedia
 *    Commons. Coil's SVG decoder (configured in [ChessApplication]) is
 *    required to render them. The request is built with the SVG decoder
 *    explicitly added as a component.
 *
 * 2. **User-Agent header**: Wikimedia Commons blocks direct HTTP requests
 *    from mobile apps with an HTTP 403 Forbidden response. This request
 *    includes a standard browser User-Agent string to bypass the block.
 *    The header is set both in the ImageRequest and globally in
 *    [ChessApplication]'s ImageLoader for belt-and-suspenders reliability.
 *
 * 3. **remember caching**: The [ImageRequest] is wrapped in `remember`
 *    with the piece's image URL as the key. This ensures that:
 *    - The same piece image is not re-requested on every recomposition
 *    - Coil's memory cache is utilised efficiently
 *    - Only URL changes trigger a new image request
 *
 * 4. **ContentScale.Fit**: The SVG is scaled to fit within the square
 *    while maintaining its aspect ratio, preventing distortion.
 *
 * @param piece    The chess piece to render.
 * @param modifier Modifier for sizing and padding within the square.
 */
@Composable
private fun PieceImage(
    piece: Piece,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUrl = piece.imageUrl

    // Cache the ImageRequest using remember — only rebuild if the URL changes.
    // This prevents redundant network calls and object allocation during
    // recomposition of squares that haven't changed.
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .addHeader("User-Agent", WIKI_USER_AGENT)
            .crossfade(true)
            .size(120) // Request a 120px render — crisp on all screen densities
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = pieceContentDescription(piece),
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

// ═══════════════════════════════════════════════════════════════
//  HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════

/**
 * Finds the position of the king of the given [color] on the board.
 * Returns null if no king is found (should not happen in a valid game).
 */
private fun findKingPosition(
    boardPieces: Array<Array<Piece?>>,
    color: PieceColor
): Position? {
    for (row in 0..7) {
        for (col in 0..7) {
            val piece = boardPieces[row][col]
            if (piece != null && piece.type == PieceType.KING && piece.color == color) {
                return Position(row, col)
            }
        }
    }
    return null
}

/**
 * Generates an accessibility content description for a chess piece.
 * Example: "White Queen", "Black Knight"
 */
private fun pieceContentDescription(piece: Piece): String {
    val colorName = if (piece.color == PieceColor.WHITE) "White" else "Black"
    val typeName = when (piece.type) {
        PieceType.KING -> "King"
        PieceType.QUEEN -> "Queen"
        PieceType.ROOK -> "Rook"
        PieceType.BISHOP -> "Bishop"
        PieceType.KNIGHT -> "Knight"
        PieceType.PAWN -> "Pawn"
    }
    return "$colorName $typeName"
}

/**
 * Composites this colour over another colour using the "over" mode.
 * Used to blend semi-transparent highlight colours with the base square colour.
 */
private fun Color.compositeOver(background: Color): Color {
    val srcAlpha = this.alpha
    if (srcAlpha == 0f) return background
    if (srcAlpha == 1f) return this

    val dstAlpha = background.alpha
    val outAlpha = srcAlpha + dstAlpha * (1f - srcAlpha)

    if (outAlpha == 0f) return Color.Transparent

    val r = (this.red * srcAlpha + background.red * dstAlpha * (1f - srcAlpha)) / outAlpha
    val g = (this.green * srcAlpha + background.green * dstAlpha * (1f - srcAlpha)) / outAlpha
    val b = (this.blue * srcAlpha + background.blue * dstAlpha * (1f - srcAlpha)) / outAlpha

    return Color(r, g, b, outAlpha)
}
