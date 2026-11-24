package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.remote.ApoyoDto
import com.albaconecta.app.data.remote.CandidatoDto
import com.albaconecta.app.data.remote.IndicadorDto
import com.albaconecta.app.data.remote.ProgramaDto
import com.albaconecta.app.data.repository.ContenidosRepository
import com.albaconecta.app.data.repository.TalentoRepository
import com.albaconecta.app.util.Conectividad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de una pantalla que carga contenido del backend. */
sealed interface EstadoContenido<out T> {
    data object Cargando : EstadoContenido<Nothing>
    data class Ok<T>(val datos: List<T>) : EstadoContenido<T>
    data class Error(val mensaje: String) : EstadoContenido<Nothing>
}

/** Programas publicados: alimentan la pantalla de Inicio. */
class ProgramasViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AlbaDatabase.getInstance(application)
    private val repositorio = ContenidosRepository(database)

    private val _estado = MutableStateFlow<EstadoContenido<ProgramaDto>>(EstadoContenido.Cargando)
    val estado: StateFlow<EstadoContenido<ProgramaDto>> = _estado.asStateFlow()

    init {
        cargar()
        observarReconexion(::sincronizar)
    }

    fun cargar() {
        _estado.value = EstadoContenido.Cargando
        viewModelScope.launch {
            _estado.value = try {
                EstadoContenido.Ok(repositorio.programas())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoContenido.Error("No pudimos cargar los programas. Revisa tu conexión.")
            }
        }
    }

    /** Fuerza sincronización manual o por reconexión, sin ocultar la caché existente. */
    fun sincronizar() {
        viewModelScope.launch {
            val sincronizado = repositorio.sincronizarProgramas()
            val datos = try {
                repositorio.programas()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            _estado.value = if (datos.isNotEmpty() || sincronizado) {
                EstadoContenido.Ok(datos)
            } else {
                EstadoContenido.Error("Sin conexión y sin datos guardados. Revisa tu conexión.")
            }
        }
    }
}

/** Indicadores: alimentan la pantalla de Impacto. */
class IndicadoresViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AlbaDatabase.getInstance(application)
    private val repositorio = ContenidosRepository(database)

    private val _estado = MutableStateFlow<EstadoContenido<IndicadorDto>>(EstadoContenido.Cargando)
    val estado: StateFlow<EstadoContenido<IndicadorDto>> = _estado.asStateFlow()

    init {
        cargar()
        observarReconexion(::sincronizar)
    }

    fun cargar() {
        _estado.value = EstadoContenido.Cargando
        viewModelScope.launch {
            _estado.value = try {
                EstadoContenido.Ok(repositorio.indicadores())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoContenido.Error("No pudimos cargar los indicadores. Revisa tu conexión.")
            }
        }
    }

    /** Fuerza sincronización manual o por reconexión, sin ocultar la caché existente. */
    fun sincronizar() {
        viewModelScope.launch {
            val sincronizado = repositorio.sincronizarIndicadores()
            val datos = try {
                repositorio.indicadores()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            _estado.value = if (datos.isNotEmpty() || sincronizado) {
                EstadoContenido.Ok(datos)
            } else {
                EstadoContenido.Error("Sin conexión y sin datos guardados. Revisa tu conexión.")
            }
        }
    }
}

/** Talento: candidatos anonimizados y apoyos desde `ms-talento`. */
class TalentoViewModel(application: Application) : AndroidViewModel(application) {
    private val repositorio = TalentoRepository()

    private val _candidatos = MutableStateFlow<EstadoContenido<CandidatoDto>>(EstadoContenido.Cargando)
    val candidatos: StateFlow<EstadoContenido<CandidatoDto>> = _candidatos.asStateFlow()

    private val _apoyos = MutableStateFlow<EstadoContenido<ApoyoDto>>(EstadoContenido.Cargando)
    val apoyos: StateFlow<EstadoContenido<ApoyoDto>> = _apoyos.asStateFlow()

    init {
        cargar()
        observarReconexion(::cargar)
    }

    fun cargar() {
        _candidatos.value = EstadoContenido.Cargando
        _apoyos.value = EstadoContenido.Cargando
        viewModelScope.launch {
            _candidatos.value = try {
                EstadoContenido.Ok(repositorio.candidatos())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoContenido.Error("No pudimos cargar los candidatos. Revisa tu conexión.")
            }
            _apoyos.value = try {
                EstadoContenido.Ok(repositorio.apoyos())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoContenido.Error("No pudimos cargar los apoyos.")
            }
        }
    }
}

/** Vuelve a cargar la pantalla cuando la red pasa de ausente a disponible. */
private fun AndroidViewModel.observarReconexion(alVolver: () -> Unit) {
    viewModelScope.launch {
        var anterior = Conectividad.enLinea.value
        Conectividad.enLinea.collect { enLinea ->
            if (enLinea && !anterior) alVolver()
            anterior = enLinea
        }
    }
}
