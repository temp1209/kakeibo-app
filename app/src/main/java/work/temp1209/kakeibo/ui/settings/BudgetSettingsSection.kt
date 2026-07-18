package work.temp1209.kakeibo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.temp1209.kakeibo.data.prefs.BudgetAggregateMode
import work.temp1209.kakeibo.data.prefs.BudgetSettings
import work.temp1209.kakeibo.data.prefs.BudgetStore

@Composable
fun BudgetSettingsSection(
    store: BudgetStore,
    onUsableChanged: (Boolean) -> Unit,
    onShowMessage: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initial = remember { store.current() }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var amountInput by remember {
        mutableStateOf(initial.monthlyBudgetYen.takeIf { it > 0 }?.toString().orEmpty())
    }
    var aggregateMode by remember { mutableStateOf(initial.aggregateMode) }
    val amount = amountInput.toLongOrNull()
    val amountValid = amount != null && amount > 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("月次予算")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("予算機能を使う")
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    store.setEnabled(it)
                    onUsableChanged(it && amountValid)
                },
            )
        }

        OutlinedTextField(
            value = amountInput,
            onValueChange = { value ->
                if (value.length <= 12 && value.all(Char::isDigit)) {
                    amountInput = value
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("月額予算（円）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = enabled && amountInput.isNotEmpty() && !amountValid,
            supportingText = {
                if (enabled && !amountValid) {
                    Text("1円以上の予算を入力してください")
                }
            },
        )

        Text("集計対象")
        BudgetModeRow(
            label = "すべての支出（必須 + 裁量）",
            selected = aggregateMode == BudgetAggregateMode.TOTAL,
            onSelect = { aggregateMode = BudgetAggregateMode.TOTAL },
        )
        BudgetModeRow(
            label = "裁量支出のみ",
            selected = aggregateMode == BudgetAggregateMode.DISCRETIONARY_ONLY,
            onSelect = { aggregateMode = BudgetAggregateMode.DISCRETIONARY_ONLY },
        )

        Button(
            enabled = amountValid,
            onClick = {
                val saved = BudgetSettings(
                    enabled = enabled,
                    monthlyBudgetYen = amount ?: 0,
                    aggregateMode = aggregateMode,
                )
                store.save(saved)
                onUsableChanged(saved.isUsable)
                scope.launch { onShowMessage("予算設定を保存しました") }
            },
        ) {
            Text("予算設定を保存")
        }
    }
}

@Composable
private fun BudgetModeRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Text(label)
    }
}
