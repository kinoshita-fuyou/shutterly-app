package com.shutterly.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条账目记录。
 * 金额以“分”为单位存 Long，避免浮点误差。
 * epochDay 为 LocalDate.toEpochDay()，便于按月区间查询。
 */
@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 金额（分，正数，均为支出） */
    val amountFen: Long,
    /** 记账日期：LocalDate.toEpochDay() */
    val epochDay: Long,
    /** 商户名，可空 */
    val merchant: String?,
    /** 类别：Category.name */
    val category: String,
    /** 来源：manual=手动录入，screenshot=截图识别 */
    val source: String = "manual",
    val createdAt: Long = System.currentTimeMillis()
)
