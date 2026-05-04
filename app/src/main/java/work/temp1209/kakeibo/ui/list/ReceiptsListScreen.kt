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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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

/** 全期間表示（DB には空文字で渡す。画面状態ではこの定数を使う） */
const val RECEIPTS_LIST_PERIOD_ALL = "ALL"

@Composable
fun ReceiptsListScreen(
    contentPadding: PaddingValues,
    /** [RECEIPTS_LIST_PERIOD_ALL] または yyyy-MM */
    periodKey: String,
    /** 全期間から「月別」に戻すときの年月（yyyy-MM） */
    lastMonthKey: String,
    onPeriodChange: (String) -> Unit,
    loadReceiptRows: suspend (yearMonth: String) -> List<ReceiptListRow>,
    onOpenReceipt: (String) -> Unit,
    onOpenAddExpenseSheet: (() -> Unit)? = null,
) {
    var rows by remember { mutableStateOf<List<ReceiptListRow>>(emptyList()) }
    val selectedMonth: YearMonth? =
        if (periodKey == RECEIPTS_LIST_PERIOD_ALL) null else runCatching { YearMonth.parse(periodKey) }.getOrNull()
    var loading by remember(periodKey) { mutableStateOf(true) }

    val yearMonthArg = selectedMonth?.toString().orEmpty()

    LaunchedEffect(yearMonthArg) {
        loading = true
        try {
            rows = loadReceiptRows(yearMonthArg)
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onOpenAddExpenseSheet != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FloatingActionButton(onClick = onOpenAddExpenseSheet) {
                    Text("＋")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = periodKey != RECEIPTS_LIST_PERIOD_ALL,
                    onClick = {
                        val key = runCatching { YearMonth.parse(lastMonthKey).toString() }
                            .getOrElse { YearMonth.now().toString() }
                        onPeriodChange(key)
                    },
                    label = { Text("月別") },
                )
                FilterChip(
                    selected = periodKey == RECEIPTS_LIST_PERIOD_ALL,
                    onClick = { onPeriodChange(RECEIPTS_LIST_PERIOD_ALL) },
                    label = { Text("全期間") },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val m = selectedMonth ?: return@IconButton
                        onPeriodChange(m.minusMonths(1).toString())
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
                            onPeriodChange(next.toString())
                        }
                    },
                    enabled = selectedMonth != null && selectedMonth.isBefore(YearMonth.now()),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "翌月")
                }
            }
        }

        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("読み込み中…", modifier = Modifier.padding(top = 12.dp))
            }
            return@Column
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
