package jp.saitama.orange.drawkokuban2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class CellState {
    EMPTY,
    WHITE,  // プレイヤー（先攻）
    RED     // CPU（後攻）
}

enum class Player {
    WHITE,
    RED
}

data class GameState(
    val board: Array<Array<CellState>> = Array(12) { Array(12) { CellState.EMPTY } },
    val currentPlayer: Player = Player.WHITE,
    val winner: Player? = null,
    val isGameOver: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (!board.contentDeepEquals(other.board)) return false
        if (currentPlayer != other.currentPlayer) return false
        if (winner != other.winner) return false
        if (isGameOver != other.isGameOver) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + currentPlayer.hashCode()
        result = 31 * result + (winner?.hashCode() ?: 0)
        result = 31 * result + isGameOver.hashCode()
        return result
    }
}

class GameViewModel : ViewModel() {
    var gameState by mutableStateOf(GameState())
        private set

    fun onCellClick(row: Int, col: Int) {
        if (gameState.isGameOver || gameState.board[row][col] != CellState.EMPTY) {
            return
        }

        // 配置ルールをチェック
        if (!isValidPlacement(row, col)) {
            return
        }

        // セルを現在のプレイヤーの色で塗りつぶす
        val newBoard = gameState.board.map { it.clone() }.toTypedArray()
        val cellState = if (gameState.currentPlayer == Player.WHITE) CellState.WHITE else CellState.RED
        newBoard[row][col] = cellState

        // 勝利判定
        val winner = checkWinner(newBoard, row, col, cellState)

        gameState = gameState.copy(
            board = newBoard,
            currentPlayer = if (gameState.currentPlayer == Player.WHITE) Player.RED else Player.WHITE,
            winner = winner,
            isGameOver = winner != null
        )
    }

    private fun isValidPlacement(row: Int, col: Int): Boolean {
        // 物理法則：最下段または既存ブロックの上でなければならない
        if (row != 11 && gameState.board[row + 1][col] == CellState.EMPTY) {
            return false
        }

        // 初手（盤面が空）なら最下段のどこでも可
        val isEmpty = gameState.board.all { row -> row.all { it == CellState.EMPTY } }
        if (isEmpty) {
            return row == 11  // 最下段のみ
        }

        // 隣接ルール：8方向のいずれかに既存ブロックが必要
        val directions = listOf(
            Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
            Pair(0, -1),               Pair(0, 1),
            Pair(1, -1),  Pair(1, 0),  Pair(1, 1)
        )

        for ((dRow, dCol) in directions) {
            val newRow = row + dRow
            val newCol = col + dCol
            if (newRow in 0..11 && newCol in 0..11 && gameState.board[newRow][newCol] != CellState.EMPTY) {
                return true
            }
        }

        return false
    }

    private fun checkWinner(board: Array<Array<CellState>>, row: Int, col: Int, cellState: CellState): Player? {
        val directions = listOf(
            Pair(0, 1),   // 横
            Pair(1, 0),   // 縦
            Pair(1, 1),   // 右下斜め
            Pair(1, -1)   // 左下斜め
        )

        for ((dRow, dCol) in directions) {
            var count = 1 // 配置したセル自体をカウント

            // 正方向にチェック
            var r = row + dRow
            var c = col + dCol
            while (r in 0..11 && c in 0..11 && board[r][c] == cellState) {
                count++
                r += dRow
                c += dCol
            }

            // 逆方向にチェック
            r = row - dRow
            c = col - dCol
            while (r in 0..11 && c in 0..11 && board[r][c] == cellState) {
                count++
                r -= dRow
                c -= dCol
            }

            if (count >= 4) {
                return if (cellState == CellState.WHITE) Player.WHITE else Player.RED
            }
        }

        return null
    }

    fun resetGame() {
        gameState = GameState()
    }
}