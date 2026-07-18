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
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

object BudgetNotifications {
    const val CHANNEL_ID = "budget_progress"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "月次予算",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "月次予算の定期確認と使用割合の通知です"
            },
        )
    }

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    fun notifyProgress(
        context: Context,
        trackedYen: Long,
        budgetYen: Long,
        percent: Int,
        overBudgetYen: Long,
        monthEnd: Boolean,
    ): Boolean {
        ensureChannel(context)
        if (!canPost(context)) return false

        val title = when {
            overBudgetYen > 0 -> "月次予算を超えました"
            percent >= 100 -> "月次予算に達しました"
            monthEnd -> "今月の支出結果"
            else -> "月次予算の進捗"
        }
        val text = if (overBudgetYen > 0) {
            "予算超過 ${formatYen(overBudgetYen)}（使用 ${formatYen(trackedYen)}）"
        } else {
            "予算 ${formatYen(budgetYen)} のうち ${formatYen(trackedYen)}（$percent%）"
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val notificationId = "budget-${YearMonth.now()}".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Lint は canPost() 越しの権限チェックを追えないため、notify 直前でも再確認する
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    private fun formatYen(value: Long): String =
        "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(value)}"
}
