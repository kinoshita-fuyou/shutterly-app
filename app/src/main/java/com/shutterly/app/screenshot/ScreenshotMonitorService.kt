package com.shutterly.app.screenshot

import android.accessibilityservice.AccessibilityService
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.shutterly.app.recognition.BillTextExtractor
import com.shutterly.app.recognition.ExtractedBill
import com.shutterly.app.recognition.OcrEngine
import com.shutterly.app.recognition.OcrStatus
import com.shutterly.app.ui.ConfirmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

/**
 * 截图监听服务（无障碍服务）：
 *  1. FileObserver 监听系统截图目录（Pictures/Screenshots、DCIM/Screenshots 等）新文件；
 *  2. MediaStore ContentObserver 兜底（Android 9–13 配合媒体权限）；
 *  3. 检测到新截图 → 读取 → ML Kit OCR → 提取金额/日期/商户/类别 → 弹出确认卡片；
 *  4. Android 14+ 目录文件受媒体访问限制时，降级为 takeScreenshot() 截取当前屏幕。
 * 无障碍服务开启后常驻，保证截图瞬间即被捕获（2 秒内弹出结果）。
 */
class ScreenshotMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ocr = OcrEngine()
    private val processedNames = HashSet<String>()
    private var lastTriggerAt = 0L
    private var fileWatchers = emptyList<FileObserver>()

    private val screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scope.launch { checkMediaStoreForScreenshot() }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver
        )
        startFileWatching()
        Log.i(TAG, "截图监听已启动")
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(screenshotObserver)
        fileWatchers.forEach { it.stopWatching() }
        ocr.close()
        scope.cancel()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ── 监听：文件目录 ──────────────────────────────────────────────

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
        fileWatchers = dirs.map { dir ->
            object : FileObserver(dir.absolutePath, FileObserver.CREATE or FileObserver.MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    onScreenshotFile(File(dir, path))
                }
            }.also { it.startWatching() }
        }
    }

    private fun looksLikeScreenshotDir(name: String): Boolean =
        name.contains("Screenshot", ignoreCase = true) ||
            name.contains("Screen", ignoreCase = true) ||
            name.contains("截屏")

    private fun onScreenshotFile(file: File) {
        val name = file.name
        if (!(name.contains("Screenshot", ignoreCase = true) || name.contains("截屏"))) return
        synchronized(processedNames) {
            if (!processedNames.add(name)) return
        }
        if (!debounced()) return
        scope.launch { handleScreenshot(file) }
    }

    // ── 监听：MediaStore 兜底 ───────────────────────────────────────

    private suspend fun checkMediaStoreForScreenshot() {
        if (Build.VERSION.SDK_INT < 29) return
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val selection = "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR " +
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?) AND ${MediaStore.Images.Media.SIZE} > 0"
        val args = arrayOf("%Screenshot%", "%截屏%")
        val cursor = try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
        } catch (_: Exception) {
            null
        } ?: return

        val id = cursor.use { c ->
            if (c.moveToFirst()) {
                val rowId = c.getLong(0)
                val rowName = c.getString(1)
                synchronized(processedNames) {
                    if (!processedNames.add(rowName)) null else rowId
                }
            } else null
        } ?: return
        if (!debounced()) return

        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val bitmap = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = 2 }
                )
            }
        } catch (_: Exception) {
            null
        }
        if (bitmap != null) runOcr(bitmap)
    }

    // ── 处理流程 ───────────────────────────────────────────────────

    /** 事件风暴去抖：同一截图触发多个监听器时只处理一次 */
    private fun debounced(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < 1500) return false
        lastTriggerAt = now
        return true
    }

    private suspend fun handleScreenshot(file: File) {
        val bitmap = loadBitmap(file)
        if (bitmap != null) {
            runOcr(bitmap)
        } else if (Build.VERSION.SDK_INT >= 30) {
            captureCurrentScreen()
        }
    }

    /** 优先直接读文件路径；受限时经 MediaStore 读（需媒体权限，≤33 生效） */
    private fun loadBitmap(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
        } catch (_: SecurityException) {
            loadViaMediaStore(file.name)
        }
    }

    private fun loadViaMediaStore(name: String): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) return null
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val cursor = try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, arrayOf(name),
                "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
            )
        } catch (_: Exception) {
            null
        } ?: return null
        val id = cursor.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        } ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Android 14+ 无法读截图文件时：截取当前屏幕（用户通常停留在账单页） */
    @RequiresApi(30)
    private fun captureCurrentScreen() {
        val executor = Executor { r -> Handler(Looper.getMainLooper()).post(r) }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer ?: return
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        if (bitmap != null) {
                            scope.launch { runOcr(bitmap) }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        OcrStatus.onEvent("Android 14+ 截图文件受限，屏幕截取失败（$errorCode）")
                    }
                }
            )
        } catch (e: Exception) {
            OcrStatus.onEvent("Android 14+ 截图文件受限，屏幕截取不可用：${e.message}")
        }
    }

    private suspend fun runOcr(bitmap: Bitmap) {
        val text = ocr.recognize(bitmap)
        bitmap.recycle()
        if (text.isNullOrBlank()) return // 模型未就绪或无可识别文本：等下次截图
        val bill = BillTextExtractor.extract(text)
        Log.i(TAG, "识别完成: ${bill.amountFen}分 / ${bill.merchant} / ${bill.category}")
        launchConfirm(bill)
    }

    private fun launchConfirm(bill: ExtractedBill) {
        val intent = Intent(this, ConfirmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ConfirmActivity.EXTRA_BILL, bill)
        }
        startActivity(intent)
    }

    companion object {
        private const val TAG = "ShutterlyMonitor"
    }
}
