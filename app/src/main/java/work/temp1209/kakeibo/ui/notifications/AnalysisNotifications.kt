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
    const val EXTRA_RECEIPT_ID = "extra_receipt_id"

    /** 解析が完了し、確定扱いで一覧に載ったとき */
    const val CHANNEL_ID_COMPLETED = "analysis_completed"

    /** 解析はできたが低信頼・必須欠落などでユーザー確認が必要なとき */
    const val CHANNEL_ID_NEEDS_REVIEW = "analysis_needs_review"

    /** API/ネットワーク/パース等で解析自体が失敗したとき */
    const val CHANNEL_ID_FAILED = "analysis_failed"

    @Deprecated("チャネルを種別ごとに分割した。互換のため残す", ReplaceWith("CHANNEL_ID_COMPLETED"))
    const val CHANNEL_ID = CHANNEL_ID_COMPLETED

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun createIfMissing(
            id: String,
            name: String,
            description: String,
            importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        ) {
            if (mgr.getNotificationChannel(id) != null) return
            mgr.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    this.description = description
                },
            )
        }

        createIfMissing(
            CHANNEL_ID_COMPLETED,
            "解析完了",
            "解析が完了し、一覧に反映されたときの通知です",
        )
        createIfMissing(
            CHANNEL_ID_NEEDS_REVIEW,
            "要確認",
            "低信頼や必須項目の不足などで、内容の確認が必要なときの通知です",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        createIfMissing(
            CHANNEL_ID_FAILED,
            "解析失敗",
            "通信エラーや応答の解釈失敗などで、解析処理が完了できなかったときの通知です",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
    }

    fun notifyDone(context: Context, receiptId: String) {
        notify(
            context = context,
            channelId = CHANNEL_ID_COMPLETED,
            receiptId = receiptId,
            title = "解析完了",
            text = "レシートの解析が完了しました",
            notificationId = receiptId.hashCode(),
        )
    }

    fun notifyNeedsReview(context: Context, receiptId: String) {
        notify(
            context = context,
            channelId = CHANNEL_ID_NEEDS_REVIEW,
            receiptId = receiptId,
            title = "要確認",
            text = "解析結果の確認が必要です",
            notificationId = receiptId.hashCode(),
        )
    }

    fun notifyFailed(context: Context, receiptId: String) {
        notify(
            context = context,
            channelId = CHANNEL_ID_FAILED,
            receiptId = receiptId,
            title = "解析失敗",
            text = "レシートの解析に失敗しました",
            notificationId = receiptId.hashCode(),
        )
    }

    private fun notify(
        context: Context,
        channelId: String,
        receiptId: String,
        title: String,
        text: String,
        notificationId: Int,
    ) {
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

        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
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
