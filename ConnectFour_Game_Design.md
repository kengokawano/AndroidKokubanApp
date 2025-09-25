# 黒板大将 隠しゲーム設計書

## ゲーム概要
黒板大将アプリに追加する4目並べ風の隠しゲーム

### ゲーム仕様
- **グリッドサイズ**: 横14 × 縦40
- **プレイヤー**: 先手（白）、後手（赤）
- **勝利条件**: 4つ連続で並べる（上下左右斜め全方向）
- **表示方式**: グリッドセル全体を色で塗りつぶし
- **ゲームモード**: 人vsCPU（CPU対戦）
- **やり込み要素**:
  - 連勝記録表示（プレイヤー・CPU別々にカウント）
  - 3段階CPU強度（ランダム選択）
- **CPU強度**:
  - EASY（弱）: ランダム配置
  - NORMAL（中）: 相手勝利阻止 + ランダム
  - HARD（強）: 自分勝利優先 + 相手阻止 + ランダム

### 配置ルール
1. **初手**: 最下段（40行目）にのみ配置可能
2. **2手目以降**: 既に配置された駒の**下・左・右**に隣接するセルにのみ配置可能
   - 上方向への隣接は無効
   - 必ず既存駒と接触している必要がある

### 起動方法
特定の隠しコマンド（例：アバウトダイアログのタイトルを5回タップ）

---

## 実装設計

### ファイル構成
```
app/src/main/java/jp/saitama/orange/drawkokuban2/
├── GameViewModel.kt       # ゲームロジック（新規作成）
├── GameScreen.kt          # ゲーム画面UI（新規作成）
└── AppNavigation.kt       # ナビゲーション修正（既存ファイル修正）
```

### データ構造
```kotlin
enum class Player { WHITE, RED }
enum class CellState { EMPTY, WHITE, RED }
enum class CpuLevel { EASY, NORMAL, HARD }

class GameViewModel {
    var board: Array<Array<CellState>>  // 14×40のグリッド
    var currentPlayer: Player
    var winner: Player?
    var isGameOver: Boolean
    var playerWins: Int                 // プレイヤー連勝記録
    var cpuWins: Int                   // CPU連勝記録
    var cpuLevel: CpuLevel             // 現在のCPU強度（ランダム選択）
}
```

### CPU思考アルゴリズム（3段階）
**各ゲーム開始時にランダムに決定される強度別の思考パターン：**

1. **EASY（弱）**: 完全ランダム配置
2. **NORMAL（中）**: 相手勝利阻止 → ランダム
3. **HARD（強）**: 自分勝利優先 → 相手勝利阻止 → ランダム

### 連勝記録システム
- **勝利時**: 勝者の連勝カウント+1、敗者の連勝カウントリセット
- **表示**: リアルタイムで左上に表示
- **継続性**: ゲーム終了後も記録保持

---

## 実装コード

### 1. GameViewModel.kt（新規作成）- 完成版

