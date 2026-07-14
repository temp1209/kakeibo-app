package work.temp1209.kakeibo.ui.settings

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.temp1209.kakeibo.data.ai.AiProviderId
import work.temp1209.kakeibo.data.ai.AiRequestRouter
import work.temp1209.kakeibo.data.ai.ProviderSlot
import work.temp1209.kakeibo.data.gemini.GeminiUserMessages
import work.temp1209.kakeibo.data.prefs.AiProviderStore

private val SlotRowHeight = 56.dp
private const val TAG = "AiProviderSlots"

@Composable
fun AiProviderSlotsSection(
    store: AiProviderStore,
    onShowMessage: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val router = remember { AiRequestRouter(store) }
    var revision by remember { mutableIntStateOf(0) }
    val slots = remember { mutableStateListOf<ProviderSlot>() }

    LaunchedEffect(revision) {
        slots.clear()
        slots.addAll(store.getConfig().orderedSlots())
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var testingSlotId by remember { mutableStateOf<String?>(null) }
    var slotPendingDelete by remember { mutableStateOf<ProviderSlot?>(null) }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { SlotRowHeight.toPx() }

    fun persistOrder(from: Int, to: Int) {
        if (from == to || from !in slots.indices || to !in slots.indices) return
        val item = slots.removeAt(from)
        slots.add(to, item)
        val order = slots.map { it.slotId }
        store.setOrderedSlotIds(order)
        val labels = slots.joinToString(" → ") { it.label }
        Log.d(TAG, "order saved: $labels ids=$order")
        scope.launch { onShowMessage("優先順: $labels") }
    }

    fun moveSlot(index: Int, delta: Int) {
        val to = index + delta
        if (to !in slots.indices) return
        persistOrder(index, to)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI / API", style = MaterialTheme.typography.titleMedium)
        Text(
            "上から順に使います。長押ししてドラッグ、または矢印で優先順位を変更できます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (slots.isEmpty()) {
            Text(
                "APIキー未設定",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                slots.forEachIndexed { index, slot ->
                    key(slot.slotId) {
                        val isDragging = draggingId == slot.slotId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(SlotRowHeight)
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffsetY
                                        alpha = 0.92f
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "長押しで並び替え",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .pointerInput(slot.slotId, rowHeightPx) {
                                        var localOffset = 0f
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                localOffset = 0f
                                                draggingId = slot.slotId
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                localOffset += dragAmount.y
                                                dragOffsetY = localOffset
                                            },
                                            onDragEnd = {
                                                val from = slots.indexOfFirst { it.slotId == slot.slotId }
                                                if (from >= 0 && slots.isNotEmpty()) {
                                                    val to = (from + (localOffset / rowHeightPx).roundToInt())
                                                        .coerceIn(0, slots.lastIndex)
                                                    persistOrder(from, to)
                                                }
                                                draggingId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffsetY = 0f
                                            },
                                        )
                                    },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${index + 1}. ${slot.label}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = AiProviderId.displayName(slot.providerId),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { moveSlot(index, -1) },
                                enabled = index > 0 && draggingId == null,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上へ")
                            }
                            IconButton(
                                onClick = { moveSlot(index, 1) },
                                enabled = index < slots.lastIndex && draggingId == null,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下へ")
                            }
                            TextButton(
                                onClick = {
                                    if (testingSlotId != null) return@TextButton
                                    testingSlotId = slot.slotId
                                    scope.launch {
                                        val msg = runCatching {
                                            withContext(Dispatchers.IO) { router.testSlot(slot.slotId) }
                                        }.fold(
                                            onSuccess = { "疎通OK（${slot.label}）" },
                                            onFailure = {
                                                GeminiUserMessages.userFacingError(
                                                    it,
                                                    GeminiUserMessages.Operation.CONNECTIVITY_TEST,
                                                )
                                            },
                                        )
                                        onShowMessage(msg)
                                        testingSlotId = null
                                    }
                                },
                                enabled = testingSlotId == null && store.readApiKey(slot.slotId) != null,
                            ) {
                                Text(if (testingSlotId == slot.slotId) "…" else "疎通")
                            }
                            IconButton(onClick = { slotPendingDelete = slot }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "削除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (index < slots.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("APIキーを追加", modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showAddDialog) {
        AddApiKeyDialog(
            defaultLabel = if (slots.isEmpty()) "メイン" else "予備${slots.size}",
            onDismiss = { showAddDialog = false },
            onConfirm = { label, apiKey ->
                store.addSlot(AiProviderId.GEMINI, label, apiKey, enabled = true)
                showAddDialog = false
                revision++
                scope.launch { onShowMessage("「$label」を追加しました") }
            },
        )
    }

    slotPendingDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotPendingDelete = null },
            title = { Text("削除しますか？") },
            text = { Text("「${slot.label}」を削除します。この操作は取り消せません。") },
            confirmButton = {
                Button(
                    onClick = {
                        store.removeSlot(slot.slotId)
                        slotPendingDelete = null
                        revision++
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { slotPendingDelete = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun AddApiKeyDialog(
    defaultLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (label: String, apiKey: String) -> Unit,
) {
    var label by remember { mutableStateOf(defaultLabel) }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("APIキーを追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("ラベル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini APIキー") },
                    placeholder = { Text("AIza...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val key = apiKey.trim()
                    if (key.isEmpty()) return@Button
                    onConfirm(label.trim().ifBlank { defaultLabel }, key)
                },
                enabled = apiKey.isNotBlank(),
            ) { Text("追加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
