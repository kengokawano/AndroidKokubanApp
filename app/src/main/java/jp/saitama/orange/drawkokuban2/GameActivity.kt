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
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.saitama.orange.drawkokuban2.ui.theme.MyApplicationTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInCubic
import kotlinx.coroutines.launch

class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

@Composable
fun GameScreen(onClose: () -> Unit, gameViewModel: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        WoodTextureBackground()

        // 左上の連勝数表示（CPU対戦時のみ）
        if (gameViewModel.gameState.gameMode == GameMode.VS_CPU) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "黒板大将",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ゲームモード選択（初期画面でのみ表示）
            if (gameViewModel.gameState.board.all { row -> row.all { it == CellState.EMPTY } }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            gameViewModel.startSinglePlayerGame()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gameViewModel.gameState.gameMode == GameMode.SINGLE_PLAYER) Color.White else Color.Gray
                        )
                    ) {
                        Text(
                            "一人モード",
                            color = Color.Black,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            gameViewModel.startCpuGame()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gameViewModel.gameState.gameMode == GameMode.VS_CPU) Color.White else Color.Gray
                        )
                    ) {
                        Text(
                            "CPU対戦",
                            color = Color.Black,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // CPU対戦モード時の情報表示
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
                    color = Color.White,
                    fontSize = 12.sp
                )
                Text(
                    text = "あなた: ${if (gameViewModel.gameState.playerIsWhite) "白（先攻）" else "赤（後攻）"}",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ゲーム盤面（画面の大部分を使用）
            GameBoard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 16.dp),
                gameViewModel = gameViewModel
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 現在の状況表示
            if (gameViewModel.gameState.isWaitingForCpu) {
                Text(
                    text = "CPUが思考中...",
                    fontSize = 16.sp,
                    color = Color.Yellow
                )
            } else if (gameViewModel.gameState.gameMode == GameMode.VS_CPU && !gameViewModel.gameState.isGameOver) {
                val currentPlayerText = if (gameViewModel.gameState.currentPlayer == Player.WHITE) {
                    if (gameViewModel.gameState.playerIsWhite) "あなたのターン（白）" else "CPUのターン（白）"
                } else {
                    if (!gameViewModel.gameState.playerIsWhite) "あなたのターン（赤）" else "CPUのターン（赤）"
                }
                Text(
                    text = currentPlayerText,
                    fontSize = 14.sp,
                    color = Color.White
                )
            } else {
                Text(
                    text = "四目並べゲーム",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            // 勝利・引き分け表示
            if (gameViewModel.gameState.isGameOver) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White)
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
                        color = Color.Black,
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

                    // CPU対戦だった場合は続けるボタンを表示
                    if (gameViewModel.gameState.gameMode == GameMode.VS_CPU) {
                        Button(
                            onClick = { gameViewModel.startCpuGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(
                                "続けてCPU対戦",
                                color = Color.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                    else {
                        Button(
                            onClick = { gameViewModel.startSinglePlayerGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(
                                "続けて一人モード",
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

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val boardSize = kotlin.math.min(size.width, size.height)
                val cellSize = boardSize / 12f
                val col = (offset.x / cellSize).toInt()
                val row = (offset.y / cellSize).toInt()

                if (row in 0..11 && col in 0..11) {
                    gameViewModel.onCellClick(row, col)
                }
            }
        }
    ) {
        val boardSize = kotlin.math.min(size.width, size.height)
        val cellSize = boardSize / 12f

        // 背景色を描画（正方形領域のみ）
        drawRect(
            color = Color(0xFF0F3D20),
            size = Size(boardSize, boardSize)
        )

        // セルを描画
        for (row in 0..11) {
            for (col in 0..11) {
                val cellState = gameViewModel.gameState.board[row][col]
                val pos = Pair(row, col)

                // アニメーション中のY座標を取得。なければ本来の行位置を使う。
                val animatedY = blockAnimations[pos]?.value ?: row.toFloat()

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

                    drawRect(
                        color = color,
                        topLeft = Offset(col * cellSize, animatedY * cellSize),
                        size = Size(cellSize, cellSize)
                    )

                    // 勝利ラインに枠線を追加
                    if (isWinningCell) {
                        drawRect(
                            color = Color.Yellow,
                            topLeft = Offset(col * cellSize, row * cellSize), // 枠線はアニメーションさせない
                            size = Size(cellSize, cellSize),
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
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }

        // グリッド線を描画（薄い白色）
        val gridColor = AppColors.GRID_LINE

        // 縦線
        for (i in 0..12) {
            val x = i * cellSize
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, boardSize),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // 横線
        for (i in 0..12) {
            val y = i * cellSize
            val lineColor = if (i == 12) {
                AppColors.BOTTOM_LINE_HIGHLIGHT
            } else {
                gridColor
            }

            val strokeWidth = if (i == 12) {
                2.0.dp.toPx()
            } else {
                0.5.dp.toPx()
            }

            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(boardSize, y),
                strokeWidth = strokeWidth
            )
        }
    }
}

