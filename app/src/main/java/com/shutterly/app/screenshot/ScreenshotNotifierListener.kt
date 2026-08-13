package com.shutterly.app.screenshot

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 通知监听服务（免无障碍）：
 * - 系统截屏后会发布“截图已保存”通知，此服务负责第一时间捕获该通知作为触发信号；
 * - 触发后由 [ScreenshotPipeline] 从 MediaStore 读取截图（需照片与媒体权限，
 *   Android 14+ 授权弹窗选“允许访问所有照片”）；
 * - 开启一次“通知使用权”后由系统常驻管理：关闭 App、重启手机均自动恢复，
 *   不会像无障碍服务那样被系统/OEM 关闭。
 * （注：Android 14 通知中并无公开的截图 URI extra，故仅作触发、不做免权限读取。）
 */
class ScreenshotNotifierListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isScreenshotNotification(sbn)) return
        Log.i(TAG, "收到截图通知: pkg=${sbn.packageName} key=${sbn.key}")
        ScreenshotPipeline.onScreenshotNotification(this, sbn.key)
    }

    private fun isScreenshotNotification(sbn: StatusBarNotification): Boolean {
        // 常见系统截屏应用包名
        if (sbn.packageName in SYSTEM_SCREENSHOT_PACKAGES) return true
        // 标题/文本含截图关键词（适配各 ROM 包名差异）
        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val t = "$title $text"
        return SCREENSHOT_KEYWORDS.any { t.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val TAG = "ShutterlyNotify"
        private val SCREENSHOT_KEYWORDS = listOf("截图", "截屏", "screenshot", "screen shot")
        private val SYSTEM_SCREENSHOT_PACKAGES = setOf(
            "com.android.systemui",      // AOSP / ColorOS
            "com.coloros.screenshot",    // OPPO / realme / 一加
            "com.oplus.screenshot",
            "com.miui.screenshot",       // 小米
            "com.huawei.screenshot",     // 华为
            "com.vivo.screenshot",       // vivo
            "com.samsung.android.app.screenshot" // 三星
        )
    }
}
