package com.shutterly.app.screenshot

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** 权限与系统开关状态检查（免无障碍，全部可一键跳转设置开启） */
object Permissions {

    /** 截图所需的媒体权限数组（按系统版本） */
    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasMediaPermission(context: Context): Boolean =
        mediaPermissions().all { p ->
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        }

    /** 通知使用权（NotificationListenerService 是否已开启，关闭应用后依然有效） */
    fun hasNotificationAccess(context: Context): Boolean {
        val expected = ComponentName(context, ScreenshotNotifierListener::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    /** Android 13+ 通知权限（决定前台服务状态通知是否可见） */
    fun hasPostNotification(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
