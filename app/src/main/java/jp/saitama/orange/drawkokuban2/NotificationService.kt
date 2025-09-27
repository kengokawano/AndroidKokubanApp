package jp.saitama.orange.drawkokuban2

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log

class NotificationService : Service() {
    companion object {
        const val CHANNEL_ID = "chalkboard_notification"
        const val NOTIFICATION_ID = 1001
        const val ACTION_OPEN_CHALKBOARD = "ACTION_OPEN_CHALKBOARD"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationService", "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("NotificationService", "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d("NotificationService", "Stopping service")
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.d("NotificationService", "Starting foreground notification")
                try {
                    val notification = createNotification()
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d("NotificationService", "Foreground notification started successfully")
                } catch (e: Exception) {
                    Log.e("NotificationService", "Failed to start foreground: ${e.message}")
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("NotificationService", "Creating notification channel")
            val channel = NotificationChannel(
                CHANNEL_ID,
                "黒板アプリ クイックアクセス",
                NotificationManager.IMPORTANCE_LOW  // LOWに変更（MINでは表示されない可能性）
            ).apply {
                description = "黒板アプリへの素早いアクセス"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationService", "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        // メインアプリを開くインテント
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        // サービス停止インテント
        val stopIntent = Intent(this, NotificationService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("NotificationService", "Creating notification")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("黒板太一2")
            .setContentText("タップで黒板を開く")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // LOWに変更
            .setShowWhen(false)
            .setAutoCancel(false)
            .addAction(
                R.drawable.ic_notification,
                "黒板",
                mainPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopPendingIntent
            )
            .build()
    }
}