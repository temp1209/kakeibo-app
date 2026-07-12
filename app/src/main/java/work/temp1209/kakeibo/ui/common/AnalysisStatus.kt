package work.temp1209.kakeibo.ui.common

import work.temp1209.kakeibo.data.db.ReceiptEntity

enum class AnalysisStatusKind {
    None,
    Pending,
    Running,
    Failed,
    NeedsReview,
}

data class AnalysisStatusDisplay(
    val kind: AnalysisStatusKind,
    val label: String,
    val showBadge: Boolean,
) {
    companion object {
        val None = AnalysisStatusDisplay(AnalysisStatusKind.None, "", false)
    }
}

fun resolveAnalysisStatusDisplay(
    analysisStatus: String,
    needsReview: Int,
): AnalysisStatusDisplay {
    return when {
        analysisStatus == "FAILED" ->
            AnalysisStatusDisplay(AnalysisStatusKind.Failed, "解析失敗", true)
        analysisStatus == "NEEDS_REVIEW" || needsReview == 1 ->
            AnalysisStatusDisplay(AnalysisStatusKind.NeedsReview, "要確認", true)
        analysisStatus == "RUNNING" ->
            AnalysisStatusDisplay(AnalysisStatusKind.Running, "解析中", true)
        analysisStatus == "PENDING" ->
            AnalysisStatusDisplay(AnalysisStatusKind.Pending, "解析待ち", true)
        else -> AnalysisStatusDisplay.None
    }
}

fun ReceiptEntity.analysisStatusDisplay(): AnalysisStatusDisplay =
    resolveAnalysisStatusDisplay(analysisStatus, needsReview)
