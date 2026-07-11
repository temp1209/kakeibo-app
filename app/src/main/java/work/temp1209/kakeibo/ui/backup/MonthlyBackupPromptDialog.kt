package work.temp1209.kakeibo.ui.backup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.YearMonth

@Composable
fun MonthlyBackupPromptDialog(
    onExport: () -> Unit,
    onDismiss: (dismissForMonth: Boolean) -> Unit,
) {
    var dismissForMonth by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(dismissForMonth) },
        title = { Text("今月のバックアップ") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    "レシートデータを JSON ファイルに保存しておくと、" +
                        "機種変更やアプリの再インストール時に復元できます。",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dismissForMonth,
                        onCheckedChange = { dismissForMonth = it },
                    )
                    Text("今月は表示しない")
                }
            }
        },
        confirmButton = {
            Button(onClick = onExport) { Text("今すぐバックアップ") }
        },
        dismissButton = {
            Button(onClick = { onDismiss(dismissForMonth) }) { Text("あとで") }
        },
    )
}

fun recordMonthlyPromptDismissed(
    prefs: work.temp1209.kakeibo.data.prefs.FileBackupPrefs,
    dismissForMonth: Boolean,
) {
    val currentYm = YearMonth.now().toString()
    prefs.setLastPromptShownYearMonth(currentYm)
    if (dismissForMonth) {
        prefs.setDismissedPromptYearMonth(currentYm)
    }
}
