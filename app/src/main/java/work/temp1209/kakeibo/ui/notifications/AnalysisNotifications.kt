package work.temp1209.kakeibo.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import work.temp1209.kakeibo.MainActivity
import work.temp1209.kakeibo.R

object AnalysisNotifications {
    const val CHANNEL_ID = "analysis"
    const val EXTRA_RECEIPT_ID = "extra_receipt_id"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "解析通知",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "レシート解析の完了/失敗を通知します"
            }
        )
    }

    fun notifyDone(context: Context, receiptId: String) {
        notify(context, receiptId, title = "解析完了", text = "レシートの解析が完了しました", notificationId = receiptId.hashCode())
    }

    fun notifyFailed(context: Context, receiptId: String) {
        notify(context, receiptId, title = "解析失敗", text = "レシートの解析に失敗しました", notificationId = receiptId.hashCode())
    }

    private fun notify(context: Context, receiptId: String, title: String, text: String, notificationId: Int) {
        ensureChannel(context)

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_RECEIPT_ID, receiptId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, n)
        }
    }
}

