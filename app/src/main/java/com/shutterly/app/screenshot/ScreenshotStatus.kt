package com.shutterly.app.screenshot

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 识别流程步骤（用于状态面板与前台服务通知） */
enum class Step { IDLE, DETECTED, READING, OCR, EXTRACTED, CONFIRMED, ERROR }

/** 一次流程事件：步骤 + 说明 + 时间 */
data class StatusEvent(
    val step: Step,
    val message: String,
    val time: Long = System.currentTimeMillis()
)

/**
 * 截图识别运行状态中心：
 * - [events] 供 Snackbar 实时提示
 * - [timeline] 供主页状态面板展示最近 30 条流程事件
 * - [running] 前台服务运行状态
 */
object ScreenshotStatus {
    private val _events = MutableSharedFlow<StatusEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<StatusEvent> = _events

    private val _timeline = MutableStateFlow<List<StatusEvent>>(emptyList())
    val timeline: StateFlow<List<StatusEvent>> = _timeline

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun post(step: Step, message: String) {
        val event = StatusEvent(step, message)
        _timeline.value = (listOf(event) + _timeline.value).take(30)
        _events.tryEmit(event)
        // 同步刷新前台服务常驻通知，让用户不看 App 也能实时看到流程进度
        ScreenshotPipeline.watcherService?.refreshNotification(message)
    }

    fun setRunning(value: Boolean) {
        _running.value = value
    }
}
