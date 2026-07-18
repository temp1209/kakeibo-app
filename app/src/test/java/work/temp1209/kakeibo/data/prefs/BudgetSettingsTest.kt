package work.temp1209.kakeibo.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetSettingsTest {

    @Test
    fun usable_requiresEnabledAndPositiveAmount() {
        assertFalse(BudgetSettings().isUsable)
        assertFalse(BudgetSettings(enabled = true, monthlyBudgetYen = 0).isUsable)
        assertFalse(BudgetSettings(enabled = false, monthlyBudgetYen = 80_000).isUsable)
        assertTrue(BudgetSettings(enabled = true, monthlyBudgetYen = 80_000).isUsable)
    }

    @Test
    fun aggregateMode_fallsBackToTotal() {
        assertEquals(BudgetAggregateMode.TOTAL, BudgetAggregateMode.fromStored(null))
        assertEquals(BudgetAggregateMode.TOTAL, BudgetAggregateMode.fromStored("UNKNOWN"))
        assertEquals(
            BudgetAggregateMode.DISCRETIONARY_ONLY,
            BudgetAggregateMode.fromStored("DISCRETIONARY_ONLY"),
        )
    }
}
