package com.shutterly.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shutterly.app.data.Category
import com.shutterly.app.data.Money
import com.shutterly.app.data.Record
import com.shutterly.app.recognition.ExtractedBill
import com.shutterly.app.ui.theme.ShutterlyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 识别确认卡片（前台界面）：支持多笔账单逐笔确认。
 * 由截图监听流水线从后台拉起，展示识别结果，可修改，确认后逐笔入库。
 */
class ConfirmActivity : ComponentActivity() {

    /** 待确认账单列表（快照状态：onNewIntent 更新后自动重组） */
    var bills by mutableStateOf<List<ExtractedBill>>(emptyList())
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bills = readBills(intent)
        setContent {
            ShutterlyTheme {
                ConfirmScreen(activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        bills = readBills(intent)
    }

    private fun readBills(intent: Intent): List<ExtractedBill> {
        intent.parcelableArrayListExtraCompat(EXTRA_BILLS)?.let { if (it.isNotEmpty()) return it }
        intent.parcelableExtraCompat(EXTRA_BILL, ExtractedBill::class.java)?.let { return listOf(it) }
        return emptyList()
    }

    companion object {
        const val EXTRA_BILL = "extra_bill"
        const val EXTRA_BILLS = "extra_bills"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfirmScreen(activity: ConfirmActivity) {
    val vm: ConfirmViewModel = viewModel(factory = ConfirmViewModel.Factory)
    val bills = activity.bills
    var current by remember { mutableIntStateOf(0) }
    val bill = bills.getOrNull(current)

    if (bill == null) {
        // 异常兜底：无数据直接关闭
        activity.finish()
        return
    }
    val isLast = current == bills.size - 1

    var amountStr by remember(bill, current) {
        mutableStateOf(
            if (bill.hasAmount) String.format(Locale.US, "%.2f", bill.amountFen / 100.0) else ""
        )
    }
    var date by remember(bill, current) {
        mutableStateOf(LocalDate.ofEpochDay(bill.epochDay))
    }
    var merchant by remember(bill, current) { mutableStateOf(bill.merchant ?: "") }
    var category by remember(bill, current) {
        mutableStateOf(Category.entries.firstOrNull { it.name == bill.category })
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val fen = Money.yuanToFen(amountStr)
    val canSave = fen != null && category != null

    fun advance() {
        if (isLast) activity.finish() else current++
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    if (bills.size > 1) "识别到 ${bills.size} 笔交易（${current + 1}/${bills.size}）" else "识别到一笔交易",
                    style = MaterialTheme.typography.titleMedium
                )
                if (bills.size > 1) {
                    Text(
                        "请逐笔核对，确认后自动进入下一笔",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!bill.hasAmount) {
                    Text(
                        "未能自动提取金额，请手动填写",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = amountStr,
            onValueChange = { amountStr = it },
            label = { Text("金额（元）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = amountStr.isNotEmpty() && fen == null,
            modifier = Modifier.fillMaxWidth()
        )
        if (amountStr.isNotEmpty() && fen == null) {
            Text(
                "金额格式不正确（最多两位小数）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showDatePicker = true }) {
            Text("日期：${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}")
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("商户（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Text("类别", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text(c.displayName) }
                )
            }
        }
        if (category == null) {
            Text(
                "请选择类别",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (bill.sourceTextPreview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("识别原文：", style = MaterialTheme.typography.labelMedium)
            Text(
                bill.sourceTextPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { advance() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(if (isLast) "忽略" else "忽略此笔")
            }
            Button(
                onClick = {
                    vm.save(
                        Record(
                            amountFen = fen!!,
                            epochDay = date.toEpochDay(),
                            merchant = merchant.ifBlank { null },
                            category = category!!.name,
                            source = "screenshot"
                        )
                    )
                    advance()
                },
                enabled = canSave,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(if (isLast) "确认入库" else "确认并入下一笔")
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Suppress("DEPRECATION")
private fun <T> Intent.parcelableExtraCompat(key: String, clazz: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, clazz)
    } else {
        @Suppress("UNCHECKED_CAST")
        getParcelableExtra(key) as? T
    }

@Suppress("DEPRECATION")
private fun Intent.parcelableArrayListExtraCompat(key: String): ArrayList<ExtractedBill>? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(key, ExtractedBill::class.java)
    } else {
        @Suppress("UNCHECKED_CAST")
        getParcelableArrayListExtra<ExtractedBill>(key) as? ArrayList<ExtractedBill>
    }
