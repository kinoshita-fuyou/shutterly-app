package com.shutterly.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class RecordRepository(private val dao: RecordDao) {

    fun observeAll(): Flow<List<Record>> = dao.observeAll()

    fun observeByMonth(startEpochDay: Long, endEpochDay: Long): Flow<List<Record>> =
        dao.observeByMonth(startEpochDay, endEpochDay)

    suspend fun add(record: Record): Long = dao.insert(record)

    suspend fun delete(id: Long) = dao.deleteById(id)

    companion object {
        fun get(context: Context): RecordRepository =
            RecordRepository(AppDatabase.get(context).recordDao())
    }
}
