package com.example.chess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.ui.theme.CardAmberEnd
import com.example.chess.ui.theme.CardAmberStart
import com.example.chess.ui.theme.CardBlueEnd
import com.example.chess.ui.theme.CardBlueStart
import com.example.chess.ui.theme.CardGreenEnd
import com.example.chess.ui.theme.CardGreenStart
import com.example.chess.ui.theme.CardPurpleEnd
import com.example.chess.ui.theme.CardPurpleStart
import com.example.chess.ui.theme.Gold500
import com.example.chess.ui.theme.Navy900

/**
 * Premium main menu (Dashboard) for the Chess Application.
 *
 * Displays four beautiful gradient cards for the primary features:
 *   1. Play vs Friend   — local pass-and-play on the same device
 *   2. Play vs Computer — AI opponent with Easy / Medium / Hard
 *   3. Puzzles          — Mate-in-1 tactical scenarios
 *   4. Chess Academy    — interactive learning modules
 *
 * Each card has a distinct gradient colour, a Material icon, a bold
 * title, and a descriptive subtitle. Tapping a card either navigates
 * directly to the game screen or (for "Play vs Computer") opens a
 * difficulty selection dialog.
 *
 * @param onNavigateToGame   Callback invoked with (mode, difficulty) when a
 *                           game mode is selected. mode is "friend", "ai", or "puzzle".
 *                           difficulty is "easy", "medium", or "hard".
 * @param onNavigateToAcademy Callback invoked when the Academy card is tapped.
 */
@Composable
fun DashboardScreen(
    onNavigateToGame: (mode: String, difficulty: String) -> Unit,
    onNavigateToAcademy: () -> Unit
) {
    // State for the AI difficulty selection dialog
    var showDifficultyDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── App Title ──────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Chess Master",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = 1.sp
                ),
                color = Gold500
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Elevate your game",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Menu Cards ─────────────────────────────────────
            // 1. Play vs Friend
            MenuCard(
                title = "Play vs Friend",
                subtitle = "Local pass-and-play on one device",
                icon = Icons.Outlined.Group,
                gradientStart = CardBlueStart,
                gradientEnd = CardBlueEnd,
                onClick = { onNavigateToGame("friend", "medium") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Play vs Computer
            MenuCard(
                title = "Play vs Computer",
                subtitle = "Challenge the AI at your level",
                icon = Icons.Outlined.Computer,
                gradientStart = CardGreenStart,
                gradientEnd = CardGreenEnd,
                onClick = { showDifficultyDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Puzzles
            MenuCard(
                title = "Puzzles",
                subtitle = "Solve tactical Mate-in-1 scenarios",
                icon = Icons.Outlined.Extension,
                gradientStart = CardAmberStart,
                gradientEnd = CardAmberEnd,
                onClick = { onNavigateToGame("puzzle", "medium") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Chess Academy
            MenuCard(
                title = "Chess Academy",
                subtitle = "Learn strategies and master openings",
                icon = Icons.Outlined.School,
                gradientStart = CardPurpleStart,
                gradientEnd = CardPurpleEnd,
                onClick = onNavigateToAcademy
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // ── AI Difficulty Selection Dialog ──────────────────────
    if (showDifficultyDialog) {
        DifficultyDialog(
            onSelect = { difficulty ->
                showDifficultyDialog = false
                onNavigateToGame("ai", difficulty)
            },
            onDismiss = { showDifficultyDialog = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  MENU CARD COMPOSABLE
// ═══════════════════════════════════════════════════════════════

/**
 * A premium gradient card for the main menu.
 *
 * Each card features:
 *   - A diagonal gradient background (from [gradientStart] to [gradientEnd])
 *   - A large Material [icon] on the left
 *   - A bold [title] and lighter [subtitle] on the right
 *   - Rounded corners, subtle elevation via background layering
 *   - A click handler that navigates to the appropriate screen
 *
 * The card is designed for maximum tap target size and visual appeal.
 * The gradient and icon make each option instantly recognisable.
 *
 * @param title         Primary label (e.g., "Play vs Friend")
 * @param subtitle      Secondary description (e.g., "Local pass-and-play")
 * @param icon          Material icon representing this feature
 * @param gradientStart Start colour of the diagonal gradient
 * @param gradientEnd   End colour of the diagonal gradient
 * @param onClick       Callback invoked when the card is tapped
 */
@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientStart: Color,
    gradientEnd: Color,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(cardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(gradientStart, gradientEnd)
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Semi-transparent overlay for depth effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.05f))
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(44.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.width(20.dp))

            // Text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Chevron indicator
            Text(
                text = ">",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Light
                ),
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  DIFFICULTY SELECTION DIALOG
// ═══════════════════════════════════════════════════════════════

/**
 * Dialog for selecting AI difficulty before starting a game.
 *
 * Presents three options — Easy, Medium, and Hard — each with a
 * brief description of the AI's playing strength. The dialog uses
 * [OutlinedButton]s with colour coding to make each difficulty
 * visually distinct:
 *
 *   - Easy:   Green accent (relaxed, forgiving)
 *   - Medium: Amber accent (balanced, competitive)
 *   - Hard:   Red accent (challenging, aggressive)
 *
 * @param onSelect  Callback invoked with the selected difficulty string
 *                  ("easy", "medium", or "hard")
 * @param onDismiss Callback invoked when the dialog is dismissed
 */
@Composable
private fun DifficultyDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Difficulty",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Easy
                DifficultyOption(
                    label = "Easy",
                    description = "Beginner-friendly. Looks 1 move ahead.",
                    color = Color(0xFF4CAF50),
                    onClick = { onSelect("easy") }
                )

                // Medium
                DifficultyOption(
                    label = "Medium",
                    description = "Balanced challenge. Looks 2 moves ahead.",
                    color = Color(0xFFFFA726),
                    onClick = { onSelect("medium") }
                )

                // Hard
                DifficultyOption(
                    label = "Hard",
                    description = "Expert-level play. Looks 3 moves ahead.",
                    color = Color(0xFFEF5350),
                    onClick = { onSelect("hard") }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * A single difficulty option row within the [DifficultyDialog].
 *
 * Each option is an [OutlinedButton] styled with the given [color]
 * accent. The [label] is displayed in bold, and the [description]
 * provides context about the AI's search depth and playing style.
 *
 * @param label       Difficulty name (e.g., "Easy")
 * @param description Brief explanation of this difficulty level
 * @param color       Accent colour for this option's border and text
 * @param onClick     Callback invoked when this option is tapped
 */
@Composable
private fun DifficultyOption(
    label: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
