package work.temp1209.kakeibo.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import work.temp1209.kakeibo.data.db.ReceiptEntity

@Composable
fun ReceiptsListScreen(
    contentPadding: PaddingValues,
    loadReceipts: suspend () -> List<ReceiptEntity>,
    onOpenReceipt: (String) -> Unit,
) {
    var receipts by remember { mutableStateOf<List<ReceiptEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        receipts = loadReceipts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (receipts.isEmpty()) {
            Text("まだ保存されたレシートはありません。")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(receipts, key = { it.receiptId }) { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = { onOpenReceipt(r.receiptId) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("receiptDatetime: ${r.receiptDatetime ?: "-"}")
                        Text("capturedAt: ${r.capturedAt}")
                        Text("status: ${r.analysisStatus}")
                        Text("id: ${r.receiptId.take(8)}…")
                    }
                }
            }
        }
    }
}

