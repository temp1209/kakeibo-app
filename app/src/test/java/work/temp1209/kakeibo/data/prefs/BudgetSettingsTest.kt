package work.temp1209.kakeibo.data.prefs

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
}
