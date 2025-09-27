package jp.saitama.orange.drawkokuban2

import android.app.Application
import android.util.Log

class ChalkboardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("ChalkboardApplication", "Application started")

        // アプリ起動時に必ず通知設定をチェックして、ONなら即座に通知表示
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val quickAccessEnabled = prefs.getBoolean("quick_access_notification", false)

        Log.d("ChalkboardApplication", "Quick access notification enabled: $quickAccessEnabled")

        if (quickAccessEnabled) {
            Log.d("ChalkboardApplication", "Force starting notification service on app startup")
            // 起動時は必ず通知サービスを開始
            NotificationService.startService(this)
        }
    }
}