package work.temp1209.kakeibo.ui.review

import android.app.TimePickerDialog
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.db.ReceiptEntity
import work.temp1209.kakeibo.data.db.ReceiptImageEntity
import work.temp1209.kakeibo.data.db.ReceiptItemEntity
import work.temp1209.kakeibo.data.domain.CategoryCatalog
import work.temp1209.kakeibo.data.domain.PaymentMethodCatalog
import work.temp1209.kakeibo.ui.common.ExpenseLineEditorCard
import work.temp1209.kakeibo.ui.common.ExpenseLineState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

private data class EditableLine(
    val itemId: String?,
    val confidence: Double,
    val state: ExpenseLineState,
)

private fun parseReceiptDatetime(raw: String?, zone: ZoneId): Pair<LocalDate, LocalTime> {
    if (raw.isNullOrBlank()) return LocalDate.now(zone) to LocalTime.now(zone).withSecond(0).withNano(0)
    val instant = runCatching { Instant.parse(raw.trim()) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw.trim()).toInstant() }.getOrNull()
    val zdt = if (instant != null) {
        instant.atZone(zone)
    } else {
        runCatching { LocalDate.parse(raw.trim()).atStartOfDay(zone) }.getOrElse {
            ZonedDateTime.now(zone)
        }
    }
    return zdt.toLocalDate() to zdt.toLocalTime().withSecond(0).withNano(0)
}

