package work.temp1209.kakeibo.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

@Composable
fun NotificationsScreen(
    contentPadding: PaddingValues,
    repo: ReceiptRepository,
) {
    var queueCount by remember { mutableStateOf(0) }
    var latestError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        queueCount = repo.queueInFlightCount()
        latestError = repo.latestQueueErrorOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("解析キュー: ${queueCount}件")
        Text("最終エラー: ${latestError ?: "なし"}")
        Text("通知履歴/要確認一覧はPhase2後半で追加します。")
    }
}

