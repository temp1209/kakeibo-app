package work.temp1209.kakeibo.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.db.ReceiptEntity

@Composable
fun AnalysisStatusBadge(
    display: AnalysisStatusDisplay,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    if (!display.showBadge) return

    val (containerColor, contentColor) = when (display.kind) {
        AnalysisStatusKind.Pending ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        AnalysisStatusKind.Running ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        AnalysisStatusKind.Failed, AnalysisStatusKind.NeedsReview ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AnalysisStatusKind.None -> return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Text(
            text = display.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = if (small) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = contentColor,
        )
    }
}

@Composable
fun ReceiptAnalysisStatusBadge(
    receipt: ReceiptEntity,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    AnalysisStatusBadge(
        display = receipt.analysisStatusDisplay(),
        modifier = modifier,
        small = small,
    )
}
