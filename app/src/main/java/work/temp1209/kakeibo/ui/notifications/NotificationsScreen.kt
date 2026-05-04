package work.temp1209.kakeibo.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.db.ReceiptEntity

@Composable
fun NotificationsScreen(
    contentPadding: PaddingValues,
    repo: ReceiptRepository,
    onOpenReceipt: (String) -> Unit,
    onOpenReceiptReview: (String) -> Unit = onOpenReceipt,
) {
    var queueCount by remember { mutableStateOf(0) }
    var latestError by remember { mutableStateOf<String?>(null) }
    var needsReview by remember { mutableStateOf<List<ReceiptEntity>>(emptyList()) }
    var recent by remember { mutableStateOf<List<ReceiptEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        queueCount = repo.queueInFlightCount()
        latestError = repo.latestQueueErrorOrNull()
        needsReview = repo.listNeedsReview(limit = 30)
        recent = repo.listRecentAnalyzed(limit = 30)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("解析キュー: ${queueCount}件")
            Text("最終エラー: ${latestError ?: "なし"}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text("要確認（needsReview）: ${needsReview.size}件", style = MaterialTheme.typography.titleSmall)
        }
        if (needsReview.isEmpty()) {
            item { Text("該当するレシートはありません。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(needsReview, key = { it.receiptId }) { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = { onOpenReceiptReview(r.receiptId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("status: ${r.analysisStatus} / error: ${r.analysisErrorMessage ?: "-"}")
                        Text("merchant: ${r.merchantName ?: "-"} / total: ${r.totalAmountYen ?: "-"}")
                        Text("receiptDatetime: ${r.receiptDatetime ?: "-"}")
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("解析結果（最近）: ${recent.size}件", style = MaterialTheme.typography.titleSmall)
        }
        if (recent.isEmpty()) {
            item { Text("まだ解析結果がありません。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(recent, key = { it.receiptId }) { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = { onOpenReceipt(r.receiptId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("status: ${r.analysisStatus}")
                            if (r.needsReview == 1) Text("要確認", color = MaterialTheme.colorScheme.error)
                        }
                        Text("merchant: ${r.merchantName ?: "-"} / total: ${r.totalAmountYen ?: "-"}")
                        Text("receiptDatetime: ${r.receiptDatetime ?: "-"}")
                    }
                }
            }
        }
    }
}
