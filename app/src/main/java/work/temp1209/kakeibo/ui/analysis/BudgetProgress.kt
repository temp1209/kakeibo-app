package work.temp1209.kakeibo.ui.analysis

import work.temp1209.kakeibo.data.prefs.BudgetAggregateMode
import work.temp1209.kakeibo.data.prefs.BudgetSettings

data class BudgetProgress(
    val trackedYen: Long,
    val mandatoryFraction: Float,
    val discretionaryFraction: Float,
    val remainingFraction: Float,
    val percent: Int,
    val overBudgetYen: Long,
)

fun calculateBudgetProgress(
    mandatoryYen: Long,
    discretionaryYen: Long,
    settings: BudgetSettings,
): BudgetProgress? {
    if (!settings.isUsable) return null

    val budget = settings.monthlyBudgetYen
    val tracked = when (settings.aggregateMode) {
        BudgetAggregateMode.TOTAL -> mandatoryYen + discretionaryYen
        BudgetAggregateMode.DISCRETIONARY_ONLY -> discretionaryYen
    }.coerceAtLeast(0)

    val mandatoryFraction = when (settings.aggregateMode) {
        BudgetAggregateMode.TOTAL -> (mandatoryYen.coerceAtLeast(0).toDouble() / budget).coerceIn(0.0, 1.0)
        BudgetAggregateMode.DISCRETIONARY_ONLY -> 0.0
    }
    val discretionaryCapacity = 1.0 - mandatoryFraction
    val discretionaryFraction =
        (discretionaryYen.coerceAtLeast(0).toDouble() / budget).coerceIn(0.0, discretionaryCapacity)
    val remainingFraction = (1.0 - mandatoryFraction - discretionaryFraction).coerceAtLeast(0.0)

    return BudgetProgress(
        trackedYen = tracked,
        mandatoryFraction = mandatoryFraction.toFloat(),
        discretionaryFraction = discretionaryFraction.toFloat(),
        remainingFraction = remainingFraction.toFloat(),
        percent = ((tracked.toDouble() / budget) * 100).toInt().coerceAtLeast(0),
        overBudgetYen = (tracked - budget).coerceAtLeast(0),
    )
}
