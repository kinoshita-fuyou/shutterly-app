package com.shutterly.app.recognition

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 中文文本识别封装（v2，完全离线）。
 * 模型首次使用经 Play 服务下载（约 30MB），之后本地识别，单张 <1s。
 * 应用本身不声明 INTERNET 权限，下载由 Play 服务的下载器完成。
 */
class OcrEngine {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /** 识别 Bitmap，返回全部识别文本；模型未就绪等失败返回 null */
    suspend fun recognize(bitmap: Bitmap): String? {
        val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
        return try {
            task.await().text
        } catch (e: Exception) {
            val code = if (e is com.google.mlkit.common.MlKitException) e.errorCode else 0
            if (code == com.google.mlkit.common.MlKitException.UNAVAILABLE) {
                OcrStatus.onEvent("中文识别模型尚未就绪（首次使用需联网下载约 30MB），已自动重试…")
            }
            null
        }
    }

    fun close() = recognizer.close()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
