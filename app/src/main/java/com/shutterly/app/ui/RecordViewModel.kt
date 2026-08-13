package com.shutterly.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shutterly.app.data.Record
import com.shutterly.app.data.RecordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModel(private val repo: RecordRepository) : ViewModel() {

    /** 全部记录（主页列表） */
    val allRecords: StateFlow<List<Record>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 统计页月份偏移：0=本月，-1=上月… */
    val monthOffset = MutableStateFlow(0)

    /** 当前偏移月份的记录（主页本月卡片 / 统计页共用） */
    val monthRecords: StateFlow<List<Record>> = monthOffset
        .flatMapLatest { offset ->
            val m = LocalDate.now().plusMonths(offset.toLong())
            repo.observeByMonth(
                m.withDayOfMonth(1).toEpochDay(),
                m.withDayOfMonth(m.lengthOfMonth()).toEpochDay()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(record: Record) {
        viewModelScope.launch { repo.add(record) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                RecordViewModel(RecordRepository.get(app.applicationContext))
            }
        }
    }
}
