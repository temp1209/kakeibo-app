package work.temp1209.kakeibo.ui.manual

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.data.domain.CategoryCatalog
import work.temp1209.kakeibo.data.domain.PaymentMethodCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** 日付＋時刻（分まで。秒は 0 に揃え TimePicker 表示と一致） */
private fun nowDateAndTime(zone: ZoneId): Pair<LocalDate, LocalTime> {
    val z = ZonedDateTime.now(zone)
    val t = z.toLocalTime().withSecond(0).withNano(0)
    return z.toLocalDate() to t
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualExpenseScreen(
    contentPadding: PaddingValues,
    repo: ReceiptRepository,
    onBack: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var date by remember(zone) { mutableStateOf(nowDateAndTime(zone).first) }
    var time by remember(zone) { mutableStateOf(nowDateAndTime(zone).second) }

    var merchant by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var paymentCode by remember { mutableStateOf("UNKNOWN") }
    var paymentServiceName by remember { mutableStateOf("") }
    var paymentMenu by remember { mutableStateOf(false) }

    var lines by remember {
        mutableStateOf(
            listOf(
                ManualLineState(
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()

    fun receiptDatetimeIso(): String {
        val zdt = date.atTime(time).atZone(zone)
        return zdt.toOffsetDateTime().toString()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        topBar = {
            TopAppBar(
                title = { Text("レシートなしで入力") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                val total = totalText.toLongOrNull()
                                val merchantTrimmed = merchant.trim()
                                if (merchantTrimmed.isBlank() || merchantTrimmed.length > 80) {
                                    snackbarHostState.showSnackbar("店名（支出先）を1〜80文字で入力してください")
                                    return@launch
                                }
                                if (total == null || total < 0) {
                                    snackbarHostState.showSnackbar("合計金額（円）を入力してください")
                                    return@launch
                                }
                                if (lines.isEmpty()) {
                                    snackbarHostState.showSnackbar("明細は1行以上必要です")
                                    return@launch
                                }
                                if (lines.size > 30) {
                                    snackbarHostState.showSnackbar("明細は最大30行までです")
                                    return@launch
                                }

                                val parsedLines = lines.mapIndexed { idx, s ->
                                    val name = s.itemName.trim()
                                    val qty = s.quantityText.toIntOrNull()
                                    val lineTotal = s.lineTotalText.toLongOrNull()
                                    if (name.isBlank() || name.length > 120) {
                                        throw IllegalArgumentException("${idx + 1}行目: 商品名を1〜120文字で入力してください")
                                    }
                                    if (qty == null || qty < 1) {
                                        throw IllegalArgumentException("${idx + 1}行目: 数量は1以上で入力してください")
                                    }
                                    if (lineTotal == null || lineTotal < 0) {
                                        throw IllegalArgumentException("${idx + 1}行目: 行合計（円）を入力してください")
                                    }
                                    if (!CategoryCatalog.isValidPair(s.categoryMajor, s.categoryMinor)) {
                                        throw IllegalArgumentException("${idx + 1}行目: カテゴリが不正です")
                                    }
                                    ReceiptRepository.ManualReceiptItemInput(
                                        itemName = name,
                                        quantity = qty,
                                        lineTotalYen = lineTotal,
                                        categoryMajor = s.categoryMajor,
                                        categoryMinor = s.categoryMinor,
                                        necessityScore = s.necessityScore.coerceIn(0, 100),
                                    )
                                }

                                val result =
                                    runCatching {
                                        repo.saveManualNoReceipt(
                                            ReceiptRepository.ManualReceiptInput(
                                                receiptDatetime = receiptDatetimeIso(),
                                                merchantName = merchantTrimmed,
                                                totalAmountYen = total,
                                                paymentMethod = paymentCode,
                                                paymentServiceName = paymentServiceName.trim().ifBlank { null },
                                                items = parsedLines,
                                            ),
                                        ).getOrThrow()
                                    }

                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("保存しました")
                                    // 確定事項F: 遷移しない。入力を残す/クリアは任意だが、連続入力しやすいように最低限クリア。
                                    merchant = ""
                                    totalText = ""
                                    paymentCode = "UNKNOWN"
                                    paymentServiceName = ""
                                    val (d, t) = nowDateAndTime(zone)
                                    date = d
                                    time = t
                                    lines = lines.take(1).map { it.copy(itemName = "", quantityText = "1", lineTotalText = "") }
                                } else {
                                    snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "保存に失敗しました")
                                }
                            }
                        },
                    ) {
                        Text("保存")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        val now = Instant.now().atZone(zone)
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                date = LocalDate.of(y, m + 1, d)
                            },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        ).show()
                    },
                ) {
                    Text("変更")
                }
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
                ) {
                    Text("変更")
                }
            }

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it.take(80) },
                label = { Text("店名／支出先（必須・1〜80）") },
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
                            lines =
                                lines + ManualLineState(
                                    itemName = "",
                                    quantityText = "1",
                                    lineTotalText = "",
                                    categoryMajor = "FOOD",
                                    categoryMinor = CategoryCatalog.minorsFor("FOOD").firstOrNull() ?: "その他",
                                    necessityScore = 50,
                                )
                        }
                    },
                    enabled = lines.size < 30,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "追加")
                    Text("追加")
                }
            }

            lines.forEachIndexed { idx, s ->
                ManualLineEditorCard(
                    index = idx,
                    state = s,
                    onChange = { new ->
                        lines = lines.mapIndexed { i, cur -> if (i == idx) new else cur }
                    },
                    onRemove = {
                        if (lines.size > 1) {
                            lines = lines.filterIndexed { i, _ -> i != idx }
                        }
                    },
                    removeEnabled = lines.size > 1,
                )
            }

            val linesSum = lines.sumOf { it.lineTotalText.toLongOrNull() ?: 0L }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("明細合計: ${linesSum}円")
                    Text("合計金額: ${totalText.toLongOrNull() ?: 0L}円")
                    if (totalText.toLongOrNull() != null && totalText.toLongOrNull() != linesSum) {
                        Text("明細合計と合計金額が一致していません", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private data class ManualLineState(
    val itemName: String,
    val quantityText: String,
    val lineTotalText: String,
    val categoryMajor: String,
    val categoryMinor: String,
    val necessityScore: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualLineEditorCard(
    index: Int,
    state: ManualLineState,
    onChange: (ManualLineState) -> Unit,
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
                onValueChange = { onChange(state.copy(necessityScore = it.toInt().coerceIn(0, 100))) },
                valueRange = 0f..100f,
                steps = 99,
            )
        }
    }
}

