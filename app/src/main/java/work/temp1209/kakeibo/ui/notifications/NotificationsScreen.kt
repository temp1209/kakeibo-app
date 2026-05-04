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
import androidx.compose.foundation.layout.Box
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.OptIn
import kotlinx.coroutines.launch
import work.temp1209.kakeibo.ui.common.TabScreenTitle
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.db.ReceiptEntity

private data class NotificationTabSnapshot(
    val queueCount: Int,
    val latestError: String?,
    val needsReview: List<ReceiptEntity>,
    val recent: List<ReceiptEntity>,
)

private suspend fun loadNotificationTabSnapshot(repo: ReceiptRepository): NotificationTabSnapshot {
    val queueCount = repo.queueInFlightCount()
    val latestError = repo.latestQueueErrorOrNull()
    val needsReview = repo.listNeedsReview(limit = 30)
    val nrIds = needsReview.map { it.receiptId }.toSet()
    val recent = repo.listRecentAnalyzed(limit = 60).filter { it.receiptId !in nrIds }.take(30)
    return NotificationTabSnapshot(queueCount, latestError, needsReview, recent)
}

@OptIn(ExperimentalMaterialApi::class)
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
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun applySnapshot(s: NotificationTabSnapshot) {
        queueCount = s.queueCount
        latestError = s.latestError
        needsReview = s.needsReview
        recent = s.recent
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                try {
                    applySnapshot(loadNotificationTabSnapshot(repo))
                } finally {
                    isRefreshing = false
                }
            }
        },
    )

    LaunchedEffect(Unit) {
        applySnapshot(loadNotificationTabSnapshot(repo))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .pullRefresh(pullRefreshState),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            TabScreenTitle("通知")
        }
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
            items(needsReview, key = { "needsReview:${it.receiptId}" }) { r ->
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
            items(recent, key = { "recent:${it.receiptId}" }) { r ->
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
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
}
