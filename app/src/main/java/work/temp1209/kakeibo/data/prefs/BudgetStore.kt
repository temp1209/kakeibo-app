package work.temp1209.kakeibo.data.prefs

import android.content.Context

data class BudgetSettings(
    val enabled: Boolean = false,
    val monthlyBudgetYen: Long = 0,
) {
    val isUsable: Boolean
        get() = enabled && monthlyBudgetYen > 0
}

class BudgetStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): BudgetSettings =
        BudgetSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            monthlyBudgetYen = prefs.getLong(KEY_MONTHLY_BUDGET_YEN, 0),
        )

    fun save(settings: BudgetSettings) {
        require(settings.monthlyBudgetYen >= 0) { "月次予算は0円以上である必要があります" }
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putLong(KEY_MONTHLY_BUDGET_YEN, settings.monthlyBudgetYen)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "budget_prefs"
        private const val KEY_ENABLED = "budget_enabled"
        private const val KEY_MONTHLY_BUDGET_YEN = "monthly_budget_yen"
    }
}
