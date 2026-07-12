package work.temp1209.kakeibo.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.domain.CategoryCatalog
import work.temp1209.kakeibo.data.domain.NecessityUtils

data class ExpenseLineState(
    val itemName: String,
    val quantityText: String,
    val lineTotalText: String,
    val categoryMajor: String,
    val categoryMinor: String,
    val necessityScore: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseLineEditorCard(
    index: Int,
    state: ExpenseLineState,
    onChange: (ExpenseLineState) -> Unit,
    onRemove: () -> Unit,
    removeEnabled: Boolean,
) {
    var majorMenu by remember { mutableStateOf(false) }
    var minorMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}行目", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onRemove, enabled = removeEnabled) {
                    Icon(Icons.Filled.Delete, contentDescription = "削除")
                }
            }

            OutlinedTextField(
                value = state.itemName,
                onValueChange = { onChange(state.copy(itemName = it.take(120))) },
                label = { Text("商品名（必須・1〜120）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.quantityText,
                    onValueChange = { onChange(state.copy(quantityText = it.filter { ch -> ch.isDigit() }.take(3))) },
                    label = { Text("数量（必須）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.lineTotalText,
                    onValueChange = { onChange(state.copy(lineTotalText = it.filter { ch -> ch.isDigit() }.take(12))) },
                    label = { Text("行合計（円・必須）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            ExposedDropdownMenuBox(
                expanded = majorMenu,
                onExpandedChange = { majorMenu = !majorMenu },
            ) {
                OutlinedTextField(
                    value = CategoryCatalog.majorLabel(state.categoryMajor),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("大カテゴリ") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = majorMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                DropdownMenu(expanded = majorMenu, onDismissRequest = { majorMenu = false }) {
                    CategoryCatalog.majorsOrdered.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                val newMajor = code
                                val newMinor = CategoryCatalog.minorsFor(newMajor).firstOrNull() ?: "その他"
                                onChange(state.copy(categoryMajor = newMajor, categoryMinor = newMinor))
                                majorMenu = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = minorMenu,
                onExpandedChange = { minorMenu = !minorMenu },
            ) {
                OutlinedTextField(
                    value = state.categoryMinor,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("小カテゴリ") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minorMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                DropdownMenu(expanded = minorMenu, onDismissRequest = { minorMenu = false }) {
                    CategoryCatalog.minorsFor(state.categoryMajor).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                onChange(state.copy(categoryMinor = m))
                                minorMenu = false
                            },
                        )
                    }
                }
            }

            Text("必須度: ${state.necessityScore}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.necessityScore.toFloat(),
                onValueChange = {
                    onChange(state.copy(necessityScore = NecessityUtils.snapScore(it.toInt())))
                },
                valueRange = 0f..100f,
                steps = (100 / NecessityUtils.SCORE_STEP) - 1,
            )
        }
    }
}
