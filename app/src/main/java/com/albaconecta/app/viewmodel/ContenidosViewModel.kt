package com.albaconecta.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.remote.IndicadorDto
import com.albaconecta.app.data.remote.ProgramaDto
import com.albaconecta.app.data.repository.ContenidosRepository
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
class ProgramasViewModel : ViewModel() {
    private val repositorio = ContenidosRepository()

    private val _estado = MutableStateFlow<EstadoContenido<ProgramaDto>>(EstadoContenido.Cargando)
    val estado: StateFlow<EstadoContenido<ProgramaDto>> = _estado.asStateFlow()

    init {
        cargar()
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
}

/** Indicadores: alimentan la pantalla de Impacto. */
class IndicadoresViewModel : ViewModel() {
    private val repositorio = ContenidosRepository()

    private val _estado = MutableStateFlow<EstadoContenido<IndicadorDto>>(EstadoContenido.Cargando)
    val estado: StateFlow<EstadoContenido<IndicadorDto>> = _estado.asStateFlow()

    init {
        cargar()
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
}
