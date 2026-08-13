package com.shutterly.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shutterly.app.data.Category
import com.shutterly.app.data.Money
import com.shutterly.app.data.Record
import com.shutterly.app.recognition.OcrStatus
import com.shutterly.app.screenshot.ScreenshotMonitorService
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: RecordViewModel,
    onAdd: () -> Unit,
    onStats: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val allRecords by vm.allRecords.collectAsState()
    val monthRecords by vm.monthRecords.collectAsState()
    var pendingDelete by remember { mutableStateOf<Record?>(null) }

    LaunchedEffect(Unit) {
        OcrStatus.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快门账") },
                actions = {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "月度统计")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "手动记账")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            item { ServiceBanner() }
            item { MonthSummaryCard(monthRecords, onClick = onStats) }
            item {
                Text(
                    "全部记录",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(allRecords, key = { it.id }) { record ->
                RecordRow(record, onDelete = { pendingDelete = record })
            }
            if (allRecords.isEmpty()) {
                item {
                    Text(
                        "还没有记录，点右下角 + 手动记一笔，或截一张支付账单试试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            item { DemoSection() }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = { Text("${record.merchant ?: "无商户"} · ¥${Money.fenToYuan(record.amountFen)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(record.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 引导横幅：无障碍服务未开启 / 媒体读取权限缺失时显示 */
@Composable
private fun ServiceBanner() {
    val context = LocalContext.current
    val a11yEnabled = remember { mutableStateOf(isAccessibilityEnabled(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            a11yEnabled.value = isAccessibilityEnabled(context)
            delay(2000)
        }
    }
    val mediaPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasMedia = ContextCompat.checkSelfPermission(context, mediaPermission) ==
        PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    if (a11yEnabled.value && hasMedia) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("开启截图自动记账", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    a11yEnabled.value -> "已开启无障碍服务，还需授予截图读取权限（仅 Android 13 及以下生效）"
                    !hasMedia -> "开启无障碍服务并授予媒体权限后，截支付账单图将自动识别记账"
                    else -> "开启无障碍服务后，截支付账单图将自动识别记账（全程本地，不联网）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                if (!hasMedia) {
                    TextButton(onClick = { permissionLauncher.launch(mediaPermission) }) {
                        Text("授予媒体权限")
                    }
                }
                if (!a11yEnabled.value) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) {
                        Text("去开启无障碍服务")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(monthRecords: List<Record>, onClick: () -> Unit) {
    val now = LocalDate.now()
    val total = monthRecords.sumOf { it.amountFen }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${now.year}年${now.monthValue}月支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "¥${Money.fenToYuan(total)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${monthRecords.size} 笔", style = MaterialTheme.typography.labelMedium)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun RecordRow(record: Record, onDelete: () -> Unit) {
    val cat = Category.entries.firstOrNull { it.name == record.category }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(cat?.color() ?: Color.Gray)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.merchant ?: "未填商户",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${LocalDate.ofEpochDay(record.epochDay)} · ${cat?.displayName ?: record.category}" +
                        " · ${if (record.source == "screenshot") "截图" else "手动"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "-¥${Money.fenToYuan(record.amountFen)}",
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 演示入口：模拟三张常见账单截图，走完整 OCR→提取→确认流程（自测用） */
@Composable
private fun DemoSection() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    TextButton(
        onClick = { showDialog = true },
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Icon(
            Icons.Outlined.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text("演示：模拟三张账单截图识别", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("模拟截图识别（自测）") },
            text = {
                Column {
                    DemoSimulator.KINDS.forEachIndexed { i, label ->
                        TextButton(onClick = {
                            showDialog = false
                            DemoSimulator.run(context, i)
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("关闭") }
            }
        )
    }
}

fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ScreenshotMonitorService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
