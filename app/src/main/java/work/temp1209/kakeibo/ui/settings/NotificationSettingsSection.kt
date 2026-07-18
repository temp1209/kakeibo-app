package work.temp1209.kakeibo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.prefs.NotificationPrefs

@Composable
fun NotificationSettingsSection(
    prefs: NotificationPrefs,
    showBudgetToggle: Boolean,
) {
    var masterEnabled by remember { mutableStateOf(prefs.isMasterEnabled()) }
    var failedEnabled by remember { mutableStateOf(prefs.isAnalysisFailedEnabled()) }
    var doneEnabled by remember { mutableStateOf(prefs.isAnalysisDoneEnabled()) }
    var needsReviewEnabled by remember { mutableStateOf(prefs.isNeedsReviewEnabled()) }
    var budgetProgressEnabled by remember { mutableStateOf(prefs.isBudgetProgressEnabled()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("通知")
        NotificationToggleRow(
            label = "すべての通知",
            checked = masterEnabled,
            onCheckedChange = {
                masterEnabled = it
                prefs.setMasterEnabled(it)
            },
        )
        NotificationToggleRow(
            label = "解析が失敗したとき",
            checked = failedEnabled,
            enabled = masterEnabled,
            onCheckedChange = {
                failedEnabled = it
                prefs.setAnalysisFailedEnabled(it)
            },
        )
        NotificationToggleRow(
            label = "解析が完了したとき",
            checked = doneEnabled,
            enabled = masterEnabled,
            onCheckedChange = {
                doneEnabled = it
                prefs.setAnalysisDoneEnabled(it)
            },
        )
        NotificationToggleRow(
            label = "要確認のレシートがあるとき",
            checked = needsReviewEnabled,
            enabled = masterEnabled,
            onCheckedChange = {
                needsReviewEnabled = it
                prefs.setNeedsReviewEnabled(it)
            },
        )
        if (showBudgetToggle) {
            NotificationToggleRow(
                label = "月次予算の進捗",
                checked = budgetProgressEnabled,
                enabled = masterEnabled,
                onCheckedChange = {
                    budgetProgressEnabled = it
                    prefs.setBudgetProgressEnabled(it)
                },
            )
        }
    }
}

@Composable
private fun NotificationToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
