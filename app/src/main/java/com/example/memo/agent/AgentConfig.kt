package com.example.memo.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_settings")

object AgentConfig {

    private val KEY_AGENT_MODE = booleanPreferencesKey("is_agent_mode")
    private val KEY_AUTO_CONTROL = booleanPreferencesKey("is_auto_control_enabled")

    fun isAgentModeFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_AGENT_MODE] ?: false
        }
    }

    suspend fun toggleAgentMode(context: Context, newValue: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AGENT_MODE] = newValue
            if (!newValue) {
                preferences[KEY_AUTO_CONTROL] = false
            }
        }
    }

    suspend fun getAgentMode(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_AGENT_MODE] ?: false
    }

    fun isAutoControlEnabledFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_AUTO_CONTROL] ?: false
        }
    }

    suspend fun toggleAutoControl(context: Context, newValue: Boolean) {
        val agentMode = getAgentMode(context)
        if (!agentMode && newValue) return
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_CONTROL] = newValue
        }
    }

    suspend fun getAutoControlEnabled(context: Context): Boolean {
        val agentMode = context.dataStore.data.first()[KEY_AGENT_MODE] ?: false
        if (!agentMode) return false
        return context.dataStore.data.first()[KEY_AUTO_CONTROL] ?: false
    }
}