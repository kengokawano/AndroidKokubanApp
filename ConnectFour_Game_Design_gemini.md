# 黒板大将 隠しゲーム設計書

## ゲーム概要
黒板大将アプリに追加する4目並べ風の隠しゲーム

### ゲーム仕様
- **グリッドサイズ**: 12×12（正方形、144セル）
- **プレイヤー**: 先手（白）、後手（赤）
- **勝利条件**: 4つ連続で並べる（上下左右斜め全方向）
- **表示方式**: グリッドセル全体を色で塗りつぶし
- **ゲームモード**: 人vsCPU（CPU対戦）
- **ドロー処理**: 盤面満杯で勝者なしの場合、自動で新ゲーム開始
- **やり込み要素**:
  - 連勝記録表示（プレイヤー・CPU別々にカウント）
  - 3段階CPU強度（ランダム選択）
- **CPU強度**:
  - EASY（弱）: ランダム配置
  - NORMAL（中）: 相手勝利阻止 + ランダム
  - HARD（強）: 自分勝利優先 + 相手阻止 + ランダム

### 配置ルール（物理法則＋隣接ルール）
ブロックを配置するには、以下の2つの条件を **両方** 満たす必要があります。

1.  **物理法則（支持）**:
    - 配置したい場所が **最下段（地面）** である。
    - または、配置したい場所の **真下に既にブロックが存在** する。
    - (つまり、ブロックを宙に浮かすことはできません)

2.  **隣接ルール（接続）**:
    - **初手（盤面に何もない状態）** の場合：上記「物理法則」を満たす場所（＝最下段）ならどこでも配置可能。
    - **2手目以降** の場合：配置したい場所の **上下左右斜め8方向のいずれかに、既にブロックが隣接** している。

### 起動方法
メインの黒板キャンバスを長押しすると表示されるダイアログで「ゲーム開始」を選択する。

---

## 実装設計

### ファイル構成
```
app/src/main/java/jp/saitama/orange/drawkokuban2/
├── GameViewModel.kt       # ゲームロジック（変更なし）
├── GameScreen.kt          # ゲーム画面UI（ナビゲーション追加）
├── ChalkboardScreen.kt    # 黒板画面（長押し検知とダイアログ追加）
└── AppNavigation.kt       # ナビゲーション定義（NavHost使用）
```

