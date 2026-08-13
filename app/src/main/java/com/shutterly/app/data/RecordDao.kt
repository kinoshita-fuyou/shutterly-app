package com.shutterly.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    /** 全部记录，按日期倒序、同日内按创建倒序 */
    @Query("SELECT * FROM records ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<Record>>

    /** 某月区间记录（含首尾日） */
    @Query("SELECT * FROM records WHERE epochDay >= :start AND epochDay <= :end ORDER BY epochDay DESC, id DESC")
    fun observeByMonth(start: Long, end: Long): Flow<List<Record>>

    @Insert
    suspend fun insert(record: Record): Long

    @Delete
    suspend fun delete(record: Record)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
