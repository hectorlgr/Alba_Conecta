package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.OutboxWorker
import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.util.Conectividad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppSessionState(
    val loading: Boolean = true,
    val usuarioId: String? = null,
    val error: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionDataStore = SessionDataStore(application)
    private val outboxDao = AlbaDatabase.getInstance(application).solicitudOutboxDao()

    private val _sessionState = MutableStateFlow(AppSessionState())
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    /** Estado de red y cola pendiente: alimentan el aviso global de la app. */
    val enLinea: StateFlow<Boolean> = Conectividad.enLinea
    val pendientes: StateFlow<Int> = outboxDao.observarPendientes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    init {
        Conectividad.iniciar(application)

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

        // Recuperar la red (o encontrar solicitudes guardadas al arrancar) dispara el worker.
        viewModelScope.launch {
            var anterior: Boolean? = null
            Conectividad.enLinea.collect { enLinea ->
                if (enLinea && anterior == false) OutboxWorker.enqueueNow(application)
                anterior = enLinea
            }
        }
        viewModelScope.launch {
            outboxDao.observarPendientes().distinctUntilChanged().collect { cantidad ->
                if (cantidad > 0) OutboxWorker.enqueueNow(application)
            }
        }
    }
}
