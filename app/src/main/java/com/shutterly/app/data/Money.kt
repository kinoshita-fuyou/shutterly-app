package com.shutterly.app.data

import java.math.BigDecimal

/** 金额单位换算工具：界面以“元”输入/显示，入库以“分”存储 */
object Money {

    private val VALID = Regex("""^\d+(\.\d{1,2})?$""")

    /** "18.5" / "18.50" / "0.5" → 分；非法或 ≤0 返回 null */
    fun yuanToFen(input: String): Long? {
        val clean = input.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
        if (!VALID.matches(clean)) return null
        val fen = (BigDecimal(clean) * BigDecimal(100)).toLong()
        return if (fen > 0) fen else null
    }

    /** 分 → "1,234.50" */
    fun fenToYuan(fen: Long): String = String.format("%,.2f", fen / 100.0)
}
