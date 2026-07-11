package work.temp1209.kakeibo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.prefs.GeminiApiKeyStore

@Composable
fun GeminiApiKeyInputSection(
    store: GeminiApiKeyStore,
    apiKeyInput: String,
    onApiKeyInputChange: (String) -> Unit,
    onSaved: () -> Unit = {},
    showStatusLine: Boolean = true,
    showSaveButton: Boolean = true,
    confirmOverwrite: Boolean = true,
    saveButtonLabel: String? = null,
) {
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showStatusLine) {
            Text(
                text = if (store.hasKey()) {
                    "APIキー: 保存済み（表示はしません）"
                } else {
                    "APIキー: 未設定"
                },
            )
        }

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = onApiKeyInputChange,
            label = { Text("Gemini APIキーを入力") },
            placeholder = { Text("AIza...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (showSaveButton) {
            Button(
                onClick = {
                    val trimmed = apiKeyInput.trim()
                    if (trimmed.isEmpty()) return@Button
                    if (confirmOverwrite && store.hasKey()) {
                        showOverwriteConfirm = true
                    } else {
                        store.saveKey(trimmed)
                        onApiKeyInputChange("")
                        onSaved()
                    }
                },
                enabled = apiKeyInput.isNotBlank(),
            ) {
                val defaultLabel = if (store.hasKey()) "APIキーを更新" else "APIキーを保存"
                Text(saveButtonLabel ?: defaultLabel)
            }
        }
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text("APIキーを更新しますか？") },
            text = { Text("既存のAPIキーを上書きします。よろしいですか？") },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = apiKeyInput.trim()
                        if (trimmed.isNotEmpty()) {
                            store.saveKey(trimmed)
                            onApiKeyInputChange("")
                            onSaved()
                        }
                        showOverwriteConfirm = false
                    },
                ) { Text("更新") }
            },
            dismissButton = {
                Button(onClick = { showOverwriteConfirm = false }) { Text("キャンセル") }
            },
        )
    }
}
