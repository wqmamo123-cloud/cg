package com.example.chess.engine

import com.example.chess.model.ChessBoard
import com.example.chess.model.Move
import com.example.chess.model.PieceColor
import com.example.chess.model.PieceType
import com.example.chess.model.Position

/**
 * Complete, production-ready chess move generator that enforces all FIDE rules.
 *
 * This generator produces only **legal** moves — moves that do not leave the
 * moving player's own king in check. It operates in two phases:
 *
 *   1. **Pseudo-legal generation**: All candidate moves are produced based on
 *      piece movement rules, board boundaries, and friendly-piece occupancy.
 *      Special moves (en passant, castling, promotion) are included here.
 *
 *   2. **Legality filter**: Each pseudo-legal move is applied to a copy of the
 *      board. If the resulting position leaves the moving player's king in check,
 *      the move is discarded.
 *
 * Attack detection ([isSquareAttacked]) is performed by scanning outward from
 * the target square in all directions, which is far more efficient than
 * generating the full move list for the attacking side.
 *
 * Thread safety: This class is stateless and all methods are pure functions.
 * It can be safely called from any coroutine or thread without synchronization.
 */
class MoveGenerator {

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generates all legal moves for the given [color] on the current [board].
     *
     * The returned list includes regular moves, captures, en passant captures,
     * castling moves, and pawn promotions (to Queen, Rook, Bishop, and Knight).
     * Every move in the list is guaranteed to be legal — i.e., it will not
     * leave the moving player's king in check after execution.
     */
    fun generateLegalMoves(board: ChessBoard, color: PieceColor): List<Move> {
        val pseudoLegalMoves = generatePseudoLegalMoves(board, color)
        return pseudoLegalMoves.filter { move ->
            val boardAfterMove = board.applyMove(move)
            !isInCheck(boardAfterMove, color)
        }
    }

    /**
     * Returns true if the king of the given [color] is currently in check.
     *
     * This is determined by locating the king's position and checking whether
     * any opponent piece attacks that square.
     */
    fun isInCheck(board: ChessBoard, color: PieceColor): Boolean {
        val kingPosition = board.findKing(color) ?: return false
        return isSquareAttacked(board, kingPosition, color.opposite())
    }

    /**
     * Returns true if the given [color] is in checkmate.
     *
     * Checkmate occurs when the king is in check AND there are no legal moves
     * available to escape the check. This is a terminal game state — the player
     * whose king is checkmated has lost.
     */
    fun isCheckmate(board: ChessBoard, color: PieceColor): Boolean {
        return isInCheck(board, color) && generateLegalMoves(board, color).isEmpty()
    }

    /**
     * Returns true if the given [color] is in stalemate.
     *
     * Stalemate occurs when the king is NOT in check but there are no legal moves
     * available. The game is drawn in this situation.
     */
    fun isStalemate(board: ChessBoard, color: PieceColor): Boolean {
        return !isInCheck(board, color) && generateLegalMoves(board, color).isEmpty()
    }

    /**
     * Determines whether a given square [position] is attacked by any piece
     * belonging to [byColor].
     *
     * Instead of generating all opponent moves (which is expensive), this method
     * performs targeted scans from the target square outward:
     *   - Pawn attacks: check the two diagonal squares from which a pawn of [byColor] could attack
     *   - Knight attacks: check all 8 L-shaped squares
     *   - Sliding attacks (Bishop/Queen): check all 4 diagonal rays
     *   - Sliding attacks (Rook/Queen): check all 4 straight rays
     *   - King attacks: check all 8 adjacent squares
     *
     * Each scan stops at the first piece encountered. If that piece belongs to [byColor]
     * and is capable of attacking along that direction, the square is attacked.
     * This approach is O(8+8+28+28+8) = O(80) in the worst case, compared to
     * O(n) for generating all opponent moves where n can be 100+.
     */
    fun isSquareAttacked(board: ChessBoard, position: Position, byColor: PieceColor): Boolean {
        return isAttackedByPawn(board, position, byColor) ||
                isAttackedByKnight(board, position, byColor) ||
                isAttackedByDiagonalSlider(board, position, byColor) ||
                isAttackedByStraightSlider(board, position, byColor) ||
                isAttackedByKing(board, position, byColor)
    }

