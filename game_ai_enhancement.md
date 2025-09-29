# ゲームAI強化案

## 現在の難易度システム

### 既存の難易度レベル
- **EASY**: ランダムな手を選択
- **NORMAL**: プレイヤーの勝ちを阻止するのみ
- **HARD**: 自分の勝ち手 + プレイヤーの勝ちを阻止
- **EXPERT**: 上記 + 2手先読み評価関数
  - 守備的評価: 相手のリーチ阻止 (5000点)
  - 攻撃的評価: 自分のリーチ作成 (4000点)
  - 中央重視 + 接続数評価

## 追加可能な強化レベル

### 1. MASTER級
**特徴**: 中級者レベルの戦略的思考
- **3-4手先読み**: Minimax算法実装
- **精密な盤面評価**: より複雑な評価関数
- **フォーク戦略**: 複数の勝ち筋を同時に作成
- **パターン認識**: 危険な配置の早期検出

**実装方法**:
```kotlin
private fun getMasterMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
    return minimaxWithAlphaBeta(gameState.board, 4, true, Int.MIN_VALUE, Int.MAX_VALUE)
}
```

### 2. GRANDMASTER級
**特徴**: 上級者レベルの深い読み
- **5-6手先読み**: より深い探索
- **開局定石知識**: 序盤の最適手順
- **中盤・終盤別戦略**: ゲーム段階に応じた戦略
- **位置価値テーブル**: 各マスの戦略的価値

**実装方法**:
```kotlin
private fun getGrandMasterMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
    // 開局段階判定
    if (countPlacedPieces() < 8) {
        return getOpeningMove(validMoves)
    }
    // 深い読み
    return minimaxWithAlphaBeta(gameState.board, 6, true, Int.MIN_VALUE, Int.MAX_VALUE)
}
```

### 3. LEGENDARY級
**特徴**: エキスパートレベルの完全分析
- **7-8手先読み**: 完全探索に近い計算
- **棋譜学習機能**: 過去のゲームから学習
- **完全序盤分析**: 最初の10手の完全解析
- **エンドゲーム完全解**: 終盤の完全読み切り

## 技術的実装手法

### 1. Minimax算法
```kotlin
private fun minimax(board: Array<Array<CellState>>, depth: Int, isMaximizing: Boolean): Int {
    if (depth == 0 || isGameOver(board)) {
        return evaluateBoard(board)
    }

    if (isMaximizing) {
        var maxEval = Int.MIN_VALUE
        for (move in getValidMoves(board)) {
            val newBoard = makeMove(board, move)
            val eval = minimax(newBoard, depth - 1, false)
            maxEval = maxOf(maxEval, eval)
        }
        return maxEval
    } else {
        var minEval = Int.MAX_VALUE
        for (move in getValidMoves(board)) {
            val newBoard = makeMove(board, move)
            val eval = minimax(newBoard, depth - 1, true)
            minEval = minOf(minEval, eval)
        }
        return minEval
    }
}
```

### 2. Alpha-Beta枝刈り
```kotlin
private fun minimaxWithAlphaBeta(
    board: Array<Array<CellState>>,
    depth: Int,
    isMaximizing: Boolean,
    alpha: Int,
    beta: Int
): Int {
    if (depth == 0 || isGameOver(board)) {
        return evaluateBoard(board)
    }

    var alphaValue = alpha
    var betaValue = beta

    if (isMaximizing) {
        var maxEval = Int.MIN_VALUE
        for (move in getValidMoves(board)) {
            val newBoard = makeMove(board, move)
            val eval = minimaxWithAlphaBeta(newBoard, depth - 1, false, alphaValue, betaValue)
            maxEval = maxOf(maxEval, eval)
            alphaValue = maxOf(alphaValue, eval)
            if (betaValue <= alphaValue) break // Beta cutoff
        }
        return maxEval
    } else {
        var minEval = Int.MAX_VALUE
        for (move in getValidMoves(board)) {
            val newBoard = makeMove(board, move)
            val eval = minimaxWithAlphaBeta(newBoard, depth - 1, true, alphaValue, betaValue)
            minEval = minOf(minEval, eval)
            betaValue = minOf(betaValue, eval)
            if (betaValue <= alphaValue) break // Alpha cutoff
        }
        return minEval
    }
}
```

### 3. 強化された評価関数
```kotlin
private fun advancedEvaluateBoard(board: Array<Array<CellState>>): Int {
    var score = 0

    // 1. 勝利条件チェック
    val winner = checkWinner(board)
    if (winner == CPU_PLAYER) return 10000
    if (winner == HUMAN_PLAYER) return -10000

    // 2. 脅威レベル分析
    score += analyzeThreatLevel(board)

    // 3. ポジション価値
    score += evaluatePositionalValue(board)

    // 4. パターン認識
    score += recognizePatterns(board)

    // 5. モビリティ（選択肢の多さ）
    score += evaluateMobility(board)

    return score
}

private fun analyzeThreatLevel(board: Array<Array<CellState>>): Int {
    var score = 0

    // 即座の脅威（3連続）
    score += countThreats(board, CPU_PLAYER) * 1000
    score -= countThreats(board, HUMAN_PLAYER) * 1200 // 相手の脅威はより重要

    // 潜在的脅威（2連続）
    score += countPotentialThreats(board, CPU_PLAYER) * 100
    score -= countPotentialThreats(board, HUMAN_PLAYER) * 150

    return score
}
```

