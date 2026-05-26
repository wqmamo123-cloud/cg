package com.example.chess.engine

import com.example.chess.model.ChessBoard
import com.example.chess.model.Move
import com.example.chess.model.Piece
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.model.Position

/**
 * AI difficulty levels, each mapped to a specific Minimax search depth.
 *
 * Deeper searches examine more possible futures and produce stronger play,
 * but take exponentially more time. The chosen depths balance strength
 * against response time on modern Android hardware:
 *
 *   - EASY:   depth 1 — looks only at immediate captures and threats.
 *             Plays loosely, often ignores hanging pieces.
 *
 *   - MEDIUM: depth 2 — considers the opponent's immediate response.
 *             Avoids one-move blunders and spots simple tactics.
 *
 *   - HARD:   depth 3 — sees two moves ahead for both sides.
 *             Finds two-move combinations, avoids simple traps,
 *             and plays competitively for casual players.
 */
enum class AIDifficulty(val depth: Int) {
    EASY(1),
    MEDIUM(2),
    HARD(3)
}

/**
 * Minimax-based chess AI with Alpha-Beta pruning and positional evaluation.
 *
 * Algorithm overview:
 *   The AI uses the classic Minimax algorithm with the following enhancements:
 *
 *   1. **Alpha-Beta Pruning**: Eliminates branches that cannot possibly
 *      influence the final decision, reducing the search tree from O(b^d)
 *      to approximately O(b^(d/2)) in the best case, where b is the
 *      branching factor (~35 for chess) and d is the search depth.
 *
 *   2. **Move Ordering**: Candidate moves are sorted so that promising moves
 *      (captures, especially of high-value pieces) are examined first. This
 *      dramatically improves the effectiveness of alpha-beta pruning because
 *      early refutations cut off more branches.
 *
 *   3. **Piece-Square Tables (PSTs)**: Positional bonuses are added to the
 *      raw material evaluation. Each piece type has a pre-computed 8×8 table
 *      that assigns bonuses or penalties based on the square the piece
 *      occupies. This encourages the AI to:
 *      - Control the centre with pawns and knights
 *      - Develop bishops and knights early
 *      - Advance pawns toward promotion
 *      - Keep the king safe in the middlegame
 *
 *   4. **Evaluation Function**: The static evaluation sums material values
 *      and positional bonuses for both sides and returns the difference
 *      from White's perspective. A positive score favours White; a negative
 *      score favours Black.
 *
 * Thread safety: This class is stateless. All methods are pure functions
 * that operate on the provided board and move generator. It can be safely
 * called from any coroutine (e.g., a ViewModel scope for background AI
 * computation) without synchronization.
 */
class ChessAI {

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Finds the best move for the given [game] state at the specified [difficulty].
     *
     * This method extracts the current position from the game, runs the Minimax
     * search with Alpha-Beta pruning, and returns the move that leads to the
     * highest evaluation for the side to move.
     *
     * Returns null if:
     *   - The game is over (no legal moves exist)
     *   - No legal moves are available
     *
     * @param game        The current game state.
     * @param difficulty  The AI difficulty, which determines the search depth.
     * @return The best move found, or null if no move is available.
     */
    fun findBestMove(game: ChessGame, difficulty: AIDifficulty): Move? {
        val chessBoard = game.toChessBoard()
        val currentColor = game.getCurrentTurn()
        val legalMoves = moveGenerator.generateLegalMoves(chessBoard, currentColor)

        if (legalMoves.isEmpty()) return null

        // Order moves for better alpha-beta pruning
        val orderedMoves = orderMoves(legalMoves, chessBoard)

        var bestMove: Move? = null
        val isMaximizing = currentColor == PieceColor.WHITE

        // Initial alpha/beta bounds
        var alpha = NEGATIVE_INFINITY
        var beta = POSITIVE_INFINITY

        if (isMaximizing) {
            var bestScore = NEGATIVE_INFINITY
            for (move in orderedMoves) {
                val newBoard = chessBoard.applyMove(move)
                val score = minimax(newBoard, difficulty.depth - 1, alpha, beta, false)
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move
                }
                alpha = maxOf(alpha, score)
            }
        } else {
            var bestScore = POSITIVE_INFINITY
            for (move in orderedMoves) {
                val newBoard = chessBoard.applyMove(move)
                val score = minimax(newBoard, difficulty.depth - 1, alpha, beta, true)
                if (score < bestScore) {
                    bestScore = score
                    bestMove = move
                }
                beta = minOf(beta, score)
            }
        }

