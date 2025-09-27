package jp.saitama.orange.drawkokuban2

import android.content.Context
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
import androidx.compose.ui.platform.LocalConfiguration
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
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // レスポンシブな高さ設定
    val topSectionHeight = (screenHeight * 0.08f).coerceAtLeast(60.dp).coerceAtMost(100.dp)
    val bottomSectionHeight = (screenHeight * 0.18f).coerceAtLeast(120.dp).coerceAtMost(200.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ヘッダ
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text(stringResource(R.string.game_name), color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0B2E1A)
            ),
            actions = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.game_close_button),
                        tint = Color.White
                    )
                }
            }
        )

        // Body部分（上中下の3段構造）
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 【上部】ゲーム情報（難易度、先攻後攻）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topSectionHeight)
                    .background(Color(0xFF3E2723))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 左上の連勝数表示
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0x80000000) // 半透明黒
                    )
                ) {
                    Text(
                        text = stringResource(R.string.game_win_streak, gameViewModel.gameState.playerWinStreak),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp)
                    )
                }

                // 中央のゲーム情報
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (gameViewModel.gameState.gameMode == GameMode.VS_CPU) {
                        Text(
                            text = stringResource(
                                R.string.game_difficulty_label,
                                stringResource(
                                    when (gameViewModel.gameState.cpuDifficulty) {
                                        CpuDifficulty.EASY -> R.string.difficulty_easy
                                        CpuDifficulty.NORMAL -> R.string.difficulty_normal
                                        CpuDifficulty.HARD -> R.string.difficulty_hard
                                        CpuDifficulty.EXPERT -> R.string.difficulty_expert
                                    }
                                )
                            ),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                if (gameViewModel.gameState.playerIsWhite) R.string.game_player_role_white else R.string.game_player_role_red
                            ),
                            color = if (gameViewModel.gameState.playerIsWhite) Color.White else Color.Red,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // 【中央】盤面エリア
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                WoodTextureBackground()

                // 盤面を絶対的な中央に固定
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // ゲーム盤面（常に中央固定、縦横比14:11）
                    GameBoard(
                        modifier = Modifier
                            .fillMaxWidth(0.97f)
                            .aspectRatio(BOARD_WIDTH.toFloat() / BOARD_HEIGHT.toFloat()),
                        gameViewModel = gameViewModel
                    )

                    // ルール表示（ゲーム開始前のみ、中央に表示）
                    if (gameViewModel.gameState.gameMode == GameMode.SINGLE_PLAYER && gameViewModel.gameState.board.all { row -> row.all { it == CellState.EMPTY } }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xE0000000) // より濃い半透明黒
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.game_rules_title),
                                    color = Color.Yellow,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = stringResource(R.string.game_rule_1),
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.game_rule_2),
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.game_rule_3),
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.game_rule_4),
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 【下部】ボタンとステータス
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomSectionHeight)
                    .background(Color(0xFF3E2723))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // ゲーム開始（初期画面でのみ表示）
                if (gameViewModel.gameState.board.all { row -> row.all { it == CellState.EMPTY } } && gameViewModel.gameState.gameMode == GameMode.SINGLE_PLAYER) {
                    Button(
                        onClick = {
                            gameViewModel.startCpuGame()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.game_start_button),
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                    }
                }

                // 現在の状況表示
                if (gameViewModel.gameState.isWaitingForCpu) {
                    Text(
                        text = stringResource(R.string.game_cpu_thinking),
                        fontSize = 16.sp,
                        color = Color.Yellow,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (!gameViewModel.gameState.isGameOver && gameViewModel.gameState.gameMode == GameMode.VS_CPU) {
                    val currentPlayerText = if (gameViewModel.gameState.currentPlayer == Player.WHITE) {
                        if (gameViewModel.gameState.playerIsWhite) stringResource(R.string.game_your_turn_white) else stringResource(R.string.game_cpu_turn_white)
                    } else {
                        if (!gameViewModel.gameState.playerIsWhite) stringResource(R.string.game_your_turn_red) else stringResource(R.string.game_cpu_turn_red)
                    }
                    Text(
                        text = currentPlayerText,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (gameViewModel.gameState.board.all { row -> row.all { it == CellState.EMPTY } }) {
                    Text(
                        text = stringResource(R.string.dialog_game_title),
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // 勝利・引き分け表示
                if (gameViewModel.gameState.isGameOver) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                gameViewModel.gameState.isDraw -> Color.Gray
                                gameViewModel.gameState.winner == Player.WHITE -> Color.White
                                gameViewModel.gameState.winner == Player.RED -> AppColors.RED
                                else -> Color.White
                            }
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                gameViewModel.gameState.isDraw -> stringResource(R.string.game_result_draw)
                                gameViewModel.gameState.winner == Player.WHITE -> stringResource(R.string.game_result_white_win)
                                gameViewModel.gameState.winner == Player.RED -> stringResource(R.string.game_result_red_win)
                                else -> ""
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                gameViewModel.gameState.isDraw -> Color.White
                                gameViewModel.gameState.winner == Player.WHITE -> Color.Black
                                gameViewModel.gameState.winner == Player.RED -> Color.White
                                else -> Color.Black
                            },
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Button(
                            onClick = { gameViewModel.resetGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text(
                                stringResource(R.string.game_button_reset),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { gameViewModel.startCpuGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(
                                stringResource(R.string.game_button_restart),
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
                // アニメーション中はタップを無効にする
                if (blockAnimations.isNotEmpty()) {
                    return@detectTapGestures
                }

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

