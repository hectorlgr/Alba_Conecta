package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.SessionDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class AppSessionState(
    val loading: Boolean = true,
    val usuarioId: String? = null,
    val error: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionDataStore = SessionDataStore(application)

    private val _sessionState = MutableStateFlow(AppSessionState())
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionDataStore.sesion
                .map { sesion -> sesion?.usuarioId }
                .catch {
                    _sessionState.value = AppSessionState(
                        loading = false,
                        usuarioId = null,
                        error = true
                    )
                }
                .collect { usuarioId ->
                    _sessionState.value = AppSessionState(
                        loading = false,
                        usuarioId = usuarioId,
                        error = false
                    )
                }
        }
    }
}
