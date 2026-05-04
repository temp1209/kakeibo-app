package work.temp1209.kakeibo.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.ui.format.formatYen
import java.time.YearMonth

@Composable
fun AnalysisScreen(
    contentPadding: PaddingValues,
    repo: ReceiptRepository,
) {
    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    var summary by remember { mutableStateOf<ReceiptRepository.MonthAnalysisSummary?>(null) }
    var rawItems by remember { mutableStateOf<List<ReceiptItemEntity>>(emptyList()) }
    var sortByAmount by remember { mutableStateOf(false) }

    LaunchedEffect(yearMonth) {
        val ym = yearMonth.toString()
        summary = repo.monthAnalysisSummary(ym)
        rawItems = repo.listNonAdjustmentItemsInMonth(ym)
    }

    val sorted = remember(rawItems, sortByAmount) {
        repo.sortWasteCandidates(rawItems, byAmount = sortByAmount)
    }

    val s = summary
    val totalYen = ((s?.mandatoryYen) ?: 0L) + ((s?.discretionaryYen) ?: 0L)
    val mandatoryRatio = if (totalYen > 0 && s != null) s.mandatoryYen.toFloat() / totalYen.toFloat() else 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("分析", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                if (totalYen > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { mandatoryRatio },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "必須比率（金額）: ${"%.1f".format(mandatoryRatio * 100)}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Text("この月の対象明細はありません。", style = MaterialTheme.typography.bodyMedium)
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
