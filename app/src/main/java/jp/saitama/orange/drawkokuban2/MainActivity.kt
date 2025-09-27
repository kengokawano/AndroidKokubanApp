package jp.saitama.orange.drawkokuban2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import jp.saitama.orange.drawkokuban2.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Activity起動時に必ず設定をチェックして通知サービスを開始
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val quickAccessEnabled = prefs.getBoolean("quick_access_notification", false)

        if (quickAccessEnabled) {
            // 設定がONなら強制的に通知サービスを開始
            NotificationService.startService(this)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
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