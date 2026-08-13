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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
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
 * 识别确认卡片（前台界面）：展示 OCR 提取结果，可修改，确认后入库。
 * 由截图监听服务 / 演示模拟器从后台拉起。
 */
class ConfirmActivity : ComponentActivity() {

    /** 用 Compose 快照状态承载当前账单：onNewIntent 更新后自动重组 */
    var bill by mutableStateOf<ExtractedBill?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bill = intent.parcelableExtra(EXTRA_BILL, ExtractedBill::class.java)
        setContent {
            ShutterlyTheme {
                ConfirmScreen(activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        bill = intent.parcelableExtra(EXTRA_BILL, ExtractedBill::class.java)
    }

    companion object {
        const val EXTRA_BILL = "extra_bill"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfirmScreen(activity: ConfirmActivity) {
    val vm: ConfirmViewModel = viewModel(factory = ConfirmViewModel.Factory)
    val saved by vm.saved.collectAsState()
    val bill = activity.bill

    var amountStr by remember(bill) {
        mutableStateOf(
            if (bill?.hasAmount == true) {
                String.format(Locale.US, "%.2f", bill!!.amountFen / 100.0)
            } else ""
        )
    }
    var date by remember(bill) {
        mutableStateOf(LocalDate.ofEpochDay(bill?.epochDay ?: LocalDate.now().toEpochDay()))
    }
    var merchant by remember(bill) { mutableStateOf(bill?.merchant ?: "") }
    var category by remember(bill) {
        mutableStateOf(Category.entries.firstOrNull { it.name == bill?.category })
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val fen = Money.yuanToFen(amountStr)
    val canSave = fen != null && category != null

    if (saved) {
        LaunchedEffect(Unit) { activity.finish() }
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
                Text("识别到一笔交易，请确认", style = MaterialTheme.typography.titleMedium)
                if (bill?.hasAmount != true) {
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

        if (bill?.sourceTextPreview?.isNotBlank() == true) {
            Spacer(Modifier.height(12.dp))
            Text("识别原文：", style = MaterialTheme.typography.labelMedium)
            Text(
                bill!!.sourceTextPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { activity.finish() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("忽略")
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
                },
                enabled = canSave,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("确认入库")
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
private fun <T> Intent.parcelableExtra(key: String, clazz: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, clazz)
    } else {
        @Suppress("UNCHECKED_CAST")
        getParcelableExtra(key) as? T
    }
