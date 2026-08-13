package com.shutterly.app.recognition

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 从截图识别出的账单候选结果。
 * 确认卡片展示后可修改，确认后入库。
 * [hasAmount] 为 false 表示未提取到可靠金额（必须人工填写，禁止静默录错）。
 */
@Parcelize
data class ExtractedBill(
    val amountFen: Long = 0L,
    val hasAmount: Boolean = false,
    val epochDay: Long,
    val merchant: String? = null,
    val category: String? = null,
    val sourceTextPreview: String = ""
) : Parcelable
