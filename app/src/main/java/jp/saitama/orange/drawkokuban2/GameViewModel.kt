package jp.saitama.orange.drawkokuban2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CellState {
    EMPTY,
    WHITE,  // プレイヤー（先攻）
    RED     // CPU（後攻）
}

enum class Player {
    WHITE,
    RED
}

enum class GameMode {
    SINGLE_PLAYER,  // 一人モード（現在の実装）
    VS_CPU         // CPU対戦モード
}

enum class CpuDifficulty {
    EASY,
    NORMAL,
    HARD
}

data class GameState(
    val board: Array<Array<CellState>> = Array(12) { Array(12) { CellState.EMPTY } },
    val currentPlayer: Player = Player.WHITE,
    val winner: Player? = null,
    val isGameOver: Boolean = false,
    val gameMode: GameMode = GameMode.SINGLE_PLAYER,
    val cpuDifficulty: CpuDifficulty = CpuDifficulty.EASY,
    val playerIsWhite: Boolean = true, // プレイヤーが白（先攻）かどうか
    val isWaitingForCpu: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (!board.contentDeepEquals(other.board)) return false
        if (currentPlayer != other.currentPlayer) return false
        if (winner != other.winner) return false
        if (isGameOver != other.isGameOver) return false
        if (gameMode != other.gameMode) return false
        if (cpuDifficulty != other.cpuDifficulty) return false
        if (playerIsWhite != other.playerIsWhite) return false
        if (isWaitingForCpu != other.isWaitingForCpu) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + currentPlayer.hashCode()
        result = 31 * result + (winner?.hashCode() ?: 0)
        result = 31 * result + isGameOver.hashCode()
        result = 31 * result + gameMode.hashCode()
        result = 31 * result + cpuDifficulty.hashCode()
        result = 31 * result + playerIsWhite.hashCode()
        result = 31 * result + isWaitingForCpu.hashCode()
        return result
    }
}

class GameViewModel : ViewModel() {
    var gameState by mutableStateOf(GameState())
        private set

    fun onCellClick(row: Int, col: Int) {
        if (gameState.isGameOver || gameState.board[row][col] != CellState.EMPTY || gameState.isWaitingForCpu) {
            return
        }

        // CPU対戦モード時、CPUターンなら無効
        if (gameState.gameMode == GameMode.VS_CPU) {
            val isPlayerTurn = (gameState.playerIsWhite && gameState.currentPlayer == Player.WHITE) ||
                              (!gameState.playerIsWhite && gameState.currentPlayer == Player.RED)
            if (!isPlayerTurn) {
                return
            }
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

        // CPU対戦モードかつCPUターンになった場合、CPU手番を実行
        if (gameState.gameMode == GameMode.VS_CPU && !gameState.isGameOver) {
            val isCpuTurn = (gameState.playerIsWhite && gameState.currentPlayer == Player.RED) ||
                           (!gameState.playerIsWhite && gameState.currentPlayer == Player.WHITE)

            if (isCpuTurn) {
                gameState = gameState.copy(isWaitingForCpu = true)
                viewModelScope.launch {
                    delay(1000) // CPU思考時間
                    makeCpuMove()
                }
            }
        }
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

    fun startSinglePlayerGame() {
        gameState = GameState(gameMode = GameMode.SINGLE_PLAYER)
    }

    fun startCpuGame() {
        val difficulties = CpuDifficulty.values()
        val randomDifficulty = difficulties.random()
        val playerIsWhite = kotlin.random.Random.nextBoolean()

        gameState = GameState(
            gameMode = GameMode.VS_CPU,
            cpuDifficulty = randomDifficulty,
            playerIsWhite = playerIsWhite,
            currentPlayer = Player.WHITE
        )

        // CPUが先攻（白）の場合、すぐにCPUの手を実行
        if (!playerIsWhite) {
            gameState = gameState.copy(isWaitingForCpu = true)
            viewModelScope.launch {
                delay(1000) // CPU思考時間
                makeCpuMove()
            }
        }
    }

    private fun makeCpuMove() {
        val validMoves = getValidMoves()
        if (validMoves.isEmpty()) {
            gameState = gameState.copy(isWaitingForCpu = false)
            return
        }

        val move = when (gameState.cpuDifficulty) {
            CpuDifficulty.EASY -> getRandomMove(validMoves)
            CpuDifficulty.NORMAL -> getNormalMove(validMoves)
            CpuDifficulty.HARD -> getHardMove(validMoves)
        }

        move?.let { (row, col) ->
            // CPUの手を実行
            val newBoard = gameState.board.map { it.clone() }.toTypedArray()
            val cpuCellState = if (gameState.playerIsWhite) CellState.RED else CellState.WHITE
            newBoard[row][col] = cpuCellState

            val winner = checkWinner(newBoard, row, col, cpuCellState)

            gameState = gameState.copy(
                board = newBoard,
                currentPlayer = if (gameState.currentPlayer == Player.WHITE) Player.RED else Player.WHITE,
                winner = winner,
                isGameOver = winner != null,
                isWaitingForCpu = false
            )
        }
    }

    private fun getValidMoves(): List<Pair<Int, Int>> {
        val validMoves = mutableListOf<Pair<Int, Int>>()
        for (row in 0..11) {
            for (col in 0..11) {
                if (gameState.board[row][col] == CellState.EMPTY && isValidPlacement(row, col)) {
                    validMoves.add(Pair(row, col))
                }
            }
        }
        return validMoves
    }

    private fun getRandomMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
        return validMoves.randomOrNull()
    }

    private fun getNormalMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
        // プレイヤーの勝ちを阻止する手があるかチェック
        val playerCellState = if (gameState.playerIsWhite) CellState.WHITE else CellState.RED

        for ((row, col) in validMoves) {
            val testBoard = gameState.board.map { it.clone() }.toTypedArray()
            testBoard[row][col] = playerCellState

            if (checkWinner(testBoard, row, col, playerCellState) != null) {
                return Pair(row, col) // プレイヤーの勝ちを阻止
            }
        }

        // 阻止する手がなければランダム
        return getRandomMove(validMoves)
    }

    private fun getHardMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
        val cpuCellState = if (gameState.playerIsWhite) CellState.RED else CellState.WHITE
        val playerCellState = if (gameState.playerIsWhite) CellState.WHITE else CellState.RED

        // 1. 自分が勝てる手があるかチェック
        for ((row, col) in validMoves) {
            val testBoard = gameState.board.map { it.clone() }.toTypedArray()
            testBoard[row][col] = cpuCellState

            if (checkWinner(testBoard, row, col, cpuCellState) != null) {
                return Pair(row, col) // 勝ち手
            }
        }

        // 2. プレイヤーの勝ちを阻止する手があるかチェック
        for ((row, col) in validMoves) {
            val testBoard = gameState.board.map { it.clone() }.toTypedArray()
            testBoard[row][col] = playerCellState

            if (checkWinner(testBoard, row, col, playerCellState) != null) {
                return Pair(row, col) // プレイヤーの勝ちを阻止
            }
        }

        // 3. 勝ちも阻止もなければランダム
        return getRandomMove(validMoves)
    }

    fun resetGame() {
        gameState = GameState()
    }
}