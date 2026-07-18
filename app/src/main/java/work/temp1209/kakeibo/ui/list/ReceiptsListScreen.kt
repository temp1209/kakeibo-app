package work.temp1209.kakeibo.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.AnnotatedString
import work.temp1209.kakeibo.ui.common.ReceiptAnalysisStatusBadge
import work.temp1209.kakeibo.ui.common.analysisErrorSummary
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.db.ReceiptListRow
import work.temp1209.kakeibo.data.domain.NecessityUtils
import work.temp1209.kakeibo.ui.common.TabScreenTitle
import work.temp1209.kakeibo.ui.format.formatIsoInstant
import work.temp1209.kakeibo.ui.format.formatYen
import java.time.YearMonth

/** 全期間表示（DB には空文字で渡す。画面状態ではこの定数を使う） */
const val RECEIPTS_LIST_PERIOD_ALL = "ALL"

/** 一覧左カラム用: 先頭商品名。2 行以上なら末尾に「など」（「など」はやや小さい字） */
@Composable
private fun buildFirstItemPreviewAnnotated(
    firstItemName: String?,
    nonAdjustmentItemCount: Int,
): AnnotatedString? {
    val name = firstItemName?.trim().orEmpty()
    if (name.isEmpty()) return null
    val base = MaterialTheme.typography.bodyLarge
    val etcSize = MaterialTheme.typography.bodySmall.fontSize
    return buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontSize = base.fontSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = base.letterSpacing,
            ),
        ) {
            append(name)
        }
        if (nonAdjustmentItemCount >= 2) {
            append(" ")
            withStyle(SpanStyle(fontSize = etcSize, fontWeight = FontWeight.Normal)) {
                append("など")
            }
        }
    }
}

/** 右下 FAB（56dp 相当）＋マージンで一覧末尾が隠れないように確保する余白 */
private val ReceiptsListFabBottomClearance = 88.dp

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

    val fabBottomClearance =
        if (onOpenAddExpenseSheet != null) ReceiptsListFabBottomClearance else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TabScreenTitle("一覧", modifier = Modifier.fillMaxWidth())

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

            when {
                loading -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = fabBottomClearance),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text("読み込み中…", modifier = Modifier.padding(top = 12.dp))
                    }
                }

                rows.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = fabBottomClearance),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("該当するレシートはありません。")
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = fabBottomClearance),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rows, key = { it.receipt.receiptId }) { row ->
                            val r = row.receipt
                            val badge = NecessityUtils.badgeLabel(row.weightedNecessity)
                            val mandatoryHeavy = (row.weightedNecessity ?: 0.0) >= 50.0
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                onClick = { onOpenReceipt(r.receiptId) },
                            ) {
                                val kindLabel =
                                    when (r.inputKind) {
                                        "MANUAL_NO_RECEIPT" -> "手入力"
                                        "EVIDENCE_IMAGE" -> "画像"
                                        else -> null
                                    }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = formatIsoInstant(r.receiptDatetime ?: r.capturedAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        val itemPreviewAnnotated =
                                            buildFirstItemPreviewAnnotated(
                                                firstItemName = row.firstItemName,
                                                nonAdjustmentItemCount = row.nonAdjustmentItemCount ?: 0,
                                            )
                                        if (itemPreviewAnnotated != null) {
                                            Text(
                                                text = itemPreviewAnnotated,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        } else {
                                            Text(
                                                text = "（明細なし）",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = r.merchantName?.ifBlank { "（店名なし）" } ?: "（店名なし）",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "合計 ${formatYen(r.totalAmountYen)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (r.analysisStatus == "FAILED") {
                                            analysisErrorSummary(r.analysisErrorMessage)?.let { reason ->
                                                Text(
                                                    text = "理由: $reason",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
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
                                        if (kindLabel != null) {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                            ) {
                                                Text(
                                                    text = kindLabel,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                        ReceiptAnalysisStatusBadge(receipt = r, small = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (onOpenAddExpenseSheet != null) {
            FloatingActionButton(
                onClick = onOpenAddExpenseSheet,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("＋")
            }
        }
    }
}