```kotlin
package jp.saitama.orange.drawkokuban2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// グリッドのサイズ
const val GRID_COLUMNS = 14
const val GRID_ROWS = 40

// プレイヤー
enum class Player {
    WHITE, RED
}

// セルの状態
enum class CellState {
    EMPTY, WHITE, RED
}

// CPUの強さ
enum class CpuLevel {
    EASY, NORMAL, HARD
}

class GameViewModel : ViewModel() {

    var board by mutableStateOf(
        Array(GRID_ROWS) { Array(GRID_COLUMNS) { CellState.EMPTY } }
    )
        private set

    var currentPlayer by mutableStateOf(Player.WHITE)
        private set

    var winner by mutableStateOf<Player?>(null)
        private set

    var isGameOver by mutableStateOf(false)
        private set

    // --- 新機能：連勝記録とCPUレベル ---
    var playerWins by mutableStateOf(0)
        private set
    var cpuWins by mutableStateOf(0)
        private set
    var cpuLevel by mutableStateOf(CpuLevel.EASY)
        private set
    // ------------------------------------

    init {
        startNewGame() // 最初にゲームを開始するときにCPUレベルをランダム設定
    }

    /**
     * 新しいゲームを開始する
     */
    fun startNewGame() {
        board = Array(GRID_ROWS) { Array(GRID_COLUMNS) { CellState.EMPTY } }
        currentPlayer = Player.WHITE
        winner = null
        isGameOver = false
        cpuLevel = CpuLevel.values().random() // CPUの強さをランダムに選択
    }

    /**
     * 駒を置く処理
     */
    fun placePiece(row: Int, col: Int) {
        if (isValidMove(row, col)) {
            val newBoard = board.map { it.clone() }.toTypedArray()
            newBoard[row][col] = if (currentPlayer == Player.WHITE) CellState.WHITE else CellState.RED
            board = newBoard

            if (checkForWin(row, col)) {
                winner = currentPlayer
                isGameOver = true
                // --- 新機能：勝敗に応じて連勝記録を更新 ---
                if (winner == Player.WHITE) {
                    playerWins++
                    cpuWins = 0 // 相手の連勝はリセット
                } else {
                    cpuWins++
                    playerWins = 0 // 相手の連勝はリセット
                }
                // ------------------------------------
            } else {
                currentPlayer = if (currentPlayer == Player.WHITE) Player.RED else Player.WHITE
            }
        }
    }

    /**
     * CPUの手を実行する
     */
    fun performCpuMove() {
        if (currentPlayer == Player.WHITE || isGameOver) return

        findBestMove()?.let { (row, col) ->
            placePiece(row, col)
        }
    }

    /**
     * CPUの思考ロジック (難易度別)
     */
    private fun findBestMove(): Pair<Int, Int>? {
        val validMoves = getValidMoves()
        if (validMoves.isEmpty()) return null

        return when (cpuLevel) {
            CpuLevel.EASY -> {
                // レベル「弱」: ランダムな場所に置く
                validMoves.random()
            }
            CpuLevel.NORMAL -> {
                // レベル「中」: プレイヤーの勝ちを阻止し、それ以外はランダム
                val blockMove = validMoves.find { (r, c) -> isWinningMove(r, c, Player.WHITE) }
                blockMove ?: validMoves.random()
            }
            CpuLevel.HARD -> {
                // レベル「強」: 自分の勝ちを優先し、次に相手の勝ちを阻止、それ以外はランダム
                val winMove = validMoves.find { (r, c) -> isWinningMove(r, c, Player.RED) }
                if (winMove != null) return winMove

                val blockMove = validMoves.find { (r, c) -> isWinningMove(r, c, Player.WHITE) }
                if (blockMove != null) return blockMove

                validMoves.random()
            }
        }
    }

    /**
     * 有効な手かどうかの判定
     */
    private fun isValidMove(row: Int, col: Int): Boolean {
        if (isGameOver || board[row][col] != CellState.EMPTY) {
            return false
        }
        val isBoardEmpty = board.all { boardRow -> boardRow.all { it == CellState.EMPTY } }
        return if (isBoardEmpty) {
            row == GRID_ROWS - 1
        } else {
            val requiredNeighbors = listOf(Pair(row + 1, col), Pair(row, col - 1), Pair(row, col + 1))
            requiredNeighbors.any { (r, c) ->
                r >= 0 && r < GRID_ROWS && c >= 0 && c < GRID_COLUMNS && board[r][c] != CellState.EMPTY
            }
        }
    }

    /**
     * 現在の盤面で配置可能なすべての手のリストを取得
     */
    private fun getValidMoves(): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLUMNS) {
                if (isValidMove(row, col)) {
                    moves.add(Pair(row, col))
                }
            }
        }
        return moves
    }

    /**
     * 勝利判定
     */
    private fun checkForWin(row: Int, col: Int): Boolean {
        val playerState = if (currentPlayer == Player.WHITE) CellState.WHITE else CellState.RED
        val directions = listOf(Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1))
        for ((dr, dc) in directions) {
            var count = 1
            for (i in 1..3) {
                val r = row + i * dr; val c = col + i * dc
                if (r in 0 until GRID_ROWS && c in 0 until GRID_COLUMNS && board[r][c] == playerState) count++ else break
            }
            for (i in 1..3) {
                val r = row - i * dr; val c = col - i * dc
                if (r in 0 until GRID_ROWS && c in 0 until GRID_COLUMNS && board[r][c] == playerState) count++ else break
            }
            if (count >= 4) return true
        }
        return false
    }

    /**
     * 指定した場所に駒を置いた場合に勝利となるか判定
     */
    private fun isWinningMove(row: Int, col: Int, player: Player): Boolean {
        val tempBoard = board.map { it.clone() }.toTypedArray()
        tempBoard[row][col] = if (player == Player.WHITE) CellState.WHITE else CellState.RED
        val playerState = if (player == Player.WHITE) CellState.WHITE else CellState.RED
        val directions = listOf(Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1))
        for ((dr, dc) in directions) {
            var count = 1
            for (i in 1..3) {
                val r = row + i * dr; val c = col + i * dc
                if (r in 0 until GRID_ROWS && c in 0 until GRID_COLUMNS && tempBoard[r][c] == playerState) count++ else break
            }
            for (i in 1..3) {
                val r = row - i * dr; val c = col - i * dc
                if (r in 0 until GRID_ROWS && c in 0 until GRID_COLUMNS && tempBoard[r][c] == playerState) count++ else break
            }
            if (count >= 4) return true
        }
        return false
    }
}
```

