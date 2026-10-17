package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.SessionDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AppSessionState(
    val loading: Boolean = true,
    val loggedUserId: Int? = null,
    val error: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionDataStore = SessionDataStore(application)

    private val _sessionState = MutableStateFlow(AppSessionState())
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionDataStore.loggedUserId
                .catch {
                    _sessionState.value = AppSessionState(
                        loading = false,
                        loggedUserId = null,
                        error = true
                    )
                }
                .collect { loggedUserId ->
                    _sessionState.value = AppSessionState(
                        loading = false,
                        loggedUserId = loggedUserId,
                        error = false
                    )
                }
        }
    }
}