        return bestMove
    }

    /**
     * Overload that accepts raw board components instead of a ChessGame.
     * Useful for the AI's internal recursive search and for testing.
     */
    fun findBestMove(
        chessBoard: ChessBoard,
        color: PieceColor,
        difficulty: AIDifficulty
    ): Move? {
        val legalMoves = moveGenerator.generateLegalMoves(chessBoard, color)
        if (legalMoves.isEmpty()) return null

        val orderedMoves = orderMoves(legalMoves, chessBoard)
        val isMaximizing = color == PieceColor.WHITE
        var alpha = NEGATIVE_INFINITY
        var beta = POSITIVE_INFINITY
        var bestMove: Move? = null

        if (isMaximizing) {
            var bestScore = NEGATIVE_INFINITY
            for (move in orderedMoves) {
                val newBoard = chessBoard.applyMove(move)
                val score = minimax(newBoard, difficulty.depth - 1, alpha, beta, false)
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move
                }
                alpha = maxOf(alpha, score)
            }
        } else {
            var bestScore = POSITIVE_INFINITY
            for (move in orderedMoves) {
                val newBoard = chessBoard.applyMove(move)
                val score = minimax(newBoard, difficulty.depth - 1, alpha, beta, true)
                if (score < bestScore) {
                    bestScore = score
                    bestMove = move
                }
                beta = minOf(beta, score)
            }
        }

        return bestMove
    }

    // ═══════════════════════════════════════════════════════════════
    //  MINIMAX WITH ALPHA-BETA PRUNING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Recursive Minimax search with Alpha-Beta pruning.
     *
     * Explores the game tree to the specified [depth], alternating between
     * maximising (White) and minimising (Black) players. The alpha-beta
     * window narrows as the search progresses, and branches that fall
     * outside the window are pruned immediately.
     *
     * @param board          The current board state (already has the move applied).
     * @param depth          Remaining search depth. When 0, the position is
     *                       evaluated statically.
     * @param alpha          Best score White can guarantee (lower bound).
     * @param beta           Best score Black can guarantee (upper bound).
     * @param isMaximizing   True if it is White's turn to move in this node.
     * @return The evaluation score of the best continuation from this position.
     */
    private fun minimax(
        board: ChessBoard,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean
    ): Int {
        val currentColor = if (isMaximizing) PieceColor.WHITE else PieceColor.BLACK
        val legalMoves = moveGenerator.generateLegalMoves(board, currentColor)

        // ── Terminal conditions ──────────────────────────────────
        if (legalMoves.isEmpty()) {
            // No legal moves: either checkmate or stalemate
            return if (moveGenerator.isInCheck(board, currentColor)) {
                // Checkmate — the current player lost
                // Return a score that is worse the deeper the checkmate is
                // (prefer quicker checkmates and slower losses)
                if (isMaximizing) {
                    NEGATIVE_INFINITY + (MAX_DEPTH - depth)
                } else {
                    POSITIVE_INFINITY - (MAX_DEPTH - depth)
                }
            } else {
                // Stalemate — draw
                0
            }
        }

        // Leaf node — return static evaluation
        if (depth <= 0) {
            return evaluate(board)
        }

        // ── Alpha-Beta search ────────────────────────────────────
        val orderedMoves = orderMoves(legalMoves, board)

        if (isMaximizing) {
            var maxScore = NEGATIVE_INFINITY
            var currentAlpha = alpha
            for (move in orderedMoves) {
                val newBoard = board.applyMove(move)
                val score = minimax(newBoard, depth - 1, currentAlpha, beta, false)
                maxScore = maxOf(maxScore, score)
                currentAlpha = maxOf(currentAlpha, score)
                if (beta <= currentAlpha) {
                    break // Beta cutoff — Black can do better elsewhere
                }
            }
            return maxScore
        } else {
            var minScore = POSITIVE_INFINITY
            var currentBeta = beta
            for (move in orderedMoves) {
                val newBoard = board.applyMove(move)
                val score = minimax(newBoard, depth - 1, alpha, currentBeta, true)
                minScore = minOf(minScore, score)
                currentBeta = minOf(currentBeta, score)
                if (currentBeta <= alpha) {
                    break // Alpha cutoff — White can do better elsewhere
                }
            }
            return minScore
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MOVE ORDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Orders moves to improve alpha-beta pruning efficiency.
     *
     * Moves that are likely to be good are examined first so that the
     * alpha-beta window narrows quickly, causing more cutoffs in later
     * branches. The ordering heuristic is:
     *
     *   1. **Captures** (sorted by MVV-LVA: Most Valuable Victim –
     *      Least Valuable Attacker). Capturing a queen with a pawn
     *      is examined before capturing a pawn with a queen.
     *
     *   2. **Promotions** (queen promotions first, then others).
     *
     *   3. **Non-captures** (remaining moves, unsorted).
     *
     * This simple ordering provides a significant speedup over random
     * ordering, typically reducing the number of nodes searched by 50–80%.
     */
    private fun orderMoves(moves: List<Move>, board: ChessBoard): List<Move> {
        return moves.sortedByDescending { move ->
            var score = 0

            // Capture bonus: MVV-LVA
            val capturedPiece = board.getPiece(move.to)
            if (capturedPiece != null) {
                val victimValue = pieceValue(capturedPiece.type)
                val attackerValue = board.getPiece(move.from)?.let { pieceValue(it.type) } ?: 0
                score += CAPTURE_BONUS + victimValue * 10 - attackerValue
            }

            // Promotion bonus
            if (move.promotionType != null) {
                score += PROMOTION_BONUS + pieceValue(move.promotionType)
            }

            // En passant bonus (capture of a pawn)
            if (move.isEnPassant) {
                score += CAPTURE_BONUS + pieceValue(PieceType.PAWN) * 10
            }

            score
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STATIC EVALUATION FUNCTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Evaluates the position statically (no search).
     *
     * The evaluation is the sum of:
     *   - Material values for all pieces on the board
     *   - Positional bonuses from piece-square tables
     *
     * The score is from White's perspective:
     *   - Positive = White is better
     *   - Negative = Black is better
     *   - Zero = approximately equal
     *
     * The evaluation is the difference between White's total and Black's total:
     *   evaluate = (whiteMaterial + whitePositional) - (blackMaterial + blackPositional)
     */
    private fun evaluate(board: ChessBoard): Int {
        var whiteScore = 0
        var blackScore = 0

        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board.getPiece(Position(row, col)) ?: continue
                val materialValue = pieceValue(piece.type)
                val positionalValue = getPositionalBonus(piece, row, col)

                if (piece.color == PieceColor.WHITE) {
                    whiteScore += materialValue + positionalValue
                } else {
                    blackScore += materialValue + positionalValue
                }
            }
        }

        return whiteScore - blackScore
    }

    /**
     * Returns the standard material value of a piece type.
     *
     * These values are in "centipawns" (1/100th of a pawn) and are the
     * conventional values used in chess programming:
     *
     *   - Pawn:   100
     *   - Knight: 320  (≈3.2 pawns)
     *   - Bishop: 330  (≈3.3 pawns, slightly better than knight)
     *   - Rook:   500  (=5 pawns)
     *   - Queen:  900  (=9 pawns)
     *   - King:   20000 (effectively infinite — losing the king ends the game)
     */
    private fun pieceValue(type: PieceType): Int {
        return when (type) {
            PieceType.PAWN -> PAWN_VALUE
            PieceType.KNIGHT -> KNIGHT_VALUE
            PieceType.BISHOP -> BISHOP_VALUE
            PieceType.ROOK -> ROOK_VALUE
            PieceType.QUEEN -> QUEEN_VALUE
            PieceType.KING -> KING_VALUE
        }
    }

    /**
     * Returns the positional bonus for a [piece] at the given [row] and [col].
     *
     * The bonus comes from a pre-computed piece-square table (PST) that assigns
     * a value to each square for each piece type. For White pieces, the table
     * is indexed directly. For Black pieces, the row is mirrored (7 - row)
     * so that the same strategic principles apply from Black's perspective.
     *
     * The PST values are measured in centipawns and typically range from -50
     * to +50. They are small enough to never override a material advantage
     * but significant enough to guide the AI toward strategically sound play.
     */
    private fun getPositionalBonus(piece: Piece, row: Int, col: Int): Int {
        val tableRow = if (piece.color == PieceColor.WHITE) row else 7 - row
        return when (piece.type) {
            PieceType.PAWN -> PAWN_PST[tableRow][col]
            PieceType.KNIGHT -> KNIGHT_PST[tableRow][col]
            PieceType.BISHOP -> BISHOP_PST[tableRow][col]
            PieceType.ROOK -> ROOK_PST[tableRow][col]
            PieceType.QUEEN -> QUEEN_PST[tableRow][col]
            PieceType.KING -> KING_MIDDLEGAME_PST[tableRow][col]
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PIECE-SQUARE TABLES
    // ═══════════════════════════════════════════════════════════════
    //
    // All tables are from White's perspective in our coordinate system:
    //   - Row 0 = rank 1 (White's back rank)
    //   - Row 7 = rank 8 (Black's back rank)
    //
    // For Black pieces, the row index is mirrored: [7 - row][col]
    //
    // Values are in centipawns. Positive values encourage the AI to place
    // pieces on those squares; negative values discourage it.
    //
    // Source: Standard tables from the Chess Programming Wiki, adapted to
    // our coordinate system.

    /**
     * Pawn piece-square table.
     *
     * Strategic goals encoded:
     *   - Central pawns (d4, e4, d5, e5) get bonuses for controlling the centre
     *   - Advanced pawns get increasing bonuses as they approach promotion
     *   - Edge pawns (a and h files) get penalties — they are less flexible
     *   - Pawns on the starting rank that block central advances are penalised
     */
    private val PAWN_PST = arrayOf(
        intArrayOf(  0,   0,   0,   0,   0,   0,   0,   0), // rank 1 (pawns never here)
        intArrayOf(  5,  10,  10, -20, -20,  10,  10,   5), // rank 2 (starting)
        intArrayOf(  5,  -5, -10,   0,   0, -10,  -5,   5), // rank 3
        intArrayOf(  0,   0,   0,  20,  20,   0,   0,   0), // rank 4 (centre)
        intArrayOf(  5,   5,  10,  25,  25,  10,   5,   5), // rank 5
        intArrayOf( 10,  10,  20,  30,  30,  20,  10,  10), // rank 6
        intArrayOf( 50,  50,  50,  50,  50,  50,  50,  50), // rank 7 (near promotion)
        intArrayOf(  0,   0,   0,   0,   0,   0,   0,   0)  // rank 8 (promoted)
    )

    /**
     * Knight piece-square table.
     *
     * Strategic goals encoded:
     *   - Central knights (d4, e4, d5, e5) get the highest bonuses
     *   - Edge and corner knights are heavily penalised — they control
     *     fewer squares and are easily pushed around
     *   - The gradient encourages development toward the centre
     */
    private val KNIGHT_PST = arrayOf(
        intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50), // rank 1
        intArrayOf(-40, -20,   0,   0,   0,   0, -20, -40), // rank 2
        intArrayOf(-30,   0,  10,  15,  15,  10,   0, -30), // rank 3
        intArrayOf(-30,   5,  15,  20,  20,  15,   5, -30), // rank 4 (centre)
        intArrayOf(-30,   0,  15,  20,  20,  15,   0, -30), // rank 5 (centre)
        intArrayOf(-30,   5,  10,  15,  15,  10,   5, -30), // rank 6
        intArrayOf(-40, -20,   0,   5,   5,   0, -20, -40), // rank 7
        intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50)  // rank 8
    )

    /**
     * Bishop piece-square table.
     *
     * Strategic goals encoded:
     *   - Central and semi-central squares get bonuses for maximum diagonal reach
     *   - Edge squares are penalised — a bishop on the rim is dim
     *   - Fianchetto squares (b2/g2 for White) get a small bonus
     */
    private val BISHOP_PST = arrayOf(
        intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20), // rank 1
        intArrayOf(-10,   0,   0,   0,   0,   0,   0, -10), // rank 2
        intArrayOf(-10,   0,  10,  10,  10,  10,   0, -10), // rank 3
        intArrayOf(-10,   5,   5,  10,  10,   5,   5, -10), // rank 4
        intArrayOf(-10,   0,   5,  10,  10,   5,   0, -10), // rank 5
        intArrayOf(-10,  10,  10,  10,  10,  10,  10, -10), // rank 6
        intArrayOf(-10,   5,   0,   0,   0,   0,   5, -10), // rank 7
        intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20)  // rank 8
    )

    /**
     * Rook piece-square table.
     *
     * Strategic goals encoded:
     *   - Seventh rank (rank 7 for White) gets a large bonus — rooks
     *     on the seventh rank are extremely powerful
     *   - Central files (d and e) get small bonuses
     *   - Rooks are generally position-agnostic; the table is flat
     */
    private val ROOK_PST = arrayOf(
        intArrayOf(  0,   0,   0,   5,   5,   0,   0,   0), // rank 1
        intArrayOf( -5,   0,   0,   0,   0,   0,   0,  -5), // rank 2
        intArrayOf( -5,   0,   0,   0,   0,   0,   0,  -5), // rank 3
        intArrayOf( -5,   0,   0,   0,   0,   0,   0,  -5), // rank 4
        intArrayOf( -5,   0,   0,   0,   0,   0,   0,  -5), // rank 5
        intArrayOf( -5,   0,   0,   0,   0,   0,   0,  -5), // rank 6
        intArrayOf(  5,  10,  10,  10,  10,  10,  10,   5), // rank 7 (7th rank)
        intArrayOf(  0,   0,   0,   0,   0,   0,   0,   0)  // rank 8
    )

    /**
     * Queen piece-square table.
     *
     * Strategic goals encoded:
     *   - Central squares get moderate bonuses
     *   - Corners and edges are penalised
     *   - The queen should stay active but not over-advanced early on
     */
    private val QUEEN_PST = arrayOf(
        intArrayOf(-20, -10, -10,  -5,  -5, -10, -10, -20), // rank 1
        intArrayOf(-10,   0,   0,   0,   0,   0,   0, -10), // rank 2
        intArrayOf(-10,   0,   5,   5,   5,   5,   0, -10), // rank 3
        intArrayOf( -5,   0,   5,   5,   5,   5,   0,  -5), // rank 4
        intArrayOf(  0,   0,   5,   5,   5,   5,   0,  -5), // rank 5
        intArrayOf(-10,   5,   5,   5,   5,   5,   0, -10), // rank 6
        intArrayOf(-10,   0,   5,   0,   0,   0,   0, -10), // rank 7
        intArrayOf(-20, -10, -10,  -5,  -5, -10, -10, -20)  // rank 8
    )

    /**
     * King middlegame piece-square table.
     *
     * Strategic goals encoded:
     *   - The king should stay on the back rank behind pawns (rank 1)
     *   - Castled positions (g1/c1) get bonuses for safety
     *   - Central squares are heavily penalised — the king must not
     *     venture into the centre during the middlegame
     *
     * Note: A separate endgame table (where the king becomes active)
     * could be added later for improved endgame play.
     */
    private val KING_MIDDLEGAME_PST = arrayOf(
        intArrayOf( 20,  30,  10,   0,   0,  10,  30,  20), // rank 1 (castled = safe)
        intArrayOf( 20,  20,   0,   0,   0,   0,  20,  20), // rank 2
        intArrayOf(-10, -20, -20, -20, -20, -20, -20, -10), // rank 3
        intArrayOf(-20, -30, -30, -40, -40, -30, -30, -20), // rank 4
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30), // rank 5
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30), // rank 6
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30), // rank 7
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30)  // rank 8
    )

    // ═══════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    companion object {
        // Material values in centipawns
        private const val PAWN_VALUE = 100
        private const val KNIGHT_VALUE = 320
        private const val BISHOP_VALUE = 330
        private const val ROOK_VALUE = 500
        private const val QUEEN_VALUE = 900
        private const val KING_VALUE = 20000

        // Search bounds
        private const val POSITIVE_INFINITY = 999999
        private const val NEGATIVE_INFINITY = -999999
        private const val MAX_DEPTH = 10

        // Move ordering bonuses
        private const val CAPTURE_BONUS = 10000
        private const val PROMOTION_BONUS = 9000
    }

    /** Shared move generator instance — stateless and thread-safe. */
    private val moveGenerator = MoveGenerator()
}
