package work.temp1209.kakeibo.data.domain

import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import kotlin.math.roundToInt

object NecessityUtils {
    /** 要件: 調整行除外の金額加重平均 necessityScore（0〜100）。明細が無いか金額0のみなら null。 */
    fun weightedAverageScore(items: List<ReceiptItemEntity>): Double? {
        val rows = items.filter { it.isAdjustment == 0 }
        var sumW = 0.0
        var sumY = 0.0
        for (it in rows) {
            val y = it.lineTotalYen.toDouble()
            if (y <= 0) continue
            sumW += y * it.necessityScore
            sumY += y
        }
        if (sumY <= 0) return null
        return sumW / sumY
    }

    fun weightedAverageInt(items: List<ReceiptItemEntity>): Int? =
        weightedAverageScore(items)?.roundToInt()?.coerceIn(0, 100)

    /** 境界50: ≥50 必須寄り、＜50 裁量寄り */
    fun badgeLabel(weighted: Double?): String {
        val v = weighted ?: return "—"
        return if (v >= 50.0) "必須寄り" else "裁量寄り"
    }

    const val SCORE_STEP = 5

    fun snapScore(score: Int): Int =
        ((score.coerceIn(0, 100) + SCORE_STEP / 2) / SCORE_STEP) * SCORE_STEP
}
