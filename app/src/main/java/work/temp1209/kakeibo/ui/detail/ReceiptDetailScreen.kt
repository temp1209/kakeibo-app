package work.temp1209.kakeibo.ui.detail

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import work.temp1209.kakeibo.data.db.ReceiptImageEntity

@Composable
fun ReceiptDetailScreen(
    contentPadding: PaddingValues,
    receiptId: String,
    loadImage: suspend (String) -> ReceiptImageEntity?,
    onBack: () -> Unit,
) {
    var image by remember { mutableStateOf<ReceiptImageEntity?>(null) }

    LaunchedEffect(receiptId) {
        image = loadImage(receiptId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = onBack) { Text("戻る") }

        if (image == null) {
            Text("画像が見つかりません")
            return@Column
        }

        AsyncImage(
            model = Uri.parse(image!!.localUri),
            contentDescription = "Receipt image",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

