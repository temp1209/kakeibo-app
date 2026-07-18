package work.temp1209.kakeibo.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.prefs.BudgetAggregateMode
import work.temp1209.kakeibo.data.prefs.BudgetSettings
import work.temp1209.kakeibo.data.prefs.BudgetStore
import work.temp1209.kakeibo.ui.common.TabScreenTitle
import work.temp1209.kakeibo.ui.format.formatYen
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.YearMonth

@Composable
fun AnalysisScreen(
    contentPadding: PaddingValues,
    repo: ReceiptRepository,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    var summary by remember { mutableStateOf<ReceiptRepository.MonthAnalysisSummary?>(null) }
    var rawItems by remember { mutableStateOf<List<ReceiptItemEntity>>(emptyList()) }
    var sortByAmount by remember { mutableStateOf(false) }
    var budgetSettings by remember { mutableStateOf(BudgetStore(context).current()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                budgetSettings = BudgetStore(context).current()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(yearMonth) {
        val ym = yearMonth.toString()
        budgetSettings = BudgetStore(context).current()
        summary = repo.monthAnalysisSummary(ym)
        rawItems = repo.listNonAdjustmentItemsInMonth(ym)
    }

    val sorted = remember(rawItems, sortByAmount) {
        repo.sortWasteCandidates(rawItems, byAmount = sortByAmount)
    }

    val s = summary
    val totalYen = ((s?.mandatoryYen) ?: 0L) + ((s?.discretionaryYen) ?: 0L)
    val mandatoryRatio = if (totalYen > 0 && s != null) s.mandatoryYen.toFloat() / totalYen.toFloat() else 0f
    val budgetProgress = s?.let {
        calculateBudgetProgress(it.mandatoryYen, it.discretionaryYen, budgetSettings)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TabScreenTitle("分析")
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { yearMonth = yearMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前月")
                }
                Text(
                    "${yearMonth.year}年${yearMonth.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                IconButton(
                    onClick = {
                        val n = yearMonth.plusMonths(1)
                        if (!n.isAfter(YearMonth.now())) yearMonth = n
                    },
                    enabled = yearMonth.isBefore(YearMonth.now()),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "翌月")
                }
            }
        }

        if (s == null) {
            item { Text("読み込み中…") }
        } else {
            item {
                Text("必須 vs 裁量（明細行・円ベース）", fontWeight = FontWeight.SemiBold)
            }
            item {
                Text(
                    "必須度は AI による目安です（境界: 50）。修正画面で調整できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    "必須寄り（≥50）: ${formatYen(s.mandatoryYen)}（${s.mandatoryLineCount}行）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Text(
                    "裁量寄り（<50）: ${formatYen(s.discretionaryYen)}（${s.discretionaryLineCount}行）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                when {
                    budgetProgress != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "予算 ${formatYen(budgetSettings.monthlyBudgetYen)}",
                                fontWeight = FontWeight.SemiBold,
                            )
                            SpendingStackedBar(
                                mandatoryFraction = budgetProgress.mandatoryFraction,
                                discretionaryFraction = budgetProgress.discretionaryFraction,
                                remainingFraction = budgetProgress.remainingFraction,
                            )
                            Text(
                                "使用 ${formatYen(budgetProgress.trackedYen)}（${budgetProgress.percent}%）",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (budgetSettings.aggregateMode == BudgetAggregateMode.DISCRETIONARY_ONLY) {
                                Text(
                                    "裁量支出のみを予算と比較しています。必須支出は参考表示です。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (budgetProgress.overBudgetYen > 0) {
                                Text(
                                    "予算超過 ${formatYen(budgetProgress.overBudgetYen)}",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    totalYen > 0 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SpendingStackedBar(
                                mandatoryFraction = mandatoryRatio,
                                discretionaryFraction = 1f - mandatoryRatio,
                                remainingFraction = 0f,
                            )
                            Text(
                                "支出合計 ${formatYen(totalYen)} / 必須比率 ${"%.1f".format(mandatoryRatio * 100)}%",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                "月次予算は設定タブから有効にできます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        Text(
                            "この月の対象明細はありません。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text("無駄遣い候補TOP", fontWeight = FontWeight.SemiBold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !sortByAmount,
                        onClick = { sortByAmount = false },
                        label = { Text("スコア順") },
                    )
                    FilterChip(
                        selected = sortByAmount,
                        onClick = { sortByAmount = true },
                        label = { Text("金額順") },
                    )
                }
            }

            itemsIndexed(sorted.take(30), key = { _, it -> it.itemId }) { idx, it ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text("${idx + 1}. ${it.itemName}", fontWeight = FontWeight.Medium)
                Text(
                    "${formatYen(it.lineTotalYen)} / 必須度 ${it.necessityScore} / 指標 ${(100 - it.necessityScore) * it.lineTotalYen}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SpendingStackedBar(
    mandatoryFraction: Float,
    discretionaryFraction: Float,
    remainingFraction: Float,
) {
    val mandatory = mandatoryFraction.coerceIn(0f, 1f)
    val discretionary = discretionaryFraction.coerceIn(0f, 1f - mandatory)
    val remaining = remainingFraction.coerceIn(0f, 1f - mandatory - discretionary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp)),
    ) {
        StackedBarSegment(mandatory, MaterialTheme.colorScheme.primary)
        StackedBarSegment(discretionary, MaterialTheme.colorScheme.tertiary)
        StackedBarSegment(remaining, MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StackedBarSegment(
    fraction: Float,
    color: Color,
) {
    if (fraction <= 0f) return
    Box(
        modifier = Modifier
            .weight(fraction)
            .fillMaxHeight()
            .background(color),
    )
}
