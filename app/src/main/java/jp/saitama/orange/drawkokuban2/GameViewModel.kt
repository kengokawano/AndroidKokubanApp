package jp.saitama.orange.drawkokuban2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 盤面サイズ定数
const val BOARD_WIDTH = 11   // 横
const val BOARD_HEIGHT = 14  // 縦

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
    HARD,
    EXPERT  // 2手先読み
}

data class GameState(
    val board: Array<Array<CellState>> = Array(BOARD_HEIGHT) { Array(BOARD_WIDTH) { CellState.EMPTY } },
    val currentPlayer: Player = Player.WHITE,
    val winner: Player? = null,
    val isGameOver: Boolean = false,
    val gameMode: GameMode = GameMode.SINGLE_PLAYER,
    val cpuDifficulty: CpuDifficulty = CpuDifficulty.EASY,
    val playerIsWhite: Boolean = true, // プレイヤーが白（先攻）かどうか
    val isWaitingForCpu: Boolean = false,
    val playerWinStreak: Int = 0, // プレイヤーの連勝数（CPU対戦時のみ）
    val isDraw: Boolean = false, // 引き分けフラグ
    val winningLine: List<Pair<Int, Int>> = emptyList(), // 勝利ライン座標
    val validMoves: List<Pair<Int, Int>> = emptyList(), // 配置可能位置
    val lastPlacedBlock: Pair<Int, Int>? = null // アニメーション用の最後に置かれたブロック
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
        if (playerWinStreak != other.playerWinStreak) return false
        if (isDraw != other.isDraw) return false
        if (winningLine != other.winningLine) return false
        if (validMoves != other.validMoves) return false

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
        result = 31 * result + playerWinStreak.hashCode()
        result = 31 * result + isDraw.hashCode()
        result = 31 * result + winningLine.hashCode()
        result = 31 * result + validMoves.hashCode()
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

        // validMovesリストにない場合は配置不可
        if (!gameState.validMoves.contains(Pair(row, col))) {
            return
        }

        // セルを現在のプレイヤーの色で塗りつぶす
        val newBoard = gameState.board.map { it.clone() }.toTypedArray()
        val cellState = if (gameState.currentPlayer == Player.WHITE) CellState.WHITE else CellState.RED
        newBoard[row][col] = cellState

        // 勝利判定と勝利ライン取得
        val (winner, winningLine) = checkWinnerWithLine(newBoard, row, col, cellState)

        // 引き分け判定（全マス埋まりかつ勝者なし）
        val isDraw = winner == null && newBoard.all { row -> row.all { it != CellState.EMPTY } }

        // 連勝数の更新（CPU対戦時のみ）
        var newWinStreak = gameState.playerWinStreak
        if (gameState.gameMode == GameMode.VS_CPU && winner != null) {
            val isPlayerWin = (gameState.playerIsWhite && winner == Player.WHITE) ||
                             (!gameState.playerIsWhite && winner == Player.RED)
            if (isPlayerWin) {
                newWinStreak = gameState.playerWinStreak + 1
            } else {
                newWinStreak = 0 // CPUに負けたら連勝リセット
            }
        }

        // 有効な手を更新
        val newValidMoves = if (!isDraw && winner == null) {
            getValidMovesForBoard(newBoard)
        } else {
            emptyList()
        }

        gameState = gameState.copy(
            board = newBoard,
            currentPlayer = if (gameState.currentPlayer == Player.WHITE) Player.RED else Player.WHITE,
            winner = winner,
            isGameOver = winner != null || isDraw,
            playerWinStreak = newWinStreak,
            isDraw = isDraw,
            winningLine = winningLine,
            validMoves = newValidMoves,
            lastPlacedBlock = Pair(row, col) // 最後に置かれたブロックを記録
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

    fun onAnimationCompleted() {
        gameState = gameState.copy(lastPlacedBlock = null)
    }

    private fun isValidPlacement(row: Int, col: Int): Boolean {
        // 物理法則：最下段または既存ブロックの上でなければならない
        if (row != BOARD_HEIGHT - 1 && gameState.board[row + 1][col] == CellState.EMPTY) {
            return false
        }

        // 初手（盤面が空）なら最下段のどこでも可
        val isEmpty = gameState.board.all { row -> row.all { it == CellState.EMPTY } }
        if (isEmpty) {
            return row == BOARD_HEIGHT - 1  // 最下段のみ
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
            if (newRow in 0 until BOARD_HEIGHT && newCol in 0 until BOARD_WIDTH && gameState.board[newRow][newCol] != CellState.EMPTY) {
                return true
            }
        }

        return false
    }

    private fun checkWinner(board: Array<Array<CellState>>, row: Int, col: Int, cellState: CellState): Player? {
        return checkWinnerWithLine(board, row, col, cellState).first
    }

    private fun checkWinnerWithLine(board: Array<Array<CellState>>, row: Int, col: Int, cellState: CellState): Pair<Player?, List<Pair<Int, Int>>> {
        val directions = listOf(
            Pair(0, 1),   // 横
            Pair(1, 0),   // 縦
            Pair(1, 1),   // 右下斜め
            Pair(1, -1)   // 左下斜め
        )

        for ((dRow, dCol) in directions) {
            val lineCoords = mutableListOf<Pair<Int, Int>>()
            lineCoords.add(Pair(row, col)) // 配置したセル

            // 正方向にチェック
            var r = row + dRow
            var c = col + dCol
            while (r in 0 until BOARD_HEIGHT && c in 0 until BOARD_WIDTH && board[r][c] == cellState) {
                lineCoords.add(Pair(r, c))
                r += dRow
                c += dCol
            }

            // 逆方向にチェック
            r = row - dRow
            c = col - dCol
            while (r in 0 until BOARD_HEIGHT && c in 0 until BOARD_WIDTH && board[r][c] == cellState) {
                lineCoords.add(0, Pair(r, c)) // 先頭に追加
                r -= dRow
                c -= dCol
            }

            if (lineCoords.size >= 4) {
                val winner = if (cellState == CellState.WHITE) Player.WHITE else Player.RED
                return Pair(winner, lineCoords)
            }
        }

        return Pair(null, emptyList())
    }

    fun startSinglePlayerGame() {
        val newState = GameState(gameMode = GameMode.SINGLE_PLAYER)
        val validMoves = getValidMovesForBoard(newState.board)
        gameState = newState.copy(validMoves = validMoves)
    }

    fun startCpuGame() {
        val difficulties = CpuDifficulty.values()
        val randomDifficulty = difficulties.random()
        val playerIsWhite = kotlin.random.Random.nextBoolean()

        // 連勝数を保持（新規ゲーム開始時は0、続けるときは維持）
        val currentWinStreak = if (gameState.gameMode == GameMode.VS_CPU) gameState.playerWinStreak else 0

        val newState = GameState(
            gameMode = GameMode.VS_CPU,
            cpuDifficulty = randomDifficulty,
            playerIsWhite = playerIsWhite,
            currentPlayer = Player.WHITE,
            playerWinStreak = currentWinStreak
        )
        val validMoves = getValidMovesForBoard(newState.board)

        // CPUが先攻（白）の場合は待機状態も設定
        val finalState = if (!playerIsWhite) {
            newState.copy(validMoves = validMoves, isWaitingForCpu = true)
        } else {
            newState.copy(validMoves = validMoves)
        }

        gameState = finalState

        // CPUが先攻（白）の場合、すぐにCPUの手を実行
        if (!playerIsWhite) {
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
            CpuDifficulty.EXPERT -> getExpertMove(validMoves)
        }

        move?.let { (row, col) ->
            // CPUの手を実行
            val newBoard = gameState.board.map { it.clone() }.toTypedArray()
            val cpuCellState = if (gameState.playerIsWhite) CellState.RED else CellState.WHITE
            newBoard[row][col] = cpuCellState

            val (winner, winningLine) = checkWinnerWithLine(newBoard, row, col, cpuCellState)

            // 引き分け判定
            val isDraw = winner == null && newBoard.all { row -> row.all { it != CellState.EMPTY } }

            // 連勝数の更新（CPUの手で勝負が決まった場合）
            var newWinStreak = gameState.playerWinStreak
            if (winner != null) {
                val isPlayerWin = (gameState.playerIsWhite && winner == Player.WHITE) ||
                                 (!gameState.playerIsWhite && winner == Player.RED)
                if (isPlayerWin) {
                    newWinStreak = gameState.playerWinStreak + 1
                } else {
                    newWinStreak = 0 // CPUに負けたら連勝リセット
                }
            }

            // 有効な手を更新
            val newValidMoves = if (!isDraw && winner == null) {
                getValidMovesForBoard(newBoard)
            } else {
                emptyList()
            }

            gameState = gameState.copy(
                board = newBoard,
                currentPlayer = if (gameState.currentPlayer == Player.WHITE) Player.RED else Player.WHITE,
                winner = winner,
                isGameOver = winner != null || isDraw,
                isWaitingForCpu = false,
                playerWinStreak = newWinStreak,
                isDraw = isDraw,
                winningLine = winningLine,
                validMoves = newValidMoves,
                lastPlacedBlock = Pair(row, col) // 最後に置かれたブロックを記録
            )
        }
    }

    private fun getValidMoves(): List<Pair<Int, Int>> {
        val validMoves = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until BOARD_HEIGHT) {
            for (col in 0 until BOARD_WIDTH) {
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

    private fun getExpertMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
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

        // 3. 上記以外の場合、評価関数を用いて最適な手を探す
        var bestMove: Pair<Int, Int>? = null
        var bestScore = Int.MIN_VALUE

        for ((row, col) in validMoves) {
            // evaluateMoveのdepthは2に設定し、計算負荷を考慮
            val score = evaluateMove(row, col, cpuCellState, playerCellState, 2)
            if (score > bestScore) {
                bestScore = score
                bestMove = Pair(row, col)
            }
        }

        return bestMove ?: getRandomMove(validMoves)
    }

    private fun evaluateMove(row: Int, col: Int, cpuCellState: CellState, playerCellState: CellState, depth: Int): Int {
        var score = 0

        // --- 守備的評価 --- 
        // 相手がここに置くとリーチになるかをテストし、それを阻止する手に高いスコアを与える
        val opponentTestBoard = gameState.board.map { it.clone() }.toTypedArray()
        opponentTestBoard[row][col] = playerCellState
        if (countConnections(opponentTestBoard, row, col, playerCellState) == 3) {
            score += 5000 // 相手のリーチ阻止を最優先
        }

        // --- 攻撃的評価 ---
        // 自分がここに置くとリーチになるかをテストし、その手に高いスコアを与える
        val cpuTestBoard = gameState.board.map { it.clone() }.toTypedArray()
        cpuTestBoard[row][col] = cpuCellState
        if (countConnections(cpuTestBoard, row, col, cpuCellState) == 3) {
            score += 4000 // 自分のリーチ作成を次点で優先
        }

        // --- 基本評価 ---
        // 中央に近いほど評価を少し上げる
        val centerDistance = kotlin.math.abs(row - 5.5) + kotlin.math.abs(col - 5.5)
        score += (12 - centerDistance.toInt()) * 10

        // 自分の接続数を評価（2つ繋がりなど）
        score += countConnections(cpuTestBoard, row, col, cpuCellState) * 20

        return score
    }

    private fun countConnections(board: Array<Array<CellState>>, row: Int, col: Int, cellState: CellState): Int {
        val directions = listOf(
            Pair(0, 1),   // 横
            Pair(1, 0),   // 縦
            Pair(1, 1),   // 右下斜め
            Pair(1, -1)   // 左下斜め
        )

        var maxConnection = 0

        for ((dRow, dCol) in directions) {
            var count = 1 // 配置したセル自体をカウント

            // 正方向にチェック
            var r = row + dRow
            var c = col + dCol
            while (r in 0 until BOARD_HEIGHT && c in 0 until BOARD_WIDTH && board[r][c] == cellState) {
                count++
                r += dRow
                c += dCol
            }

            // 逆方向にチェック
            r = row - dRow
            c = col - dCol
            while (r in 0 until BOARD_HEIGHT && c in 0 until BOARD_WIDTH && board[r][c] == cellState) {
                count++
                r -= dRow
                c -= dCol
            }

            maxConnection = kotlin.math.max(maxConnection, count)
        }

        return maxConnection
    }

    private fun getValidMovesForBoard(board: Array<Array<CellState>>): List<Pair<Int, Int>> {
        val validMoves = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until BOARD_HEIGHT) {
            for (col in 0 until BOARD_WIDTH) {
                if (board[row][col] == CellState.EMPTY && isValidPlacementForBoard(board, row, col)) {
                    validMoves.add(Pair(row, col))
                }
            }
        }
        return validMoves
    }

    private fun isValidPlacementForBoard(board: Array<Array<CellState>>, row: Int, col: Int): Boolean {
        // 物理法則：最下段または既存ブロックの上でなければならない
        if (row != BOARD_HEIGHT - 1 && board[row + 1][col] == CellState.EMPTY) {
            return false
        }

        // 初手（盤面が空）なら最下段のどこでも可
        val isEmpty = board.all { row -> row.all { it == CellState.EMPTY } }
        if (isEmpty) {
            return row == BOARD_HEIGHT - 1  // 最下段のみ
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
            if (newRow in 0 until BOARD_HEIGHT && newCol in 0 until BOARD_WIDTH && board[newRow][newCol] != CellState.EMPTY) {
                return true
            }
        }

        return false
    }

    fun resetGame() {
        gameState = GameState()
    }
}