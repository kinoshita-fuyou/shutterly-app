package com.shutterly.app.screenshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.shutterly.app.R
import com.shutterly.app.ui.MainActivity
import java.io.File

/**
 * 截图监听前台服务（specialUse 类型，无时长限制）：
 * - 常驻通知实时展示识别流程状态（用户随时可见“系统正在干什么”）；
 * - FileObserver 监听截图目录（触发源之一）；
 * - MediaStore 内容观察者（触发源之一，需照片权限）；
 * - START_STICKY：被系统回收后自动重启，无需用户再次手动开启。
 */
class ScreenshotWatcherService : Service() {

    private var fileWatchers = emptyList<FileObserver>()

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            ScreenshotPipeline.onMediaStoreChanged(this@ScreenshotWatcherService, uri)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 用户已关闭监听（或被自动重启时仍处于关闭状态）→ 立即退出
        if (!ScreenshotPipeline.isEnabled(this)) {
            ScreenshotStatus.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }
        ScreenshotPipeline.watcherService = this
        ScreenshotStatus.setRunning(true)
        ScreenshotStatus.post(Step.IDLE, "截图监听已启动（前台服务常驻）")
        startForeground(NOTIFICATION_ID, buildNotification("监听中…"))
        registerMediaObserver()
        startFileWatching()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(mediaObserver)
        fileWatchers.forEach { it.stopWatching() }
        fileWatchers = emptyList()
        if (ScreenshotPipeline.watcherService === this) ScreenshotPipeline.watcherService = null
        ScreenshotStatus.setRunning(false)
        ScreenshotStatus.post(Step.IDLE, "截图监听已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 触发源：截图目录 FileObserver ─────────────────────────────

    private fun startFileWatching() {
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        ).filter { it.exists() }
        val dirs = mutableListOf<File>()
        for (root in roots) {
            dirs += root
            root.listFiles()?.forEach { sub ->
                if (sub.isDirectory && looksLikeScreenshotDir(sub.name)) dirs += sub
            }
        }
        @Suppress("DEPRECATION")
        fileWatchers = dirs.map { dir ->
            object : FileObserver(dir.absolutePath, FileObserver.CREATE or FileObserver.MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val file = File(dir, path)
                    if (looksLikeScreenshot(file.name)) {
                        ScreenshotPipeline.onScreenshotFile(this@ScreenshotWatcherService, file)
                    }
                }
            }.also { it.startWatching() }
        }
    }

    private fun looksLikeScreenshotDir(name: String): Boolean =
        name.contains("Screenshot", ignoreCase = true) ||
            name.contains("Screen", ignoreCase = true) ||
            name.contains("截屏")

    private fun looksLikeScreenshot(name: String): Boolean =
        name.contains("Screenshot", ignoreCase = true) ||
            name.contains("截屏") ||
            name.contains("截图")

    private fun registerMediaObserver() {
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
    }

    // ── 状态通知 ──────────────────────────────────────────────────

    /** 刷新常驻通知内容（流水线每步调用） */
    fun refreshNotification(statusText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("快门账 · 截图识别")
            .setContentText(statusText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "截图识别状态", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
    }

    companion object {
        private const val TAG = "ShutterlyWatcher"
        private const val CHANNEL_ID = "screenshot_monitor"
        const val NOTIFICATION_ID = 1
    }
}