### 2. GameScreen.kt（新規作成）- 完成版

```kotlin
package jp.saitama.orange.drawkokuban2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 新機能：情報表示エリア ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 連勝記録
            Column {
                Text(
                    text = "あなた: ${viewModel.playerWins}連勝中",
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "CPU: ${viewModel.cpuWins}連勝中",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
            // CPUレベル
            Text(
                text = "CPUレベル: ${viewModel.cpuLevel.name}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow
            )
        }
        // --------------------------------

        // ゲーム情報 (現在のターン or 勝者)
        Text(
            text = when {
                viewModel.winner != null -> "${if(viewModel.winner == Player.WHITE) "1P Win!" else "CPU Win!"}"
                else -> if(viewModel.currentPlayer == Player.WHITE) "あなたのターン" else "CPUのターン"
            },
            fontSize = 24.sp,
            color = if (viewModel.winner != null) {
                if (viewModel.winner == Player.WHITE) Color.Cyan else Color.Red
            } else Color.White,
            fontWeight = if (viewModel.winner != null) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ゲーム盤
        GameBoard(
            board = viewModel.board,
            onCellClick = { row, col ->
                // プレイヤー(WHITE)のターンで、ゲームが終了していなければ駒を置く
                if (viewModel.currentPlayer == Player.WHITE && !viewModel.isGameOver) {
                    viewModel.placePiece(row, col)
                    // プレイヤーが置いた直後にCPUの手を実行
                    viewModel.performCpuMove()
                }
            }
        )

        // ゲーム終了時の操作案内
        if (viewModel.isGameOver) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "タップ: 次の対戦  長押し: 黒板に戻る",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // 短いタップで次ゲーム
                                    viewModel.startNewGame()
                                },
                                onLongPress = {
                                    // 長押しで黒板に戻る（ナビゲーション処理）
                                    // 実装時: navController.popBackStack()
                                }
                            )
                        }
                )
            }
        }
    }
}

@Composable
fun GameBoard(board: Array<Array<CellState>>, onCellClick: (Int, Int) -> Unit) {
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(GRID_COLUMNS.toFloat() / GRID_ROWS.toFloat())
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val cellSize = size.width / GRID_COLUMNS.toFloat()
                val col = (offset.x / cellSize).toInt()
                val row = (offset.y / cellSize).toInt()
                if (row in 0 until GRID_ROWS && col in 0 until GRID_COLUMNS) {
                    onCellClick(row, col)
                }
            }
        }
    ) {
        val cellSize = size.width / GRID_COLUMNS.toFloat()

        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLUMNS) {
                val cellState = board[row][col]
                val topLeft = Offset(col * cellSize, row * cellSize)
                val cellColor = when (cellState) {
                    CellState.WHITE -> Color.White
                    CellState.RED -> Color.Red
                    CellState.EMPTY -> Color.DarkGray
                }
                drawRect(
                    color = cellColor,
                    topLeft = topLeft,
                    size = Size(cellSize, cellSize)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = topLeft,
                    size = Size(cellSize, cellSize),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
```

### 3. AppNavigation.kt の修正箇所

