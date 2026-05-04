package work.temp1209.kakeibo.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.db.ReceiptListRow
import work.temp1209.kakeibo.data.domain.NecessityUtils
import work.temp1209.kakeibo.ui.format.formatIsoInstant
import work.temp1209.kakeibo.ui.format.formatYen
import java.time.YearMonth

@Composable
fun ReceiptsListScreen(
    contentPadding: PaddingValues,
    loadReceiptRows: suspend (yearMonth: String) -> List<ReceiptListRow>,
    onOpenReceipt: (String) -> Unit,
) {
    var rows by remember { mutableStateOf<List<ReceiptListRow>>(emptyList()) }
    var selectedMonth by remember { mutableStateOf<YearMonth?>(YearMonth.now()) }

    val yearMonthArg = selectedMonth?.toString().orEmpty()

    LaunchedEffect(yearMonthArg) {
        rows = loadReceiptRows(yearMonthArg)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedMonth == null,
                onClick = { selectedMonth = null },
                label = { Text("全期間") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val m = selectedMonth ?: YearMonth.now()
                        selectedMonth = m.minusMonths(1)
                    },
                    enabled = selectedMonth != null,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前月")
                }
                Text(
                    text = selectedMonth?.let { "${it.year}年${it.monthValue}月" } ?: "全期間表示",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = {
                        val m = selectedMonth ?: return@IconButton
                        val next = m.plusMonths(1)
                        if (!next.isAfter(YearMonth.now())) {
                            selectedMonth = next
                        }
                    },
                    enabled = selectedMonth != null && selectedMonth!!.isBefore(YearMonth.now()),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "翌月")
                }
            }
        }

        if (rows.isEmpty()) {
            Text("該当するレシートはありません。")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.receipt.receiptId }) { row ->
                val r = row.receipt
                val badge = NecessityUtils.badgeLabel(row.weightedNecessity)
                val mandatoryHeavy = (row.weightedNecessity ?: 0.0) >= 50.0
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = { onOpenReceipt(r.receiptId) },
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatIsoInstant(r.receiptDatetime ?: r.capturedAt),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (mandatoryHeavy) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                            ) {
                                Text(
                                    text = badge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Text(
                            text = r.merchantName?.ifBlank { "（店名なし）" } ?: "（店名なし）",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "合計 ${formatYen(r.totalAmountYen)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (r.needsReview == 1) {
                            Text(
                                text = "要確認",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
