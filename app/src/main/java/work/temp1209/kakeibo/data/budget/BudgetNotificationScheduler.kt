package work.temp1209.kakeibo.data.budget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BudgetNotificationScheduler {
    private const val UNIQUE_WORK_NAME = "budget-progress-daily-check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BudgetNotificationWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }
}
