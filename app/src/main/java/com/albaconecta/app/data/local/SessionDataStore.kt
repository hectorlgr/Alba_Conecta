package com.albaconecta.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_preferences")

class SessionDataStore(context: Context) {
    private val dataStore = context.applicationContext.sessionDataStore

    val loggedUserId: Flow<Int?> = dataStore.data.map { preferences ->
        preferences[LOGGED_USER_ID]
    }

    suspend fun saveSession(loggedUserId: Int) {
        require(loggedUserId > 0) { "loggedUserId must be greater than zero" }
        dataStore.edit { preferences ->
            preferences[LOGGED_USER_ID] = loggedUserId
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(LOGGED_USER_ID)
        }
    }

    private companion object {
        val LOGGED_USER_ID = intPreferencesKey("logged_user_id")
    }
}
