package work.temp1209.kakeibo.ui.detail

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import work.temp1209.kakeibo.data.db.ReceiptImageEntity

@Composable
fun ReceiptDetailScreen(
    contentPadding: PaddingValues,
    receiptId: String,
    loadImage: suspend (String) -> ReceiptImageEntity?,
    loadGeminiJson: suspend (String) -> String?,
    onBack: () -> Unit,
) {
    var image by remember { mutableStateOf<ReceiptImageEntity?>(null) }
    var geminiJson by remember { mutableStateOf<String?>(null) }
    var showGeminiJson by remember { mutableStateOf(false) }

    LaunchedEffect(receiptId) {
        image = loadImage(receiptId)
        geminiJson = loadGeminiJson(receiptId)
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

        Button(
            onClick = { showGeminiJson = !showGeminiJson },
            enabled = geminiJson != null,
        ) {
            Text(if (showGeminiJson) "Gemini JSONを隠す" else "Gemini JSONを見る")
        }

        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        if (showGeminiJson) {
            val scroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(8.dp),
            ) {
                Text(
                    text = geminiJson ?: "（Gemini結果がありません）",
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            AsyncImage(
                model = Uri.parse(image!!.localUri),
                contentDescription = "Receipt image",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

