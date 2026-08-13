package com.shutterly.app.screenshot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.shutterly.app.data.Category
import com.shutterly.app.data.Money
import com.shutterly.app.recognition.BillTextExtractor
import com.shutterly.app.recognition.ExtractedBill
import com.shutterly.app.recognition.OcrEngine
import com.shutterly.app.ui.ConfirmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 截图识别流水线（无需无障碍服务）：
 *
 * 触发源（三路，取先到者）：
 *  1. [onScreenshotNotification] — 通知监听服务捕获系统截图通知；
 *  2. [onMediaStoreChanged] — MediaStore 内容观察者；
 *  3. [onScreenshotFile] — 截图目录 FileObserver。
 * 读取一律走 MediaStore（BeeCount 同款：文件名过滤 + 30 秒时效过滤），
 * 需“照片与媒体”权限（Android 14+ 授权时选“允许访问所有照片”）。
 *
 * 处理：去重 → 读图 → ML Kit OCR → 提取 → 弹确认卡片；
 * 每步状态经 [ScreenshotStatus] 展示给用户，失败立即报错并给修复指引。
 */
object ScreenshotPipeline {

    private const val TAG = "ShutterlyPipeline"
    private const val PREFS = "shutterly_prefs"
    private const val KEY_ENABLED = "watcher_enabled"
    private const val MIN_INTERVAL_MS = 800L
    private const val MAX_AGE_SECONDS = 30L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ocr = OcrEngine()
    private val processedTags = HashSet<String>()
    private var lastTriggerAt = 0L

    /** 当前前台服务实例（用于刷新状态通知），由 ScreenshotWatcherService 维护 */
    @Volatile
    var watcherService: ScreenshotWatcherService? = null

    // ── 开关 ──────────────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) startWatcher(context) else stopWatcher(context)
    }

    fun startWatcher(context: Context) {
        try {
            context.startForegroundService(Intent(context, ScreenshotWatcherService::class.java))
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限（如开机后立即）
            ScreenshotStatus.post(Step.ERROR, "前台服务启动失败：${e.message}，请打开 App 重试")
        }
    }

    fun stopWatcher(context: Context) {
        context.stopService(Intent(context, ScreenshotWatcherService::class.java))
    }

    // ── 触发源 ────────────────────────────────────────────────────

    /** 通知监听触发：系统截图通知 → 从 MediaStore 读取（需照片权限） */
    fun onScreenshotNotification(context: Context, tag: String) {
        ScreenshotStatus.post(Step.DETECTED, "检测到系统截图（通知通道）")
        enqueue(context, "notif:$tag") { loadLatestFromMediaStore(context) }
    }

    /** MediaStore 内容观察者 */
    fun onMediaStoreChanged(context: Context, changedUri: Uri?) {
        enqueue(context, "ms:${changedUri ?: System.nanoTime()}") {
            loadLatestFromMediaStore(context)
        }
    }

    /** 截图目录 FileObserver */
    fun onScreenshotFile(context: Context, file: File) {
        enqueue(context, "file:${file.name}") { loadFile(context, file) }
    }

    // ── 流水线 ────────────────────────────────────────────────────

    private fun enqueue(context: Context, tag: String, loader: suspend () -> Bitmap?) {
        synchronized(processedTags) {
            if (!processedTags.add(tag)) return
        }
        if (System.currentTimeMillis() - lastTriggerAt < MIN_INTERVAL_MS) return
        lastTriggerAt = System.currentTimeMillis()

        scope.launch {
            val bitmap = try {
                loader()
            } catch (e: Exception) {
                Log.e(TAG, "读取截图失败", e)
                null
            }
            if (bitmap == null) {
                ScreenshotStatus.post(Step.ERROR, "已检测到截图，但读取图片失败：${readFailureHint(context)}")
                return@launch
            }
            ScreenshotStatus.post(Step.READING, "图片读取成功，正在本地识别文字…")

            val text = ocr.recognize(bitmap)
            bitmap.recycle()
            if (text.isNullOrBlank()) {
                ScreenshotStatus.post(
                    Step.ERROR,
                    "文字识别失败：识别模型未就绪（首次使用需联网下载约 30MB），自动重试中"
                )
                return@launch
            }

            val bill = BillTextExtractor.extract(text)
            val category = bill.category?.let { Category.entries.firstOrNull { c -> c.name == it }?.displayName }
            ScreenshotStatus.post(
                Step.EXTRACTED,
                "识别完成：¥${Money.fenToYuan(bill.amountFen)} · ${bill.merchant ?: "商户未知"} · ${category ?: "类别待选"}"
            )
            launchConfirm(context, bill)
        }
    }

    private fun launchConfirm(context: Context, bill: ExtractedBill) {
        val intent = Intent(context, ConfirmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ConfirmActivity.EXTRA_BILL, bill)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            ScreenshotStatus.post(Step.ERROR, "弹出确认卡片失败：${e.message}")
        }
    }

    // ── 图片读取 ──────────────────────────────────────────────────

    private fun loadByUri(context: Context, uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
        }

    private fun loadFile(context: Context, file: File): Bitmap? = try {
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
    } catch (_: SecurityException) {
        loadLatestFromMediaStore(context)
    }

    /** 从 MediaStore 查询最近 30 秒内新增的截图（BeeCount 同款：时间过滤防处理历史图片） */
    private fun loadLatestFromMediaStore(context: Context): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) {
            // ≤28 走文件路径即可（有 READ_EXTERNAL_STORAGE）
            return null
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR " +
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR " +
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?) AND " +
            "${MediaStore.Images.Media.DATE_ADDED} > ? AND ${MediaStore.Images.Media.SIZE} > 0"
        val args = arrayOf(
            "%Screenshot%", "%截屏%", "%截图%",
            ((System.currentTimeMillis() / 1000) - MAX_AGE_SECONDS).toString()
        )
        val cursor = try {
            context.contentResolver.query(
                collection, projection, selection, args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore 查询失败", e)
            null
        } ?: return null
        val id = cursor.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return null
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
        return loadByUri(context, uri)
    }

    /** 读取失败的提示：按系统版本给出可操作的修复指引 */
    private fun readFailureHint(context: Context): String {
        val hasMedia = Permissions.hasMediaPermission(context)
        return when {
            !hasMedia -> "未授予“照片与媒体”权限：请点击 App 首页的“授予照片权限”，选择“允许访问所有照片”"
            Build.VERSION.SDK_INT >= 34 ->
                "Android 14+ 需要“所有照片”访问权限，请在系统设置中重新授权（拒绝一次后需手动去设置开启）"
            else -> "权限可能被系统回收，请回到 App 重新授权后重试"
        }
    }
}
