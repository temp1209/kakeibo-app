package work.temp1209.kakeibo.data.budget

import java.time.LocalDate

object BudgetNotificationPolicy {
    const val REASON_DAY_10 = "DAY_10"
    const val REASON_DAY_20 = "DAY_20"
    const val REASON_MONTH_END = "MONTH_END"
    const val REASON_THRESHOLD_80 = "THRESHOLD_80"
    const val REASON_THRESHOLD_100 = "THRESHOLD_100"

    fun dueReasons(
        date: LocalDate,
        trackedYen: Long,
        budgetYen: Long,
        alreadySent: Set<String>,
    ): Set<String> {
        if (budgetYen <= 0) return emptySet()
        val due = linkedSetOf<String>()

        when {
            date.dayOfMonth == 10 -> due += REASON_DAY_10
            date.dayOfMonth == 20 -> due += REASON_DAY_20
            date.dayOfMonth == date.lengthOfMonth() -> due += REASON_MONTH_END
        }

        val percent = trackedYen.coerceAtLeast(0).toDouble() / budgetYen * 100
        when {
            percent >= 100 -> {
                due += REASON_THRESHOLD_80
                due += REASON_THRESHOLD_100
            }
            percent >= 80 -> due += REASON_THRESHOLD_80
        }

        return due - alreadySent
    }
}
