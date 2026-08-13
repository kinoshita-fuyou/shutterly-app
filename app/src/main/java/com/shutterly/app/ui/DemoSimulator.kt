package com.shutterly.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.shutterly.app.recognition.BillTextExtractor
import com.shutterly.app.recognition.OcrEngine
import com.shutterly.app.screenshot.ScreenshotStatus
import com.shutterly.app.screenshot.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自测工具：在画布上“渲染”三张常见支付账单截图（微信/支付宝/银行短信），
 * 走与真实截屏完全相同的 OCR → 提取 → 确认卡片流程，用于验证识别正确率。
 */
object DemoSimulator {

    val KINDS = listOf("微信支付凭证", "支付宝账单详情", "银行消费短信")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun run(context: Context, kind: Int) {
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) { renderBill(kind) }
            val text = OcrEngine().recognize(bitmap)
            bitmap.recycle()
            if (text == null) {
                ScreenshotStatus.post(Step.ERROR, "演示截图识别失败：识别模型未就绪（首次使用需联网下载约 30MB）")
                return@launch
            }
            val bill = BillTextExtractor.extract(text)
            ScreenshotStatus.post(Step.EXTRACTED, "演示识别完成 → 金额 ¥${bill.amountFen / 100.0}，请核对")
            val intent = Intent(context, ConfirmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ConfirmActivity.EXTRA_BILL, bill)
            context.startActivity(intent)
        }
    }

    /** 渲染一屏“账单截图”，模拟系统截屏的分辨率与排版 */
    fun renderBill(kind: Int): Bitmap {
        val width = 720
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
        }
        fun line(y: Float, text: String, size: Float = 40f, bold: Boolean = false, color: Int = Color.BLACK) {
            val p = Paint(base).apply {
                this.textSize = size
                this.color = color
                if (bold) typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(text, 60f, y, p)
        }

        when (kind) {
            0 -> {
                line(140f, "微信支付", 44f, bold = true)
                line(260f, "商户收款凭证")
                line(380f, "星巴克咖啡店", 52f, bold = true)
                line(500f, "向商户付款")
                line(640f, "支付总额", 56f, bold = true, color = Color.parseColor("#C0392B"))
                line(720f, "-¥36.00", 80f, bold = true, color = Color.parseColor("#C0392B"))
                line(900f, "支付方式  零钱")
                line(1020f, "交易时间  2026-08-12 14:32:08")
                line(1140f, "商户单号  420000236500000")
            }
            1 -> {
                line(140f, "支付宝", 44f, bold = true)
                line(260f, "账单详情")
                line(400f, "收款方：张记沙县小吃", 44f)
                line(500f, "商品说明：香拌面 + 蒸饺")
                line(620f, "实付", 56f, bold = true, color = Color.parseColor("#1677FF"))
                line(700f, "¥23.50", 80f, bold = true, color = Color.parseColor("#1677FF"))
                line(880f, "交易时间  2026-08-13 12:41:05")
                line(1000f, "支付方式  余额")
                line(1120f, "订单号  20260813220014312345")
            }
            else -> {
                line(140f, "【工商银行】", 44f, bold = true)
                line(260f, "您尾号6688的储蓄卡于08月14日10:20")
                line(380f, "消费人民币36.50元（话费充值）。")
                line(500f, "可用额度8,900.00元。")
                line(640f, "收款方：中国移动通信集团")
                line(800f, "温馨提示：请勿向陌生人转账")
            }
        }
        return bitmap
    }
}
