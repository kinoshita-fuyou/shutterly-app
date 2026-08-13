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

    /** 识别 Bitmap，返回全部识别文本；模型未就绪等失败返回 null（由调用方报错提示） */
    suspend fun recognize(bitmap: Bitmap): String? {
        val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
        return try {
            task.await().text
        } catch (e: Exception) {
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
