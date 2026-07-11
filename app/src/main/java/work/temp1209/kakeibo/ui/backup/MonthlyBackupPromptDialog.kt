package work.temp1209.kakeibo.ui.backup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import work.temp1209.kakeibo.data.prefs.FileBackupPrefs
import java.time.YearMonth

@Composable
fun MonthlyBackupPromptDialog(
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("今月のバックアップ") },
        text = {
            Text(
                "レシートデータを JSON ファイルに保存しておくと、" +
                    "機種変更やアプリの再インストール時に復元できます。",
            )
        },
        confirmButton = {
            Button(onClick = onExport) { Text("今すぐバックアップ") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("あとで") }
        },
    )
}

fun recordMonthlyPromptDismissed(prefs: FileBackupPrefs) {
    prefs.setLastPromptShownYearMonth(YearMonth.now().toString())
}
