package jp.saitama.orange.drawkokuban2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.saitama.orange.drawkokuban2.ui.theme.MyApplicationTheme

class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                GameScreen(
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun GameScreen(onClose: () -> Unit) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // ゲーム盤面（画面の大部分を使用）
            GameBoard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "四目並べゲーム",
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun GameBoard(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val boardSize = size.minDimension
        val cellSize = boardSize / 12f

        // 背景色を描画（正方形領域のみ）
        drawRect(
            color = Color(0xFF0F3D20),
            size = androidx.compose.ui.geometry.Size(boardSize, boardSize)
        )

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