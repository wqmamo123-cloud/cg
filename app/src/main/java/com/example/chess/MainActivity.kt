package com.example.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chess.ui.screens.AcademyScreen
import com.example.chess.ui.screens.DashboardScreen
import com.example.chess.ui.screens.GameScreen
import com.example.chess.ui.theme.ChessTheme

/**
 * Main entry point for the Chess Application.
 *
 * Sets up the edge-to-edge display, applies the premium [ChessTheme],
 * and hosts the [NavHost] that routes between the Dashboard, Game,
 * and Academy screens.
 *
 * Navigation routes:
 *   - `dashboard`           : Main menu (start destination)
 *   - `game/{mode}/{difficulty}` : Active chess game
 *   - `academy`             : Chess Academy (learning)
 *
 * The `mode` parameter is one of: "friend", "ai", "puzzle".
 * The `difficulty` parameter is one of: "easy", "medium", "hard".
 * For friend and puzzle modes, the difficulty is ignored but still
 * required in the route for uniformity.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChessNavHost()
                }
            }
        }
    }
}

/**
 * Top-level navigation host for the Chess Application.
 *
 * Manages the back stack and routes between the three primary screens.
 * The [DashboardScreen] is the start destination; from there, users
 * navigate to the [GameScreen] (with mode and difficulty parameters)
 * or the [AcademyScreen].
 *
 * Navigation callbacks are passed down to each screen so that button
 * presses trigger route changes. The Game screen receives its mode
 * and difficulty via route arguments, which it uses to configure the
 * ViewModel when the screen first appears.
 */
@Composable
fun ChessNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        // ── Dashboard ──────────────────────────────────────
        composable("dashboard") {
            DashboardScreen(
                onNavigateToGame = { mode, difficulty ->
                    navController.navigate("game/$mode/$difficulty")
                },
                onNavigateToAcademy = {
                    navController.navigate("academy")
                }
            )
        }

        // ── Game ───────────────────────────────────────────
        composable(
            route = "game/{mode}/{difficulty}",
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "friend"
                },
                navArgument("difficulty") {
                    type = NavType.StringType
                    defaultValue = "medium"
                }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "friend"
            val difficulty = backStackEntry.arguments?.getString("difficulty") ?: "medium"

            GameScreen(
                gameMode = mode,
                difficultyParam = difficulty,
                onNavigateHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        // ── Academy ────────────────────────────────────────
        composable("academy") {
            AcademyScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
