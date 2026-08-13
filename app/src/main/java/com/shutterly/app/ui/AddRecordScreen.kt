package com.shutterly.app.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shutterly.app.data.Category
import com.shutterly.app.data.Money
import com.shutterly.app.data.Record
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRecordScreen(vm: RecordViewModel, onDone: () -> Unit) {
    var amountStr by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val fen = Money.yuanToFen(amountStr)
    val canSave = fen != null && category != null

    if (saved) {
        LaunchedEffect(Unit) { onDone() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手动记账") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("金额（元）", style = MaterialTheme.typography.labelMedium)
            Text(
                if (amountStr.isEmpty()) "0.00" else amountStr,
                style = MaterialTheme.typography.displaySmall,
                color = if (amountStr.isNotEmpty() && fen == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (amountStr.isNotEmpty() && fen == null) {
                Text(
                    "金额格式不正确（最多两位小数）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            NumberKeypad(onKey = { key -> amountStr = appendAmount(amountStr, key) })

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("日期", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                }
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

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    vm.add(
                        Record(
                            amountFen = fen!!,
                            epochDay = date.toEpochDay(),
                            merchant = merchant.ifBlank { null },
                            category = category!!.name,
                            source = "manual"
                        )
                    )
                    saved = true
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("保存")
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

/** 追加按键输入：限两位小数、最多 8 位整数 */
fun appendAmount(current: String, key: String): String {
    return when (key) {
        "." -> when {
            current.isEmpty() -> "0."
            "." in current -> current
            else -> current + "."
        }
        "⌫" -> current.dropLast(1)
        else -> {
            if (current.contains(".") && current.substringAfter(".").length >= 2) return current
            if (current.replace(".", "").length >= 8) return current
            current + key
        }
    }
}

/** 简易数字键盘（0-9、小数点、退格） */
@Composable
fun NumberKeypad(onKey: (String) -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        keys.forEach { row ->
            Row {
                row.forEach { key ->
                    TextButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.size(width = 96.dp, height = 56.dp)
                    ) {
                        Text(key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
