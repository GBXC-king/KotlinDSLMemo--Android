package com.example.memo.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memo.data.Alarm
import com.example.memo.data.AlarmHelper
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.getAlarmRepository()

    val allAlarms: Flow<List<Alarm>> = repository.allAlarms

    fun addAlarm(alarm: Alarm, onInserted: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insertAlarm(alarm)
            AlarmHelper.rescheduleAllAlarms(getApplication())
            onInserted?.invoke(id)
        }
    }

    fun updateAlarm(alarm: Alarm, onUpdated: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insertAlarm(alarm)
            AlarmHelper.rescheduleAllAlarms(getApplication())
            onUpdated?.invoke(id)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
            AlarmHelper.rescheduleAllAlarms(getApplication())
        }
    }

    fun deleteAlarms(alarms: List<Alarm>) {
        viewModelScope.launch {
            alarms.forEach { repository.deleteAlarm(it) }
            AlarmHelper.rescheduleAllAlarms(getApplication())
        }
    }

    fun toggleAlarmEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAlarmEnabled(alarm.id, enabled)
            AlarmHelper.rescheduleAllAlarms(getApplication())
        }
    }

    suspend fun getAlarmById(id: Long): Alarm? = repository.getAlarmById(id)
}
