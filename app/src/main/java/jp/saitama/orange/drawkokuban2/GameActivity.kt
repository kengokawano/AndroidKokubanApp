package jp.saitama.orange.drawkokuban2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                GameScreen()
            }
        }
    }
}

@Composable
fun GameScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B2E1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "黒板大将",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "四目並べゲーム",
                fontSize = 20.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "12×12の盤面",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}