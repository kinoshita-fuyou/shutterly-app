package com.shutterly.app.recognition

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** OCR 状态事件（模型下载进度提示等），主页收集后以 Snackbar 展示 */
object OcrStatus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    fun onEvent(message: String) {
        _events.tryEmit(message)
    }
}
