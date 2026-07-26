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
    var failureEnabled by remember { mutableStateOf(prefs.isFailureEnabled()) }
    var successEnabled by remember { mutableStateOf(prefs.isSuccessEnabled()) }
    var budgetEnabled by remember { mutableStateOf(prefs.isBudgetEnabled()) }

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
            label = "解析が失敗・要確認のとき",
            checked = failureEnabled,
            enabled = masterEnabled,
            onCheckedChange = {
                failureEnabled = it
                prefs.setFailureEnabled(it)
            },
        )
        NotificationToggleRow(
            label = "解析が完了したとき",
            checked = successEnabled,
            enabled = masterEnabled,
            onCheckedChange = {
                successEnabled = it
                prefs.setSuccessEnabled(it)
            },
        )
        if (showBudgetToggle) {
            NotificationToggleRow(
                label = "予算通知（定期確認・80%・100%）",
                checked = budgetEnabled,
                enabled = masterEnabled,
                onCheckedChange = {
                    budgetEnabled = it
                    prefs.setBudgetEnabled(it)
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