private fun ReceiptItemEntity.toEditableLine(): EditableLine =
    EditableLine(
        itemId = itemId,
        confidence = confidence,
        state = ExpenseLineState(
            itemName = itemName,
            quantityText = quantity.toString(),
            lineTotalText = lineTotalYen.toString(),
            categoryMajor = categoryMajor,
            categoryMinor = categoryMinor,
            necessityScore = necessityScore,
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(
    contentPadding: PaddingValues,
    receiptId: String,
    repo: ReceiptRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var loading by remember(receiptId) { mutableStateOf(true) }
    var baseReceipt by remember(receiptId) { mutableStateOf<ReceiptEntity?>(null) }
    var image by remember(receiptId) { mutableStateOf<ReceiptImageEntity?>(null) }
    var imageExpanded by remember(receiptId) { mutableStateOf(true) }

    var date by remember(receiptId) { mutableStateOf(LocalDate.now(zone)) }
    var time by remember(receiptId) { mutableStateOf(LocalTime.now(zone).withSecond(0).withNano(0)) }
    var merchant by remember(receiptId) { mutableStateOf("") }
    var totalText by remember(receiptId) { mutableStateOf("") }
    var paymentCode by remember(receiptId) { mutableStateOf("UNKNOWN") }
    var paymentServiceName by remember(receiptId) { mutableStateOf("") }
    var paymentMenu by remember { mutableStateOf(false) }
    var lines by remember(receiptId) { mutableStateOf<List<EditableLine>>(emptyList()) }

    var errorText by remember(receiptId) { mutableStateOf<String?>(null) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(receiptId) {
        loading = true
        try {
            val r = repo.getReceiptOrNull(receiptId)
            baseReceipt = r
            image = repo.getReceiptImage(receiptId)
            if (r != null) {
                val (d, t) = parseReceiptDatetime(r.receiptDatetime, zone)
                date = d
                time = t
                merchant = r.merchantName.orEmpty()
                totalText = r.totalAmountYen?.toString().orEmpty()
                paymentCode = r.paymentMethod?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
                paymentServiceName = r.paymentServiceName.orEmpty()
                lines = repo.listReceiptItems(receiptId)
                    .filter { it.isAdjustment == 0 }
                    .sortedBy { it.lineIndex }
                    .map { it.toEditableLine() }
                    .ifEmpty {
                        listOf(
                            EditableLine(
                                itemId = null,
                                confidence = 1.0,
                                state = ExpenseLineState(
                                    itemName = "",
                                    quantityText = "1",
                                    lineTotalText = "",
                                    categoryMajor = "FOOD",
                                    categoryMinor = CategoryCatalog.minorsFor("FOOD").firstOrNull() ?: "その他",
                                    necessityScore = 50,
                                ),
                            ),
                        )
                    }
            } else {
                lines = emptyList()
            }
        } finally {
            loading = false
        }
    }

    if (loading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text("読み込み中…", modifier = Modifier.padding(top = 12.dp))
        }
        return
    }

    val br = baseReceipt
    if (br == null) {
        Column(Modifier.padding(contentPadding).padding(16.dp)) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("レシートが見つかりません。")
        }
        return
    }

    if (br.deletedAt != null) {
        Column(Modifier.padding(contentPadding).padding(16.dp)) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("このレシートは削除済みのため修正できません。")
        }
        return
    }

    if (br.analysisStatus == "PENDING" || br.analysisStatus == "RUNNING") {
        Column(Modifier.padding(contentPadding).padding(16.dp)) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("解析完了後に修正できます。")
        }
        return
    }

    fun receiptDatetimeIso(): String =
        date.atTime(time).atZone(zone).toOffsetDateTime().toString()

    pendingDeleteIndex?.let { idx ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            title = { Text("明細行を削除") },
            text = { Text("${idx + 1}行目を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (lines.size > 1) {
                            lines = lines.filterIndexed { i, _ -> i != idx }
                        }
                        pendingDeleteIndex = null
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIndex = null }) { Text("キャンセル") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
            Text("レシート修正", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (image != null && image?.deletedAt == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("レシート画像", fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { imageExpanded = !imageExpanded }) {
                                Icon(
                                    if (imageExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (imageExpanded) "折りたたむ" else "展開",
                                )
                            }
                        }
                        if (imageExpanded) {
                            AsyncImage(
                                model = Uri.parse(image!!.localUri),
                                contentDescription = "レシート画像",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }

            Text(
                "画像を見ながら店名・日時・明細を修正し、「保存」を押してください。",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("取引日時", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = date.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("日付") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        ).show()
                    },
                ) { Text("変更") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = "%02d:%02d".format(time.hour, time.minute),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("時刻") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hh, mm -> time = LocalTime.of(hh, mm) },
                            time.hour,
                            time.minute,
                            true,
                        ).show()
                    },
                ) { Text("変更") }
            }

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it.take(80) },
                label = { Text("店名（必須・1〜80）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it.filter { ch -> ch.isDigit() }.take(12) },
                label = { Text("合計金額（円・必須）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ExposedDropdownMenuBox(
                expanded = paymentMenu,
                onExpandedChange = { paymentMenu = !paymentMenu },
            ) {
                OutlinedTextField(
                    value = PaymentMethodCatalog.label(paymentCode),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("支払手段（必須）") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                DropdownMenu(expanded = paymentMenu, onDismissRequest = { paymentMenu = false }) {
                    PaymentMethodCatalog.codesOrdered.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(PaymentMethodCatalog.label(code)) },
                            onClick = {
                                paymentCode = code
                                paymentMenu = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = paymentServiceName,
                onValueChange = { paymentServiceName = it },
                label = { Text("支払サービス名（任意）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("明細（1〜30行）", fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = {
                        if (lines.size < 30) {
                            lines = lines + EditableLine(
                                itemId = null,
                                confidence = 1.0,
                                state = ExpenseLineState(
                                    itemName = "",
                                    quantityText = "1",
                                    lineTotalText = "",
                                    categoryMajor = "FOOD",
                                    categoryMinor = CategoryCatalog.minorsFor("FOOD").firstOrNull() ?: "その他",
                                    necessityScore = 50,
                                ),
                            )
                        }
                    },
                    enabled = lines.size < 30,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "追加")
                    Text("行を追加")
                }
            }

            lines.forEachIndexed { idx, line ->
                ExpenseLineEditorCard(
                    index = idx,
                    state = line.state,
                    onChange = { newState ->
                        lines = lines.mapIndexed { i, cur ->
                            if (i == idx) cur.copy(state = newState) else cur
                        }
                    },
                    onRemove = { pendingDeleteIndex = idx },
                    removeEnabled = lines.size > 1,
                )
            }

            val linesSum = lines.sumOf { it.state.lineTotalText.toLongOrNull() ?: 0L }
            val total = totalText.toLongOrNull()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("明細合計: ${linesSum}円")
                    Text("合計金額: ${total ?: 0L}円")
                    if (total != null && total != linesSum) {
                        Text(
                            "差分 ${total - linesSum}円は保存時に調整行として自動反映されます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    errorText = null
                    val totalYen = totalText.toLongOrNull()
                    if (totalYen == null || totalYen < 0) {
                        errorText = "合計金額（円）を入力してください"
                        return@Button
                    }
                    scope.launch {
                        val parsedItems = lines.map { line ->
                            ReceiptRepository.ReceiptEditItemInput(
                                itemId = line.itemId,
                                itemName = line.state.itemName.trim(),
                                quantity = line.state.quantityText.toIntOrNull() ?: 0,
                                lineTotalYen = line.state.lineTotalText.toLongOrNull() ?: -1,
                                categoryMajor = line.state.categoryMajor,
                                categoryMinor = line.state.categoryMinor,
                                necessityScore = line.state.necessityScore,
                                confidence = line.confidence,
                            )
                        }
                        val result = repo.applyReceiptEdit(
                            ReceiptRepository.ReceiptEditInput(
                                receiptId = receiptId,
                                receiptDatetime = receiptDatetimeIso(),
                                merchantName = merchant,
                                totalAmountYen = totalYen,
                                paymentMethod = paymentCode,
                                paymentServiceName = paymentServiceName.trim().ifBlank { null },
                                items = parsedItems,
                            ),
                        )
                        if (result.isSuccess) {
                            onSaved()
                        } else {
                            errorText = result.exceptionOrNull()?.message ?: "保存に失敗しました"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}
