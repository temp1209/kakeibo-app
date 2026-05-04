package work.temp1209.kakeibo.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onPickCamera: () -> Unit,
    onPickManualNoReceipt: () -> Unit,
    onPickEvidenceImage: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "支出を追加",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            TextButton(
                onClick = onPickCamera,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("レシートを撮る")
            }

            HorizontalDivider()

            TextButton(
                onClick = onPickManualNoReceipt,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("レシートなしで入力")
            }

            TextButton(
                onClick = onPickEvidenceImage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("画像から（スクショ等）")
            }
        }
    }
}

