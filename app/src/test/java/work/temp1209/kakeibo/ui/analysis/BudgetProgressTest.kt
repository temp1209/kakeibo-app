package work.temp1209.kakeibo.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import work.temp1209.kakeibo.data.prefs.BudgetAggregateMode
import work.temp1209.kakeibo.data.prefs.BudgetSettings

class BudgetProgressTest {

    @Test
    fun disabledBudget_returnsNull() {
        assertNull(calculateBudgetProgress(40_000, 20_000, BudgetSettings()))
    }

    @Test
    fun totalMode_stacksMandatoryAndDiscretionary() {
        val progress = calculateBudgetProgress(
            mandatoryYen = 40_000,
            discretionaryYen = 20_000,
            settings = BudgetSettings(enabled = true, monthlyBudgetYen = 80_000),
        )!!

        assertEquals(60_000, progress.trackedYen)
        assertEquals(0.5f, progress.mandatoryFraction)
        assertEquals(0.25f, progress.discretionaryFraction)
        assertEquals(0.25f, progress.remainingFraction)
        assertEquals(75, progress.percent)
        assertEquals(0, progress.overBudgetYen)
    }

    @Test
    fun discretionaryMode_tracksOnlyDiscretionarySpend() {
        val progress = calculateBudgetProgress(
            mandatoryYen = 90_000,
            discretionaryYen = 20_000,
            settings = BudgetSettings(
                enabled = true,
                monthlyBudgetYen = 50_000,
                aggregateMode = BudgetAggregateMode.DISCRETIONARY_ONLY,
            ),
        )!!

        assertEquals(20_000, progress.trackedYen)
        assertEquals(0f, progress.mandatoryFraction)
        assertEquals(0.4f, progress.discretionaryFraction)
        assertEquals(0.6f, progress.remainingFraction)
        assertEquals(40, progress.percent)
    }

    @Test
    fun overBudget_fillsBarAndReportsExcess() {
        val progress = calculateBudgetProgress(
            mandatoryYen = 70_000,
            discretionaryYen = 30_000,
            settings = BudgetSettings(enabled = true, monthlyBudgetYen = 80_000),
        )!!

        assertEquals(1f, progress.mandatoryFraction + progress.discretionaryFraction)
        assertEquals(0f, progress.remainingFraction)
        assertEquals(125, progress.percent)
        assertEquals(20_000, progress.overBudgetYen)
    }
}