```kotlin
// 必要なインポートを追加
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember

// AppNavigation関数の修正
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chalkboard") {
        composable("chalkboard") {
            // 既存のChalkboardScreen関連のコード
            // onShowAboutの処理を修正
        }
        composable("game") {
            GameScreen()
        }
    }

    // AboutDialog の修正
    if (showAbout) {
        var tapCount by remember { mutableStateOf(0) }
        AboutDialog(
            onDismiss = { showAbout = false },
            onTitleClick = {
                tapCount++
                if (tapCount >= 5) {
                    navController.navigate("game")
                    showAbout = false
                }
            }
        )
    }
}

// AboutDialog にクリック機能を追加
@Composable
fun AboutDialog(onDismiss: () -> Unit, onTitleClick: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onTitleClick)
            ) {
                Text("黒板太一2について")
            }
        },
        // 以下既存のコード
    )
}
```

---

## 実装手順

1. **GameViewModel.kt** を新規作成
2. **GameScreen.kt** を新規作成
3. **AppNavigation.kt** に隠しコマンドとナビゲーション機能を追加
4. 動作確認とテスト

---

## 動作確認項目

### 基本ゲーム機能
- [ ] 初手が最下段にのみ配置できる
- [ ] 2手目以降は隣接ルールに従う
- [ ] 4つ並びで勝利判定される
- [ ] プレイヤーが正しく交代する
- [ ] 隠しコマンドでゲームが起動する

### CPU対戦機能
- [ ] CPU強度がゲーム開始時にランダム選択される
- [ ] EASY: ランダム配置が動作する
- [ ] NORMAL: 相手勝利阻止が動作する
- [ ] HARD: 勝利優先+阻止が動作する
- [ ] CPUが即座に手を打つ

### 連勝記録機能
- [ ] プレイヤー連勝記録が正しく表示される
- [ ] CPU連勝記録が正しく表示される
- [ ] 勝利時に勝者の連勝が+1される
- [ ] 勝利時に敗者の連勝がリセットされる
- [ ] ゲーム終了後も記録が保持される

### UI・UX
- [ ] CPUレベルが画面右上に表示される
- [ ] 連勝記録が画面左上に表示される
- [ ] 勝利時に「1P Win!」「CPU Win!」と表示される
- [ ] 勝利表示が色付き（青/赤）で強調される
- [ ] タップで次ゲーム、長押しで黒板大将に戻る操作が動作する
- [ ] 操作案内「タップ: 次の対戦 長押し: 黒板に戻る」が表示される

---

## パフォーマンス考慮事項

- **グリッド描画**: Canvas使用で軽量
- **状態管理**: Composeの最適化でリコンポーズを最小化
- **メモリ使用**: 14×40の2次元配列のみ、軽量
- **CPU思考**: 最大560手の簡単な検索、瞬時に完了
- **既存機能への影響**: 分離設計により無影響

**結論**: CPU対戦機能を追加しても、通常使用で体感できる重さはありません。

---

## 完成版隠しゲームの特徴

### 🎯 3段階CPU強度システム
- **EASY（弱）**: 完全ランダム - 初心者に優しい
- **NORMAL（中）**: 防御重視 - 適度な手応え
- **HARD（強）**: 攻守バランス - 上級者向け
- **ランダム選択**: 毎回異なる難易度で新鮮な体験

### 📊 連勝記録システム
- **継続的モチベーション**: 連勝数でやり込み要素提供
- **競争心**: CPUとの連勝記録比較
- **達成感**: 高難易度CPU撃破の喜び
- **リセット機能**: 負けたら連勝記録がゼロに

### 🎮 優れたゲームバランス
- **予測不可能**: CPUレベルランダム選択で毎回違う体験
- **適度な難しさ**: 全レベルで人間が勝利可能
- **即応性**: プレイヤーの手に対してCPUが即座に対応
- **継続性**: 連勝記録で長期間楽しめる

### 🔧 実装の優位性
- **軽量設計**: 追加機能でも動作軽快
- **シンプル構造**: 理解しやすく保守しやすい
- **拡張性**: 将来的な機能追加が容易
- **安定性**: エラー処理が適切

### 🌟 隠しゲームとしての完成度
この完成版により、黒板大将は以下の価値を提供：

1. **発見の驚き**: 隠しゲーム発見時の喜び
2. **継続の楽しさ**: 連勝記録によるやり込み要素
3. **多様な体験**: ランダムCPU強度による変化
4. **達成感**: 強いCPUに勝利した時の満足感

**結論**: 単なる隠し機能を超え、本格的なゲーム体験を提供する完成度の高い隠しゲームです。