package work.temp1209.kakeibo.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetNotificationPolicyTest {

    @Test
    fun day10_andThreshold80_areDueTogether() {
        val due = BudgetNotificationPolicy.dueReasons(
            date = LocalDate.of(2026, 7, 10),
            trackedYen = 80_000,
            budgetYen = 100_000,
            alreadySent = emptySet(),
        )

        assertEquals(
            setOf(
                BudgetNotificationPolicy.REASON_DAY_10,
                BudgetNotificationPolicy.REASON_THRESHOLD_80,
            ),
            due,
        )
    }

    @Test
    fun threshold100_marksBothThresholds_withoutRepeating80() {
        val due = BudgetNotificationPolicy.dueReasons(
            date = LocalDate.of(2026, 7, 23),
            trackedYen = 120_000,
            budgetYen = 100_000,
            alreadySent = setOf(BudgetNotificationPolicy.REASON_THRESHOLD_80),
        )

        assertEquals(setOf(BudgetNotificationPolicy.REASON_THRESHOLD_100), due)
    }

    @Test
    fun lastDay_isDetectedForVariableMonthLength() {
        val due = BudgetNotificationPolicy.dueReasons(
            date = LocalDate.of(2026, 2, 28),
            trackedYen = 10_000,
            budgetYen = 100_000,
            alreadySent = emptySet(),
        )

        assertEquals(setOf(BudgetNotificationPolicy.REASON_MONTH_END), due)
    }

    @Test
    fun sentReasons_areNotReturnedAgain() {
        val due = BudgetNotificationPolicy.dueReasons(
            date = LocalDate.of(2026, 7, 20),
            trackedYen = 20_000,
            budgetYen = 100_000,
            alreadySent = setOf(BudgetNotificationPolicy.REASON_DAY_20),
        )

        assertTrue(due.isEmpty())
    }
}
