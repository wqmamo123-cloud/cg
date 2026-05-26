# Chess App ProGuard Rules
# ========================

# Keep data classes used by Compose state and serialization
-keep class com.example.chess.model.** { *; }
-keep class com.example.chess.engine.AIDifficulty { *; }
-keep class com.example.chess.engine.GameResult { *; }
-keep class com.example.chess.engine.DrawReason { *; }
-keep class com.example.chess.engine.CastlingRights { *; }

# Keep Compose-related classes
-dontwarn androidx.compose.**
