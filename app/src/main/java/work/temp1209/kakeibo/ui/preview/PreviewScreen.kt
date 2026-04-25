package work.temp1209.kakeibo.ui.preview

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PreviewScreen(
    contentPadding: PaddingValues,
    imageUri: Uri,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Captured receipt preview",
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        )

        OutlinedButton(onClick = onCancel) {
            Text("キャンセル（撮り直す）")
        }

        Button(onClick = onConfirm) {
            Text("送信確定（保存）")
        }
    }
}