    // ═══════════════════════════════════════════════════════════════
    //  PSEUDO-LEGAL MOVE GENERATION (per piece type)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generates all pseudo-legal moves for [color] on [board].
     * Pseudo-legal moves follow piece movement rules but may leave the king in check.
     * The legality filter in [generateLegalMoves] will remove such moves.
     */
    private fun generatePseudoLegalMoves(board: ChessBoard, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        val allPieces = board.getAllPiecesOfColor(color)

        for ((position, piece) in allPieces) {
            when (piece.type) {
                PieceType.PAWN   -> generatePawnMoves(board, position, color, moves)
                PieceType.KNIGHT -> generateKnightMoves(board, position, color, moves)
                PieceType.BISHOP -> generateBishopMoves(board, position, color, moves)
                PieceType.ROOK   -> generateRookMoves(board, position, color, moves)
                PieceType.QUEEN  -> generateQueenMoves(board, position, color, moves)
                PieceType.KING   -> generateKingMoves(board, position, color, moves)
            }
        }
        return moves
    }

    // ── Pawn ──────────────────────────────────────────────────────

    /**
     * Generates all pseudo-legal pawn moves from [from] for the given [color].
     *
     * Pawn moves include:
     *   - Single square forward (if the destination is empty)
     *   - Double square forward from the starting rank (if both squares are empty)
     *   - Diagonal capture (if an opponent piece occupies the target square)
     *   - En passant capture (if the en passant target square matches)
     *   - Promotion (if the pawn reaches the last rank, 4 moves are generated
     *     for Queen, Rook, Bishop, and Knight promotions)
     */
    private fun generatePawnMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        moves: MutableList<Move>
    ) {
        val direction = if (color == PieceColor.WHITE) 1 else -1
        val startRow = if (color == PieceColor.WHITE) 1 else 6
        val promotionRow = if (color == PieceColor.WHITE) 7 else 0

        // ── Single square forward ──
        val oneStepForward = Position(from.row + direction, from.col)
        if (oneStepForward.isValid() && board.getPiece(oneStepForward) == null) {
            if (oneStepForward.row == promotionRow) {
                addPromotionMoves(from, oneStepForward, moves)
            } else {
                moves.add(Move(from, oneStepForward))

                // ── Double square forward (only from starting row) ──
                if (from.row == startRow) {
                    val twoStepsForward = Position(from.row + 2 * direction, from.col)
                    if (twoStepsForward.isValid() && board.getPiece(twoStepsForward) == null) {
                        moves.add(Move(from, twoStepsForward))
                    }
                }
            }
        }

        // ── Diagonal captures and en passant ──
        for (deltaCol in intArrayOf(-1, 1)) {
            val captureTarget = Position(from.row + direction, from.col + deltaCol)
            if (!captureTarget.isValid()) continue

            val targetPiece = board.getPiece(captureTarget)

            // Regular capture
            if (targetPiece != null && targetPiece.color != color) {
                if (captureTarget.row == promotionRow) {
                    addPromotionMoves(from, captureTarget, moves)
                } else {
                    moves.add(Move(from, captureTarget))
                }
            }

            // En passant capture
            if (captureTarget == board.enPassantTarget) {
                moves.add(Move(from, captureTarget, isEnPassant = true))
            }
        }
    }

    /**
     * Adds four promotion moves (Queen, Rook, Bishop, Knight) for a pawn
     * advancing from [from] to [to].
     */
    private fun addPromotionMoves(from: Position, to: Position, moves: MutableList<Move>) {
        for (promoType in PROMOTION_PIECE_TYPES) {
            moves.add(Move(from, to, promotionType = promoType))
        }
    }

    // ── Knight ────────────────────────────────────────────────────

    /**
     * Generates all pseudo-legal knight moves from [from] for the given [color].
     * Knights move in an L-shape (2+1 squares) and can jump over other pieces.
     * A knight can land on an empty square or capture an opponent piece.
     */
    private fun generateKnightMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        moves: MutableList<Move>
    ) {
        for ((dr, dc) in KNIGHT_OFFSETS) {
            val to = Position(from.row + dr, from.col + dc)
            if (!to.isValid()) continue
            val targetPiece = board.getPiece(to)
            if (targetPiece == null || targetPiece.color != color) {
                moves.add(Move(from, to))
            }
        }
    }

    // ── Bishop ────────────────────────────────────────────────────

    /**
     * Generates all pseudo-legal bishop moves from [from] for the given [color].
     * Bishops slide diagonally any number of squares until they hit the board edge,
     * a friendly piece (blocked), or an opponent piece (capture, then stop).
     */
    private fun generateBishopMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        moves: MutableList<Move>
    ) {
        generateSlidingMoves(board, from, color, DIAGONAL_DIRECTIONS, moves)
    }

    // ── Rook ──────────────────────────────────────────────────────

    /**
     * Generates all pseudo-legal rook moves from [from] for the given [color].
     * Rooks slide horizontally and vertically any number of squares under the
     * same blocking rules as bishops.
     */
    private fun generateRookMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        moves: MutableList<Move>
    ) {
        generateSlidingMoves(board, from, color, STRAIGHT_DIRECTIONS, moves)
    }

    // ── Queen ─────────────────────────────────────────────────────

    /**
     * Generates all pseudo-legal queen moves from [from] for the given [color].
     * The queen combines the movement capabilities of the bishop and the rook,
     * sliding in all 8 directions.
     */
    private fun generateQueenMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        moves: MutableList<Move>
    ) {
        generateSlidingMoves(board, from, color, ALL_DIRECTIONS, moves)
    }

    // ── Sliding move helper (Bishop, Rook, Queen) ────────────────

    /**
     * Generates sliding moves along the given [directions] from position [from].
     *
     * For each direction, the algorithm steps one square at a time:
     *   - Empty square → add move and continue sliding
     *   - Opponent piece → add capture move and stop (can't go further)
     *   - Friendly piece → stop (blocked, can't capture own piece)
     *
     * This helper is shared by Bishop (4 diagonal directions), Rook (4 straight
     * directions), and Queen (all 8 directions).
     */
    private fun generateSlidingMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        directions: List<Pair<Int, Int>>,
        moves: MutableList<Move>
    ) {
        for ((dr, dc) in directions) {
            var currentRow = from.row + dr
            var currentCol = from.col + dc

            while (true) {
                val to = Position(currentRow, currentCol)
                if (!to.isValid()) break

                val targetPiece = board.getPiece(to)
                if (targetPiece == null) {
                    // Empty square — add move and keep sliding
                    moves.add(Move(from, to))
                } else {
                    // Occupied square — capture if opponent, then stop either way
                    if (targetPiece.color != color) {
                        moves.add(Move(from, to))
                    }
                    break
                }
                currentRow += dr
                currentCol += dc
            }
        }
    }

    // ── King ──────────────────────────────────────────────────────

    /**
     * Generates all pseudo-legal king moves from [from] for the given [color].
     *
     * King moves include:
     *   - Single square in any of the 8 directions (if not occupied by a friendly piece)
     *   - Kingside castling (king moves from e-file to g-file)
     *   - Queenside castling (king moves from e-file to c-file)
     *
     * Castling is only generated here if the king and rook have not moved,
     * the squares between them are empty, and the king does not pass through
     * or land on an attacked square. The "not in check" prerequisite is also
     * verified — castling out of check is illegal.
     */
    private fun generateKingMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        moves: MutableList<Move>
    ) {
        // ── Normal king moves (1 square in any direction) ──
        for ((dr, dc) in ALL_DIRECTIONS) {
            val to = Position(from.row + dr, from.col + dc)
            if (!to.isValid()) continue
            val targetPiece = board.getPiece(to)
            if (targetPiece == null || targetPiece.color != color) {
                moves.add(Move(from, to))
            }
        }

        // ── Castling ──
        generateCastlingMoves(board, from, color, moves)
    }

    /**
     * Generates castling moves for the king at [from] of the given [color].
     *
     * FIDE castling rules enforced:
     *   1. The king has not moved from its original square (e1 for White, e8 for Black)
     *   2. The relevant rook has not moved from its original square
     *   3. No pieces stand between the king and the rook
     *   4. The king is not currently in check
     *   5. The king does not pass through a square attacked by the opponent
     *   6. The king does not land on a square attacked by the opponent
     *
     * Note: The "passing through check" rule applies only to the squares the
     * king actually traverses. For queenside castling, the b1/b8 square does
     * not need to be unattacked (only empty), because the king does not pass
     * through it — only the rook does.
     */
    private fun generateCastlingMoves(
        board: ChessBoard,
        from: Position,
        color: PieceColor,
        moves: MutableList<Move>
    ) {
        val backRankRow = if (color == PieceColor.WHITE) 0 else 7
        val opponentColor = color.opposite()

        // King must be on its original square and must not have moved
        val king = board.getPiece(Position(backRankRow, 4))
        if (king == null || king.type != PieceType.KING || king.hasMoved) return
        if (from.row != backRankRow || from.col != 4) return

        // Cannot castle out of check
        if (isInCheck(board, color)) return

        // ── Kingside castling (O-O) ──
        val kingsideRook = board.getPiece(Position(backRankRow, 7))
        if (kingsideRook != null &&
            kingsideRook.type == PieceType.ROOK &&
            !kingsideRook.hasMoved
        ) {
            val fSquare = Position(backRankRow, 5)
            val gSquare = Position(backRankRow, 6)

            // Squares between king and rook must be empty
            if (board.getPiece(fSquare) == null && board.getPiece(gSquare) == null) {
                // King must not pass through or land on an attacked square
                val fAttacked = isSquareAttacked(board, fSquare, opponentColor)
                val gAttacked = isSquareAttacked(board, gSquare, opponentColor)
                if (!fAttacked && !gAttacked) {
                    moves.add(Move(from, gSquare, isCastling = true))
                }
            }
        }

        // ── Queenside castling (O-O-O) ──
        val queensideRook = board.getPiece(Position(backRankRow, 0))
        if (queensideRook != null &&
            queensideRook.type == PieceType.ROOK &&
            !queensideRook.hasMoved
        ) {
            val bSquare = Position(backRankRow, 1)
            val cSquare = Position(backRankRow, 2)
            val dSquare = Position(backRankRow, 3)

            // Three squares between king and rook must be empty
            if (board.getPiece(bSquare) == null &&
                board.getPiece(cSquare) == null &&
                board.getPiece(dSquare) == null
            ) {
                // King passes through d-file and lands on c-file
                // b-file does NOT need to be unattacked (king doesn't pass through it)
                val dAttacked = isSquareAttacked(board, dSquare, opponentColor)
                val cAttacked = isSquareAttacked(board, cSquare, opponentColor)
                if (!dAttacked && !cAttacked) {
                    moves.add(Move(from, cSquare, isCastling = true))
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ATTACK DETECTION HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a pawn of [byColor] attacks the given [position].
     *
     * Pawn attacks are unique because they are diagonal-only and direction-dependent.
     * A white pawn on (r, c) attacks (r+1, c-1) and (r+1, c+1).
     * A black pawn on (r, c) attacks (r-1, c-1) and (r-1, c+1).
     *
     * To check if [position] is attacked by a [byColor] pawn, we look in the
     * opposite direction: if byColor is WHITE, a white pawn attacking [position]
     * would be one row below it (position.row - 1).
     */
    private fun isAttackedByPawn(
        board: ChessBoard,
        position: Position,
        byColor: PieceColor
    ): Boolean {
        // Direction to look for the attacking pawn (opposite of the pawn's forward direction)
        val pawnLookDirection = if (byColor == PieceColor.WHITE) -1 else 1

        for (deltaCol in intArrayOf(-1, 1)) {
            val attackerPos = Position(position.row + pawnLookDirection, position.col + deltaCol)
            if (!attackerPos.isValid()) continue
            val piece = board.getPiece(attackerPos)
            if (piece != null && piece.type == PieceType.PAWN && piece.color == byColor) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a knight of [byColor] attacks the given [position].
     *
     * Knights attack from L-shaped offsets. We check all 8 possible knight
     * positions that could reach [position].
     */
    private fun isAttackedByKnight(
        board: ChessBoard,
        position: Position,
        byColor: PieceColor
    ): Boolean {
        for ((dr, dc) in KNIGHT_OFFSETS) {
            val attackerPos = Position(position.row + dr, position.col + dc)
            if (!attackerPos.isValid()) continue
            val piece = board.getPiece(attackerPos)
            if (piece != null && piece.type == PieceType.KNIGHT && piece.color == byColor) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a bishop or queen of [byColor] attacks the given [position]
     * along a diagonal ray.
     *
     * The algorithm scans outward in all 4 diagonal directions from [position].
     * The first piece encountered on each ray is examined:
     *   - If it is a [byColor] bishop or queen → the square is attacked
     *   - If it is any other piece → the ray is blocked, stop checking this direction
     */
    private fun isAttackedByDiagonalSlider(
        board: ChessBoard,
        position: Position,
        byColor: PieceColor
    ): Boolean {
        for ((dr, dc) in DIAGONAL_DIRECTIONS) {
            var currentRow = position.row + dr
            var currentCol = position.col + dc

            while (true) {
                val rayPos = Position(currentRow, currentCol)
                if (!rayPos.isValid()) break

                val piece = board.getPiece(rayPos)
                if (piece != null) {
                    if (piece.color == byColor &&
                        (piece.type == PieceType.BISHOP || piece.type == PieceType.QUEEN)
                    ) {
                        return true
                    }
                    // This piece blocks the ray — stop checking this direction
                    break
                }
                currentRow += dr
                currentCol += dc
            }
        }
        return false
    }

    /**
     * Checks if a rook or queen of [byColor] attacks the given [position]
     * along a straight (horizontal/vertical) ray.
     *
     * Same ray-casting logic as [isAttackedByDiagonalSlider], but scanning
     * along the 4 straight directions and looking for rooks or queens.
     */
    private fun isAttackedByStraightSlider(
        board: ChessBoard,
        position: Position,
        byColor: PieceColor
    ): Boolean {
        for ((dr, dc) in STRAIGHT_DIRECTIONS) {
            var currentRow = position.row + dr
            var currentCol = position.col + dc

            while (true) {
                val rayPos = Position(currentRow, currentCol)
                if (!rayPos.isValid()) break

                val piece = board.getPiece(rayPos)
                if (piece != null) {
                    if (piece.color == byColor &&
                        (piece.type == PieceType.ROOK || piece.type == PieceType.QUEEN)
                    ) {
                        return true
                    }
                    break
                }
                currentRow += dr
                currentCol += dc
            }
        }
        return false
    }

    /**
     * Checks if a king of [byColor] attacks the given [position].
     *
     * A king attacks all 8 adjacent squares. This check prevents two kings
     * from ever occupying adjacent squares.
     */
    private fun isAttackedByKing(
        board: ChessBoard,
        position: Position,
        byColor: PieceColor
    ): Boolean {
        for ((dr, dc) in ALL_DIRECTIONS) {
            val attackerPos = Position(position.row + dr, position.col + dc)
            if (!attackerPos.isValid()) continue
            val piece = board.getPiece(attackerPos)
            if (piece != null && piece.type == PieceType.KING && piece.color == byColor) {
                return true
            }
        }
        return false
    }

    // ═══════════════════════════════════════════════════════════════
    //  GAME STATE EVALUATION HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Determines the current game state for the side to move.
     *
     * Returns one of:
     *   - [GameState.CHECKMATE]       — the current player's king is checkmated
     *   - [GameState.STALEMATE]       — the current player has no legal moves but is not in check
     *   - [GameState.CHECK]           — the current player's king is in check
     *   - [GameState.IN_PROGRESS]     — the game is ongoing normally
     *
     * This is a convenience method that combines [isCheckmate], [isStalemate],
     * and [isInCheck] into a single call, avoiding redundant move generation
     * when possible.
     */
    fun evaluateGameState(board: ChessBoard, currentColor: PieceColor): GameState {
        val inCheck = isInCheck(board, currentColor)
        val hasLegalMoves = hasAnyLegalMove(board, currentColor)

        return when {
            inCheck && !hasLegalMoves -> GameState.CHECKMATE
            !inCheck && !hasLegalMoves -> GameState.STALEMATE
            inCheck -> GameState.CHECK
            else -> GameState.IN_PROGRESS
        }
    }

    /**
     * Returns true if the given [color] has at least one legal move available.
     *
     * This is more efficient than generating the full list of legal moves when
     * you only need to know whether any exist (e.g., for checkmate/stalemate
     * detection). It stops checking as soon as the first legal move is found.
     */
    fun hasAnyLegalMove(board: ChessBoard, color: PieceColor): Boolean {
        val allPieces = board.getAllPiecesOfColor(color)

        for ((position, piece) in allPieces) {
            val pseudoLegalForPiece = when (piece.type) {
                PieceType.PAWN   -> generatePawnMovesList(board, position, color)
                PieceType.KNIGHT -> generateKnightMovesList(board, position, color)
                PieceType.BISHOP -> generateBishopMovesList(board, position, color)
                PieceType.ROOK   -> generateRookMovesList(board, position, color)
                PieceType.QUEEN  -> generateQueenMovesList(board, position, color)
                PieceType.KING   -> generateKingMovesList(board, position, color)
            }

            for (move in pseudoLegalForPiece) {
                val boardAfterMove = board.applyMove(move)
                if (!isInCheck(boardAfterMove, color)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Counts the total number of legal moves available for the given [color].
     * Useful for evaluating position complexity and mobility.
     */
    fun countLegalMoves(board: ChessBoard, color: PieceColor): Int {
        return generateLegalMoves(board, color).size
    }

    /**
     * Checks whether the game is in a "insufficient material" draw state.
     *
     * The following combinations are automatic draws:
     *   - King vs King
     *   - King + Bishop vs King
     *   - King + Knight vs King
     *   - King + Bishop vs King + Bishop (same-color bishops)
     *
     * This does NOT cover all FIDE draw conditions (e.g., 50-move rule,
     * threefold repetition) — those require game history tracking.
     */
    fun isInsufficientMaterial(board: ChessBoard): Boolean {
        val whitePieces = board.getAllPiecesOfColor(PieceColor.WHITE)
        val blackPieces = board.getAllPiecesOfColor(PieceColor.BLACK)

        // Filter out kings for analysis
        val whiteNonKing = whitePieces.filter { it.second.type != PieceType.KING }
        val blackNonKing = blackPieces.filter { it.second.type != PieceType.KING }

        // King vs King
        if (whiteNonKing.isEmpty() && blackNonKing.isEmpty()) return true

        // King + minor piece vs King
        if (whiteNonKing.isEmpty() && blackNonKing.size == 1 &&
            (blackNonKing[0].second.type == PieceType.BISHOP ||
                    blackNonKing[0].second.type == PieceType.KNIGHT)
        ) return true
        if (blackNonKing.isEmpty() && whiteNonKing.size == 1 &&
            (whiteNonKing[0].second.type == PieceType.BISHOP ||
                    whiteNonKing[0].second.type == PieceType.KNIGHT)
        ) return true

        // King + Bishop vs King + Bishop (same color square)
        if (whiteNonKing.size == 1 && blackNonKing.size == 1 &&
            whiteNonKing[0].second.type == PieceType.BISHOP &&
            blackNonKing[0].second.type == PieceType.BISHOP
        ) {
            val whiteBishopSquareColor = (whiteNonKing[0].first.row + whiteNonKing[0].first.col) % 2
            val blackBishopSquareColor = (blackNonKing[0].first.row + blackNonKing[0].first.col) % 2
            if (whiteBishopSquareColor == blackBishopSquareColor) return true
        }

        return false
    }

    // ═══════════════════════════════════════════════════════════════
    //  PER-PIECE MOVE LIST GENERATORS (for hasAnyLegalMove optimization)
    // ═══════════════════════════════════════════════════════════════

    private fun generatePawnMovesList(
        board: ChessBoard,
        from: Position,
        color: PieceColor
    ): List<Move> {
        val moves = mutableListOf<Move>()
        generatePawnMoves(board, from, color, moves)
        return moves
    }

    private fun generateKnightMovesList(
        board: ChessBoard,
        from: Position,
        color: PieceColor
    ): List<Move> {
        val moves = mutableListOf<Move>()
        generateKnightMoves(board, from, color, moves)
        return moves
    }

    private fun generateBishopMovesList(
        board: ChessBoard,
        from: Position,
        color: PieceColor
    ): List<Move> {
        val moves = mutableListOf<Move>()
        generateBishopMoves(board, from, color, moves)
        return moves
    }

    private fun generateRookMovesList(
        board: ChessBoard,
        from: Position,
        color: PieceColor
    ): List<Move> {
        val moves = mutableListOf<Move>()
        generateRookMoves(board, from, color, moves)
        return moves
    }

    private fun generateQueenMovesList(
        board: ChessBoard,
        from: Position,
        color: PieceColor
    ): List<Move> {
        val moves = mutableListOf<Move>()
        generateQueenMoves(board, from, color, moves)
        return moves
    }

    private fun generateKingMovesList(
        board: ChessBoard,
        from: Position,
        color: PieceColor
    ): List<Move> {
        val moves = mutableListOf<Move>()
        generateKingMoves(board, from, color, moves)
        return moves
    }

    // ═══════════════════════════════════════════════════════════════
    //  COMPANION OBJECT — Constants
    // ═══════════════════════════════════════════════════════════════

    companion object {
        /** The four piece types a pawn can promote to, in order of typical preference. */
        private val PROMOTION_PIECE_TYPES = listOf(
            PieceType.QUEEN,
            PieceType.ROOK,
            PieceType.BISHOP,
            PieceType.KNIGHT
        )

        /** All 8 knight L-shaped move offsets as (rowDelta, colDelta) pairs. */
        private val KNIGHT_OFFSETS = listOf(
            Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
            Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
        )

        /** Four diagonal directions as (rowDelta, colDelta) pairs. Used by Bishop and Queen. */
        private val DIAGONAL_DIRECTIONS = listOf(
            Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)
        )

        /** Four straight directions as (rowDelta, colDelta) pairs. Used by Rook and Queen. */
        private val STRAIGHT_DIRECTIONS = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
        )

        /** All 8 directions combined. Used by Queen and King. */
        private val ALL_DIRECTIONS = DIAGONAL_DIRECTIONS + STRAIGHT_DIRECTIONS
    }
}

/**
 * Represents the state of a chess game from the perspective of the side to move.
 * Used by the engine and ViewModel to communicate game outcomes to the UI.
 */
enum class GameState {
    /** The game is ongoing — the current player has legal moves. */
    IN_PROGRESS,

    /** The current player's king is in check, but escape moves exist. */
    CHECK,

    /** The current player's king is checkmated — the game is over. */
    CHECKMATE,

    /** The current player has no legal moves but is not in check — the game is drawn. */
    STALEMATE
}


