package com.shutterly.app.ui

import androidx.compose.ui.graphics.Color
import com.shutterly.app.data.Category

/** 各类别主题色（列表标签/统计条） */
fun Category.color(): Color = when (this) {
    Category.FOOD -> Color(0xFFE57373)
    Category.TRANSPORT -> Color(0xFF64B5F6)
    Category.TOBACCO_ALCOHOL -> Color(0xFFA1887F)
    Category.MEDICINE -> Color(0xFF4DB6AC)
    Category.TOPUP -> Color(0xFFFFB74D)
    Category.CLOTHING -> Color(0xFF9575CD)
}
