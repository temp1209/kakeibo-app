package work.temp1209.kakeibo.data.prefs

import android.content.Context

enum class BudgetAggregateMode {
    TOTAL,
    DISCRETIONARY_ONLY,
    ;

    companion object {
        fun fromStored(value: String?): BudgetAggregateMode =
            entries.firstOrNull { it.name == value } ?: TOTAL
    }
}

data class BudgetSettings(
    val enabled: Boolean = false,
    val monthlyBudgetYen: Long = 0,
    val aggregateMode: BudgetAggregateMode = BudgetAggregateMode.TOTAL,
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
            aggregateMode = BudgetAggregateMode.fromStored(
                prefs.getString(KEY_AGGREGATE_MODE, null),
            ),
        )

    fun save(settings: BudgetSettings) {
        require(settings.monthlyBudgetYen >= 0) { "月次予算は0円以上である必要があります" }
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putLong(KEY_MONTHLY_BUDGET_YEN, settings.monthlyBudgetYen)
            .putString(KEY_AGGREGATE_MODE, settings.aggregateMode.name)
            .apply()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "budget_prefs"
        private const val KEY_ENABLED = "budget_enabled"
        private const val KEY_MONTHLY_BUDGET_YEN = "monthly_budget_yen"
        private const val KEY_AGGREGATE_MODE = "budget_aggregate_mode"
    }
}