### 4. 開局定石システム
```kotlin
private val openingBook = mapOf(
    // 中央重視戦略
    emptyBoardHash() to listOf(Pair(5, 5), Pair(6, 6), Pair(4, 4)),

    // 相手の中央阻止
    centerOccupiedHash() to listOf(Pair(5, 4), Pair(4, 5), Pair(6, 5)),

    // 角攻め戦略
    cornerStrategyHash() to listOf(Pair(3, 3), Pair(8, 8), Pair(3, 8))
)

private fun getOpeningMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
    val boardHash = calculateBoardHash(gameState.board)
    openingBook[boardHash]?.let { preferredMoves ->
        for (move in preferredMoves) {
            if (move in validMoves) {
                return move
            }
        }
    }
    return null
}
```

## パフォーマンス最適化

### 1. 並行処理
```kotlin
private suspend fun getParallelMove(validMoves: List<Pair<Int, Int>>): Pair<Int, Int>? {
    return withContext(Dispatchers.Default) {
        val jobs = validMoves.map { move ->
            async {
                val score = evaluateMove(move.first, move.second, cpuCellState, playerCellState, 6)
                Pair(move, score)
            }
        }

        jobs.awaitAll().maxByOrNull { it.second }?.first
    }
}
```

### 2. メモ化（重複計算回避）
```kotlin
private val evaluationCache = mutableMapOf<String, Int>()

private fun getCachedEvaluation(board: Array<Array<CellState>>, depth: Int): Int? {
    val key = "${boardToString(board)}_$depth"
    return evaluationCache[key]
}

private fun cacheEvaluation(board: Array<Array<CellState>>, depth: Int, score: Int) {
    val key = "${boardToString(board)}_$depth"
    evaluationCache[key] = score
}
```

### 3. 時間制限付き探索
```kotlin
private fun getTimeLimitedMove(validMoves: List<Pair<Int, Int>>, timeLimit: Long): Pair<Int, Int>? {
    val startTime = System.currentTimeMillis()
    var bestMove: Pair<Int, Int>? = null
    var depth = 1

    while (System.currentTimeMillis() - startTime < timeLimit && depth <= 8) {
        val move = getDepthLimitedMove(validMoves, depth)
        if (move != null) {
            bestMove = move
        }
        depth++
    }

    return bestMove ?: getRandomMove(validMoves)
}
```

## 実装の段階的アプローチ

### Phase 1: MASTER級実装
1. 基本的なMinimax算法
2. 3-4手先読み
3. 改良された評価関数

### Phase 2: GRANDMASTER級実装
1. Alpha-Beta枝刈り追加
2. 5-6手先読み
3. 開局定石システム

### Phase 3: LEGENDARY級実装
1. 並行処理最適化
2. 7-8手先読み
3. メモ化システム
4. 学習機能

## 計算負荷対策

### 1. 段階的実装
- 低い難易度から順次実装
- ユーザーの端末性能に応じた調整

### 2. 最適化技術
- **枝刈り**: 不要な探索の削減
- **並行処理**: マルチコア活用
- **メモ化**: 重複計算回避
- **時間制限**: レスポンス性確保

### 3. 適応的調整
```kotlin
private fun getAdaptiveDifficulty(): CpuDifficulty {
    val processorCount = Runtime.getRuntime().availableProcessors()
    val availableMemory = Runtime.getRuntime().freeMemory()

    return when {
        processorCount >= 8 && availableMemory > 500_000_000 -> CpuDifficulty.LEGENDARY
        processorCount >= 4 && availableMemory > 200_000_000 -> CpuDifficulty.GRANDMASTER
        processorCount >= 2 -> CpuDifficulty.MASTER
        else -> CpuDifficulty.EXPERT
    }
}
```

## 機能拡張アイデア

### 1. 学習機能
- プレイヤーの癖を学習
- 対戦履歴から戦略調整
- 個人別最適化

### 2. 解説機能
- AIの思考過程表示
- なぜその手を選んだか説明
- 学習用アシスタント

### 3. 棋力測定
- プレイヤーのレーティング算出
- 適切な難易度自動選択
- 成長度合い追跡

## 結論

現在のEXPERTレベルから、さらに3段階の強化が技術的に実装可能です。段階的な実装により、初心者から上級者まで楽しめる幅広い難易度設定が実現できます。