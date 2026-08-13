package com.shutterly.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.shutterly.app.data.Category
import com.shutterly.app.data.Money
import com.shutterly.app.data.Record
import com.shutterly.app.screenshot.Permissions
import com.shutterly.app.screenshot.ScreenshotPipeline
import com.shutterly.app.screenshot.ScreenshotStatus
import com.shutterly.app.screenshot.Step
import com.shutterly.app.screenshot.StatusEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        ScreenshotStatus.events.collect { snackbarHostState.showSnackbar(it.message) }
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
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { WatchControlCard() }
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

/**
 * 监听控制卡片：开关 + 权限状态行 + 最近活动时间线。
 * 让用户随时看到系统正在干什么，权限缺失时一键跳转修复。
 */
@Composable
private fun WatchControlCard() {
    val context = LocalContext.current
    val running by ScreenshotStatus.running.collectAsState()
    val timeline by ScreenshotStatus.timeline.collectAsState()
    var enabled by remember { mutableStateOf(ScreenshotPipeline.isEnabled(context)) }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        ScreenshotStatus.post(
            if (granted) Step.IDLE else Step.ERROR,
            if (granted) "照片与媒体权限已授予"
            else "照片权限未授予：请在系统设置里为快门账开启“允许访问所有照片”（Android 14+ 需要）"
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("截屏自动识别", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            running -> "前台服务运行中：截支付账单图后 2 秒内弹确认卡片"
                            enabled -> "监听已开启，正在启动前台服务…"
                            else -> "监听已关闭：开启后截屏才会被识别"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        enabled = v
                        ScreenshotPipeline.setEnabled(context, v)
                        if (v) ScreenshotStatus.post(Step.IDLE, "正在启动截图监听…")
                    }
                )
            }

            Spacer(Modifier.height(4.dp))
            PermissionRow(
                label = "通知使用权（识别截图的关键，开启一次长期有效）",
                granted = Permissions.hasNotificationAccess(context),
                actionText = "去开启"
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            PermissionRow(
                label = "照片与媒体（读取截图图片）",
                granted = Permissions.hasMediaPermission(context),
                actionText = "授权"
            ) {
                mediaLauncher.launch(Permissions.mediaPermissions())
            }
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionRow(
                    label = "通知权限（显示运行状态与结果）",
                    granted = Permissions.hasPostNotification(context),
                    actionText = "授权"
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "若锁屏后截屏无响应：请到系统设置 → 应用 → 快门账 → 允许自启动与后台运行（ColorOS/EMUI 等国产系统会杀后台），并保持“通知使用权”开启",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (timeline.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("最近活动", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                timeline.take(6).forEach { event ->
                    TimelineRow(event)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (!granted) {
            TextButton(onClick = onAction) {
                Text(actionText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimelineRow(event: StatusEvent) {
    val color = when (event.step) {
        Step.ERROR -> MaterialTheme.colorScheme.error
        Step.EXTRACTED, Step.CONFIRMED -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.outline
    }
    val time = LocalTime.from(Instant.ofEpochMilli(event.time).atZone(ZoneId.systemDefault()))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            event.message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            time.format(TIME_FORMAT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
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

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
