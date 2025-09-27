package jp.saitama.orange.drawkokuban2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.saitama.orange.drawkokuban2.ui.theme.MyApplicationTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInCubic
import kotlinx.coroutines.launch

class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Activity起動時に必ず設定をチェックして通知サービスを開始
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val quickAccessEnabled = prefs.getBoolean("quick_access_notification", false)

        if (quickAccessEnabled) {
            // 設定がONなら強制的に通知サービスを開始
            NotificationService.startService(this)
        }

        setContent {
            MyApplicationTheme {
                val gameViewModel: GameViewModel = viewModel()
                GameScreen(
                    onClose = { finish() },
                    gameViewModel = gameViewModel
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // アプリが最小化（ホーム画面に戻る）された時、設定に応じて通知サービスを開始
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val quickAccessEnabled = prefs.getBoolean("quick_access_notification", false)

        if (quickAccessEnabled) {
            NotificationService.startService(this)
        }
    }
}

@Composable
fun GameScreen(onClose: () -> Unit, gameViewModel: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        WoodTextureBackground()

        // 左上の連勝数表示
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0x80000000) // 半透明黒
            )
        ) {
            Text(
                text = "連勝: ${gameViewModel.gameState.playerWinStreak}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )
        }

        // 右上のバツボタン
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "閉じる",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // 盤面を絶対的な中央に固定
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // ゲーム盤面（常に中央固定、縦横比14:11）
            GameBoard(
                modifier = Modifier
                    .fillMaxWidth(0.97f)
                    .aspectRatio(BOARD_WIDTH.toFloat() / BOARD_HEIGHT.toFloat()),
                gameViewModel = gameViewModel
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 上部のコンテンツ
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                text = stringResource(R.string.game_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 日直表示
            val context = LocalContext.current
            val dutyStudent = remember { StudentNameManager.getTodaysDutyStudent(context) }
            Text(
                text = stringResource(R.string.duty_student_label) + " $dutyStudent",
                fontSize = 16.sp,
                color = Color.Yellow,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ゲーム開始（初期画面でのみ表示）
            if (gameViewModel.gameState.board.all { row -> row.all { it == CellState.EMPTY } }) {
                Button(
                    onClick = {
                        gameViewModel.startCpuGame()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    )
                ) {
                    Text(
                        stringResource(R.string.game_start_button),
                        color = Color.Black,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ルール表示（ゲーム開始前のみ）
            if (gameViewModel.gameState.gameMode == GameMode.SINGLE_PLAYER && gameViewModel.gameState.board.all { row -> row.all { it == CellState.EMPTY } }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0x80000000) // 半透明黒
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.game_rules_title),
                            color = Color.Yellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.game_rule_1),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(R.string.game_rule_2),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(R.string.game_rule_3),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(R.string.game_rule_4),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ゲーム情報表示
            if (gameViewModel.gameState.gameMode == GameMode.VS_CPU) {
                Text(
                    text = "難易度: ${
                        when (gameViewModel.gameState.cpuDifficulty) {
                            CpuDifficulty.EASY -> "EASY"
                            CpuDifficulty.NORMAL -> "NORMAL"
                            CpuDifficulty.HARD -> "HARD"
                            CpuDifficulty.EXPERT -> "EXPERT"
                        }
                    }",
                    color = Color(0xFF2E5A3E),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "あなた: ${if (gameViewModel.gameState.playerIsWhite) "白（先攻）" else "赤（後攻）"}",
                    color = if (gameViewModel.gameState.playerIsWhite) Color.White else Color.Red,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            }

            // 下部のコンテンツ
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            // 現在の状況表示
            if (gameViewModel.gameState.isWaitingForCpu) {
                Text(
                    text = "CPUが思考中...",
                    fontSize = 16.sp,
                    color = Color.Yellow
                )
            } else if (!gameViewModel.gameState.isGameOver && gameViewModel.gameState.gameMode == GameMode.VS_CPU) {
                val currentPlayerText = if (gameViewModel.gameState.currentPlayer == Player.WHITE) {
                    if (gameViewModel.gameState.playerIsWhite) "あなたのターン（白）" else "CPUのターン（白）"
                } else {
                    if (!gameViewModel.gameState.playerIsWhite) "あなたのターン（赤）" else "CPUのターン（赤）"
                }
                Text(
                    text = currentPlayerText,
                    fontSize = 22.sp,
                    color = Color.White
                )
            } else if (gameViewModel.gameState.board.all { row -> row.all { it == CellState.EMPTY } }) {
                Text(
                    text = stringResource(R.string.dialog_game_title),
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            // 勝利・引き分け表示
            if (gameViewModel.gameState.isGameOver) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            gameViewModel.gameState.isDraw -> Color.Gray
                            gameViewModel.gameState.winner == Player.WHITE -> Color.White
                            gameViewModel.gameState.winner == Player.RED -> AppColors.RED
                            else -> Color.White
                        }
                    )
                ) {
                    Text(
                        text = when {
                            gameViewModel.gameState.isDraw -> "引き分け！"
                            gameViewModel.gameState.winner == Player.WHITE -> "白の勝利！"
                            gameViewModel.gameState.winner == Player.RED -> "赤の勝利！"
                            else -> ""
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            gameViewModel.gameState.isDraw -> Color.White
                            gameViewModel.gameState.winner == Player.WHITE -> Color.Black
                            gameViewModel.gameState.winner == Player.RED -> Color.White
                            else -> Color.Black
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { gameViewModel.resetGame() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text(
                            "最初に戻る",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { gameViewModel.startCpuGame() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(
                            "もう一度",
                            color = Color.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun GameBoard(modifier: Modifier = Modifier, gameViewModel: GameViewModel) {
    val blockAnimations = remember { mutableStateMapOf<Pair<Int, Int>, Animatable<Float, *>>() }

    // 新しいブロックが置かれたのを検知してアニメーションを開始する
    LaunchedEffect(gameViewModel.gameState.lastPlacedBlock) {
        val targetBlock = gameViewModel.gameState.lastPlacedBlock
        if (targetBlock != null && !blockAnimations.containsKey(targetBlock)) {
            val (row, _) = targetBlock
            val animatable = Animatable(0f) // Y座標を0（一番上）から開始
            blockAnimations[targetBlock] = animatable

            launch {
                animatable.animateTo(
                    targetValue = row.toFloat(), // 最終的な行インデックスまでアニメーション
                    animationSpec = tween(durationMillis = 400, easing = EaseInCubic) // 0.4秒で落下
                )
                // アニメーション完了後、マップから削除してViewModelに通知
                blockAnimations.remove(targetBlock)
                gameViewModel.onAnimationCompleted()
            }
        }
    }

    // 安定したセンター配置のためのBox
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { offset ->
                val boardWidth = kotlin.math.min(size.width, size.height)
                val boardHeight = boardWidth * BOARD_HEIGHT.toFloat() / BOARD_WIDTH.toFloat()
                val cellWidth = boardWidth / BOARD_WIDTH.toFloat()
                val cellHeight = boardHeight / BOARD_HEIGHT.toFloat()

                val col = (offset.x / cellWidth).toInt()
                val row = (offset.y / cellHeight).toInt()

                if (row in 0 until BOARD_HEIGHT && col in 0 until BOARD_WIDTH) {
                    gameViewModel.onCellClick(row, col)
                }
            }
        }
    ) {
        val boardWidth = kotlin.math.min(size.width, size.height)
        val boardHeight = boardWidth * BOARD_HEIGHT.toFloat() / BOARD_WIDTH.toFloat()
        val cellWidth = boardWidth / BOARD_WIDTH.toFloat()
        val cellHeight = boardHeight / BOARD_HEIGHT.toFloat()

        // 背景色を描画（矩形領域）
        drawRect(
            color = Color(0xFF0F3D20),
            size = Size(boardWidth, boardHeight)
        )

        // セルを描画
        for (row in 0 until BOARD_HEIGHT) {
            for (col in 0 until BOARD_WIDTH) {
                val cellState = gameViewModel.gameState.board[row][col]
                val pos = Pair(row, col)

                // 配置済みブロックの描画
                if (cellState != CellState.EMPTY) {
                    val baseColor = when (cellState) {
                        CellState.WHITE -> AppColors.WHITE
                        CellState.RED -> AppColors.RED
                        else -> Color.Transparent
                    }

                    // 勝利ラインのハイライト
                    val isWinningCell = gameViewModel.gameState.winningLine.contains(Pair(row, col))
                    val color = if (isWinningCell) {
                        when (cellState) {
                            CellState.WHITE -> AppColors.WINNING_WHITE
                            CellState.RED -> AppColors.WINNING_RED
                            else -> baseColor
                        }
                    } else {
                        baseColor
                    }

                    // アニメーション中の場合のみアニメーション座標を使用
                    val yPosition = if (blockAnimations.containsKey(pos)) {
                        blockAnimations[pos]?.value ?: row.toFloat()
                    } else {
                        row.toFloat()
                    }

                    drawRect(
                        color = color,
                        topLeft = Offset(col * cellWidth, yPosition * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )

                    // 勝利ラインに枠線を追加
                    if (isWinningCell) {
                        drawRect(
                            color = Color.Yellow,
                            topLeft = Offset(col * cellWidth, row * cellHeight), // 枠線はアニメーションさせない
                            size = Size(cellWidth, cellHeight),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // 配置可能位置のハイライト
                else if (gameViewModel.gameState.validMoves.contains(Pair(row, col)) && !gameViewModel.gameState.isGameOver) {
                    val isPlayerTurn = when {
                        gameViewModel.gameState.gameMode == GameMode.SINGLE_PLAYER -> true
                        gameViewModel.gameState.isWaitingForCpu -> false
                        gameViewModel.gameState.playerIsWhite -> gameViewModel.gameState.currentPlayer == Player.WHITE
                        else -> gameViewModel.gameState.currentPlayer == Player.RED
                    }

                    if (isPlayerTurn) {
                        drawRect(
                            color = AppColors.VALID_MOVE_HIGHLIGHT,
                            topLeft = Offset(col * cellWidth, row * cellHeight),
                            size = Size(cellWidth, cellHeight)
                        )
                    }
                }
            }
        }

        // グリッド線を描画（薄い白色）
        val gridColor = AppColors.GRID_LINE

        // 縦線
        for (i in 0..BOARD_WIDTH) {
            val x = i * cellWidth
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, boardHeight),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // 横線
        for (i in 0..BOARD_HEIGHT) {
            val y = i * cellHeight
            val lineColor = if (i == BOARD_HEIGHT) {
                AppColors.BOTTOM_LINE_HIGHLIGHT
            } else {
                gridColor
            }

            val strokeWidth = if (i == BOARD_HEIGHT) {
                2.0.dp.toPx()
            } else {
                0.5.dp.toPx()
            }

            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(boardWidth, y),
                strokeWidth = strokeWidth
            )
        }
    }
    }
}

