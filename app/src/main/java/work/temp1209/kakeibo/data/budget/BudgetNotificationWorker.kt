package work.temp1209.kakeibo.data.budget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.prefs.BudgetAggregateMode
import work.temp1209.kakeibo.data.prefs.BudgetNotificationStateStore
import work.temp1209.kakeibo.data.prefs.BudgetStore
import work.temp1209.kakeibo.data.prefs.NotificationPrefs
import work.temp1209.kakeibo.ui.notifications.BudgetNotifications
import java.time.LocalDate
import java.time.YearMonth

class BudgetNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val budget = BudgetStore(applicationContext).current()
        val notificationPrefs = NotificationPrefs(applicationContext)
        if (!budget.isUsable || !notificationPrefs.isAnyBudgetNotificationEnabled()) {
            return Result.success()
        }
        // 権限不足など投稿不可の間は集計せず、許可後の次回実行でキャッチアップする
        if (!BudgetNotifications.canPost(applicationContext)) {
            return Result.success()
        }

        val today = LocalDate.now()
        val yearMonth = YearMonth.from(today)
        val summary = ReceiptRepository(applicationContext).monthAnalysisSummary(yearMonth.toString())
        val trackedYen = when (budget.aggregateMode) {
            BudgetAggregateMode.TOTAL -> summary.mandatoryYen + summary.discretionaryYen
            BudgetAggregateMode.DISCRETIONARY_ONLY -> summary.discretionaryYen
        }
        val stateStore = BudgetNotificationStateStore(applicationContext)
        val dueReasons = BudgetNotificationPolicy.dueReasons(
            date = today,
            trackedYen = trackedYen,
            budgetYen = budget.monthlyBudgetYen,
            alreadySent = stateStore.sentReasons(yearMonth),
        ).filterTo(linkedSetOf()) { reason ->
            when (reason) {
                BudgetNotificationPolicy.REASON_DAY_10,
                BudgetNotificationPolicy.REASON_DAY_20,
                BudgetNotificationPolicy.REASON_MONTH_END
                -> notificationPrefs.isBudgetProgressEnabled()
                BudgetNotificationPolicy.REASON_THRESHOLD_80 ->
                    notificationPrefs.isBudgetThreshold80Enabled()
                BudgetNotificationPolicy.REASON_THRESHOLD_100 ->
                    notificationPrefs.isBudgetThreshold100Enabled()
                else -> false
            }
        }
        if (dueReasons.isEmpty()) return Result.success()

        val percent = ((trackedYen.toDouble() / budget.monthlyBudgetYen) * 100)
            .toInt()
            .coerceAtLeast(0)
        val posted = BudgetNotifications.notifyProgress(
            context = applicationContext,
            trackedYen = trackedYen,
            budgetYen = budget.monthlyBudgetYen,
            percent = percent,
            overBudgetYen = (trackedYen - budget.monthlyBudgetYen).coerceAtLeast(0),
            monthEnd = BudgetNotificationPolicy.REASON_MONTH_END in dueReasons,
        )
        if (posted) {
            stateStore.markSent(yearMonth, dueReasons)
        }
        return Result.success()
    }
}
