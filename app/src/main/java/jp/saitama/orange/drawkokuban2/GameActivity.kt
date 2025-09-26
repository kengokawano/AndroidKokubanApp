package jp.saitama.orange.drawkokuban2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.saitama.orange.drawkokuban2.ui.theme.MyApplicationTheme

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
            .background(Color(0xFF0B2E1A))
    ) {
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

            // 勝利表示
            if (gameViewModel.gameState.isGameOver && gameViewModel.gameState.winner != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Text(
                        text = when (gameViewModel.gameState.winner) {
                            Player.WHITE -> "白の勝利！"
                            Player.RED -> "赤の勝利！"
                            else -> ""
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { gameViewModel.resetGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text(
                        "新しいゲーム",
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun GameBoard(modifier: Modifier = Modifier, gameViewModel: GameViewModel) {
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
                if (cellState != CellState.EMPTY) {
                    val color = when (cellState) {
                        CellState.WHITE -> Color.White
                        CellState.RED -> Color.Red
                        else -> Color.Transparent
                    }

                    drawRect(
                        color = color,
                        topLeft = Offset(col * cellSize, row * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                }
            }
        }

        // グリッド線を描画（薄い白色）
        val gridColor = Color(0x40FFFFFF)

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
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(boardSize, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }
    }
}