package com.example.memo.repository

import com.example.memo.data.Alarm
import com.example.memo.data.AlarmDao
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmDao: AlarmDao) {

    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun getAlarmById(id: Long): Alarm? = alarmDao.getAlarmById(id)

    suspend fun insertAlarm(alarm: Alarm): Long = alarmDao.insertAlarm(alarm)

    suspend fun deleteAlarm(alarm: Alarm): Int = alarmDao.deleteAlarm(alarm)

    suspend fun deleteAlarmById(id: Long): Int = alarmDao.deleteAlarmById(id)

    suspend fun updateAlarmEnabled(id: Long, enabled: Boolean): Int =
        alarmDao.updateAlarmEnabled(id, enabled)
}