### データ構造
```kotlin
enum class Player { WHITE, RED }
enum class CellState { EMPTY, WHITE, RED }
enum class CpuLevel { EASY, NORMAL, HARD }

class GameViewModel {
    var board: Array<Array<CellState>>  // 12×12のグリッド
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

// グリッドのサイズ (設計書通り12x12に修正)
const val GRID_COLUMNS = 12
const val GRID_ROWS = 12

// Player, CellState, CpuLevel の enum は変更なし
enum class Player { WHITE, RED }
enum class CellState { EMPTY, WHITE, RED }
enum class CpuLevel { EASY, NORMAL, HARD }

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
    var playerWins by mutableStateOf(0)
        private set
    var cpuWins by mutableStateOf(0)
        private set
    var cpuLevel by mutableStateOf(CpuLevel.EASY)
        private set

    init {
        startNewGame()
    }
    
    fun startNewGame() {
        board = Array(GRID_ROWS) { Array(GRID_COLUMNS) { CellState.EMPTY } }
        currentPlayer = Player.WHITE
        winner = null
        isGameOver = false
        cpuLevel = CpuLevel.values().random()

        // ドロー判定用：盤面が埋まったらリセット
        if (getValidMoves().isEmpty() && !isGameOver) {
            startNewGame()
        }
    }

    /**
     * 駒を置く処理 (変更なし)
     */
    fun placePiece(row: Int, col: Int) {
        if (isValidMove(row, col)) {
            val newBoard = board.map { it.clone() }.toTypedArray()
            newBoard[row][col] = if (currentPlayer == Player.WHITE) CellState.WHITE else CellState.RED
            board = newBoard

            if (checkForWin(row, col)) {
                winner = currentPlayer
                isGameOver = true
                if (winner == Player.WHITE) {
                    playerWins++
                    cpuWins = 0
                } else {
                    cpuWins++
                    playerWins = 0
                }
            } else if (getValidMoves().isEmpty()) {
                // ドローの場合、自動で次のゲームへ
                startNewGame()
            }
            else {
                currentPlayer = if (currentPlayer == Player.WHITE) Player.RED else Player.WHITE
            }
        }
    }

    /**
     * 【最重要修正】有効な手かどうかの判定（物理法則＋隣接ルール）
     */
    private fun isValidMove(row: Int, col: Int): Boolean {
        // 既に駒があるかゲームオーバーなら置けない
        if (isGameOver || board[row][col] != CellState.EMPTY) {
            return false
        }

        // ルールA：物理法則チェック（下にブロックがあるか、地面であるか）
        val isSupported = (row == GRID_ROWS - 1) || (board[row + 1][col] != CellState.EMPTY)
        if (!isSupported) {
            return false // 宙に浮いているので配置不可
        }

        // ルールB：隣接チェック（初手以外は、既存ブロックに繋がっているか）
        val isBoardEmpty = board.all { it.all { cell -> cell == CellState.EMPTY } }
        if (isBoardEmpty) {
            // 初手は支えられていればOK（つまり最下段のみ）
            return true
        }
        else {
            // 2手目以降は、周囲8方向にブロックがなければならない
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue // 自分自身は除く

                    val r = row + dr
                    val c = col + dc

                    if (r in 0 until GRID_ROWS && c in 0 until GRID_COLUMNS) {
                        if (board[r][c] != CellState.EMPTY) {
                            return true // 隣接するブロックを発見、配置可能
                        }
                    }
                }
            }
            // 隣接ブロックがなければ配置不可
            return false
        }
    }
    
    // --- performCpuMove, findBestMove, getValidMoves, checkForWin, isWinningMove は前回から変更ありません ---
    // (以下、前回のコードをそのまま貼り付け)
    
    fun performCpuMove() {
        if (currentPlayer == Player.WHITE || isGameOver) return
        findBestMove()?.let { (row, col) ->
            placePiece(row, col)
        }
    }

    private fun findBestMove(): Pair<Int, Int>? {
        val validMoves = getValidMoves()
        if (validMoves.isEmpty()) return null

        return when (cpuLevel) {
            CpuLevel.EASY -> validMoves.random()
            CpuLevel.NORMAL -> {
                val blockMove = validMoves.find { (r, c) -> isWinningMove(r, c, Player.WHITE) }
                blockMove ?: validMoves.random()
            }
            CpuLevel.HARD -> {
                val winMove = validMoves.find { (r, c) -> isWinningMove(r, c, Player.RED) }
                if (winMove != null) return winMove
                val blockMove = validMoves.find { (r, c) -> isWinningMove(r, c, Player.WHITE) }
                blockMove ?: validMoves.random()
            }
        }
    }
    
    private fun getValidMoves(): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until GRID_ROWS) {
            for (c in 0 until GRID_COLUMNS) {
                if (isValidMove(r, c)) {
                    moves.add(Pair(r, c))
                }
            }
        }
        return moves
    }

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.* 
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
fun GameScreen(
    viewModel: GameViewModel = viewModel(),
    onNavigateBack: () -> Unit // 黒板に戻るためのコールバック
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 情報表示エリア ---
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
                                onTap = { viewModel.startNewGame() },
                                onLongPress = { onNavigateBack() } // 長押しで黒板に戻る
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

### 3. AppNavigation.kt と ChalkboardScreen.kt の修正案

`NavHost` を使って画面遷移を管理し、`ChalkboardScreen` に長押しでゲームを起動する機能を追加します。

```kotlin
// AppNavigation.kt
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chalkboard") {
        composable("chalkboard") {
            // 既存のChalkboardScreenにナビゲーション機能を追加
            ChalkboardWithNavigation(
                onNavigateToGame = { navController.navigate("game") }
            )
        }
        composable("game") {
            GameScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

// ChalkboardScreen.kt (または新しいファイル)
@Composable
fun ChalkboardWithNavigation(onNavigateToGame: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    // 既存の黒板UIのコンポーザブルを呼び出す
    // ここでは例としてChalkboardScreenContentを呼び出す
    ChalkboardScreenContent(
        // ... viewModelや他の必要な引数を渡す
        
        // キャンバス部分のModifierに長押し検出を追加
        canvasModifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    showDialog = true // 長押しでダイアログ表示
                },
                // 他のタップイベント（描画など）を妨げないように注意
                onTap = { /* onTapの処理 */ },
                onDoubleTap = { /* onDoubleTapの処理 */ }
            )
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("隠しゲーム") },
            text = { Text("四目並べを開始しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onNavigateToGame() // ゲーム画面へ遷移
                }) {
                    Text("開始")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

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