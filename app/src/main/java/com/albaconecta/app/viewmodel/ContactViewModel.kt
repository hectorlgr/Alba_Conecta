package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.remote.DetalleDto
import com.albaconecta.app.data.remote.SolicitudDto
import com.albaconecta.app.data.remote.TipoColaboracionDto
import com.albaconecta.app.data.repository.ResultadoEnvio
import com.albaconecta.app.data.repository.SolicitudRepository
import com.albaconecta.app.data.repository.TipoColaboracionRepository
import com.albaconecta.app.util.CameraUriFactory
import com.albaconecta.app.validation.ContactValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class ContactFormState(
    val nombre: String = "",
    val correo: String = "",
    val telefono: String = "",
    val mensaje: String = "",
    val representaEmpresa: Boolean = false,
    val cantidadTrabajadores: Int? = null,
    val fechaContacto: Long? = null,
    val autorizaContacto: Boolean = false,
    // Tipo de colaboración seleccionado (código del backend). Default "OTRO" para compatibilidad.
    val tipoColaboracionCodigo: String = "OTRO",
    // Ruta absoluta del adjunto en filesDir. Es parte del formulario para que el
    // envío (hoy local, mañana POST /solicitudes) no dependa de un estado de la pantalla.
    val fotoPath: String? = null
)

data class ContactErrors(
    val nombre: String? = null,
    val correo: String? = null,
    val telefono: String? = null,
    val mensaje: String? = null,
    val cantidadTrabajadores: String? = null,
    val fechaContacto: String? = null,
    val autorizaContacto: String? = null
)

data class ContactSubmitState(
    val success: Boolean = false,
    val enviando: Boolean = false,
    val folio: String? = null,
    val error: String? = null,
    val encolada: Boolean = false
)

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AlbaDatabase.getInstance(application)
    private val solicitudRepository = SolicitudRepository(database)
    private val tipoColaboracionRepository = TipoColaboracionRepository()

    private val _formState = MutableStateFlow(ContactFormState())
    val formState: StateFlow<ContactFormState> = _formState.asStateFlow()
    private val _errors = MutableStateFlow(ContactErrors())
    val errors: StateFlow<ContactErrors> = _errors.asStateFlow()
    private val _submitState = MutableStateFlow(ContactSubmitState())
    val submitState: StateFlow<ContactSubmitState> = _submitState.asStateFlow()

    // Tipos de colaboración desde el backend
    private val _tiposColaboracion = MutableStateFlow<List<TipoColaboracionDto>>(emptyList())
    val tiposColaboracion: StateFlow<List<TipoColaboracionDto>> = _tiposColaboracion.asStateFlow()
    private val _tiposLoading = MutableStateFlow(false)
    val tiposLoading: StateFlow<Boolean> = _tiposLoading.asStateFlow()

    // Clave de idempotencia para el envío actual. Se genera al iniciar el envío
    // y se reutiliza en reintentos. Se regenera solo tras éxito o descarte explícito.
    private var _claveIdempotencia: String? = null
    // Observador del folio de la solicitud actual; se cancela si se envía otra.
    private var folioJob: Job? = null

    init {
        // Encolar worker periódico al iniciar
        com.albaconecta.app.data.local.OutboxWorker.Companion.enqueuePeriodic(application)
        cargarTiposColaboracion()
    }

    /** Carga los tipos de colaboración desde el backend (una sola vez). */
    private fun cargarTiposColaboracion() {
        if (_tiposColaboracion.value.isNotEmpty()) return
        _tiposLoading.value = true
        viewModelScope.launch {
            try {
                val tipos = tipoColaboracionRepository.obtener()
                _tiposColaboracion.value = tipos
            } catch (_: Exception) {
                // Silencioso: se usa "OTRO" como fallback
            } finally {
                _tiposLoading.value = false
            }
        }
    }

    /** Actualiza el tipo de colaboración seleccionado. */
    fun updateTipoColaboracion(codigo: String) {
        edit { it.copy(tipoColaboracionCodigo = codigo) }
    }

    private fun edit(transform: (ContactFormState) -> ContactFormState) {
        // No se descarta el estado de envío mientras hay una solicitud en vuelo: si no, el
        // botón volvería a habilitarse y un segundo toque crearía otra solicitud.
        if (!_submitState.value.enviando && !_submitState.value.encolada) _submitState.value = ContactSubmitState()
        _formState.update(transform)
    }

    fun updateNombre(value: String) {
        edit { it.copy(nombre = value) }
        if (_errors.value.nombre != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(nombre = validation.nombre) }
        }
    }

    fun updateCorreo(value: String) {
        edit { it.copy(correo = value) }
        if (_errors.value.correo != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(correo = validation.correo) }
        }
    }

    fun updateTelefono(value: String) {
        edit { it.copy(telefono = value) }
        if (_errors.value.telefono != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(telefono = validation.telefono) }
        }
    }

    fun updateMensaje(value: String) {
        edit { it.copy(mensaje = value) }
        if (_errors.value.mensaje != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(mensaje = validation.mensaje) }
        }
    }

    fun updateRepresentaEmpresa(value: Boolean) {
        edit {
            it.copy(representaEmpresa = value, cantidadTrabajadores = if (value) it.cantidadTrabajadores else null)
        }
        if (_errors.value.cantidadTrabajadores != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(cantidadTrabajadores = validation.cantidadTrabajadores) }
        }
    }

    fun updateCantidadTrabajadores(text: String) {
        if (text.isEmpty()) {
            edit { it.copy(cantidadTrabajadores = null) }
        } else if (text.all { it in '0'..'9' }) {
            val number = text.toIntOrNull() ?: return
            edit { it.copy(cantidadTrabajadores = number) }
        } else {
            return
        }
        if (_errors.value.cantidadTrabajadores != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(cantidadTrabajadores = validation.cantidadTrabajadores) }
        }
    }

    fun updateFechaContacto(value: Long?) {
        edit { it.copy(fechaContacto = value) }
        if (_errors.value.fechaContacto != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(fechaContacto = validation.fechaContacto) }
        }
    }

    fun updateAutorizaContacto(value: Boolean) {
        edit { it.copy(autorizaContacto = value) }
        if (_errors.value.autorizaContacto != null) {
            val validation = ContactValidator.validate(_formState.value)
            _errors.update { it.copy(autorizaContacto = validation.autorizaContacto) }
        }
    }

    /** Foto tomada o quitada desde la pantalla. El archivo ya vive en filesDir. */
    fun updateFoto(path: String?) {
        edit { it.copy(fotoPath = path) }
    }

    fun submit() {
        // Doble toque = dos claves de idempotencia distintas = dos solicitudes reales.
        if (_submitState.value.enviando || _submitState.value.encolada) return
        folioJob?.cancel()

        val result = ContactValidator.validate(_formState.value)
        _errors.value = result
        if (result != ContactErrors()) {
            _submitState.value = ContactSubmitState()
            return
        }

        val form = _formState.value
        _submitState.value = ContactSubmitState(encolada = true)
        
        // Generar clave de idempotencia si no existe (primer envío) o reutilizar (reintento)
        if (_claveIdempotencia == null) {
            _claveIdempotencia = UUID.randomUUID().toString()
        }
        val clave = _claveIdempotencia!!

        viewModelScope.launch {
            when (val resultado = solicitudRepository.crear(aSolicitud(form), clave)) {
                is ResultadoEnvio.Encolada -> {
                    // Guardada en outbox: Worker la enviará en background
                    borrarFotoTemporal(form.fotoPath)
                    _formState.value = ContactFormState()
                    _errors.value = ContactErrors()
                    _submitState.value = ContactSubmitState(encolada = true, success = true)
                    _claveIdempotencia = null // Nueva clave para próxima solicitud
                    // El folio lo emite el backend: disparamos el Worker ya y observamos la BD hasta que llegue.
                    com.albaconecta.app.data.local.OutboxWorker.enqueueNow(getApplication())
                    folioJob = viewModelScope.launch {
                        database.solicitudOutboxDao().observarFolio(clave)
                            .filterNotNull()
                            .firstOrNull()
                            ?.let { folio -> _submitState.update { it.copy(folio = folio) } }
                    }
                }
                is ResultadoEnvio.Exito -> {
                    // Ya estaba enviada (reintento con misma clave)
                    borrarFotoTemporal(form.fotoPath)
                    _formState.value = ContactFormState()
                    _errors.value = ContactErrors()
                    _submitState.value = ContactSubmitState(success = true, folio = resultado.folio)
                    _claveIdempotencia = null
                }
                is ResultadoEnvio.Fallo -> {
                    // Fallo no reintentable (ej. 409 payload distinto): permitir descarte
                    _errors.value = erroresDelServidor(resultado.campos)
                    _submitState.value = ContactSubmitState(error = resultado.mensaje)
                    // _claveIdempotencia se mantiene para que el usuario vea el error y descarte
                }
            }
        }
    }

    /** Descarta el formulario actual y genera una nueva clave para la próxima solicitud. */
    fun descartarFormulario() {
        folioJob?.cancel() // evita que un folio tardío aparezca en el formulario nuevo
        _formState.value = ContactFormState()
        _errors.value = ContactErrors()
        _submitState.value = ContactSubmitState()
        _claveIdempotencia = null
    }

    /**
     * Borra el archivo de foto temporal en filesDir/contact_photos/ tras envío exitoso.
     * Se ejecuta en IO para no bloquear el hilo principal.
     */
    private fun borrarFotoTemporal(path: String?) {
        if (path.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CameraUriFactory.delete(path)
            } catch (_: Exception) {
                // Best effort: si no se puede borrar, no es crítico
            }
        }
    }

    /**
     * Formulario → contrato del API. El backend no tiene campos para "representa empresa",
     * cantidad de trabajadores ni fecha preferida: viajan como texto en el detalle.
     * El tipo de colaboración se toma del selector (cargado desde el backend).
     */
    private fun aSolicitud(form: ContactFormState) = SolicitudDto(
        nombreContacto = form.nombre.trim(),
        email = form.correo.trim().lowercase(),
        telefono = form.telefono.trim(),
        mensaje = form.mensaje.trim(),
        aceptaPrivacidad = form.autorizaContacto,
        detalles = listOf(
            DetalleDto(
                tipoColaboracionCodigo = form.tipoColaboracionCodigo,
                descripcion = descripcion(form).take(500) // máximo del contrato
            )
        )
    )

    private fun descripcion(form: ContactFormState): String = buildList {
        if (form.representaEmpresa) add("Representa a una empresa")
        form.cantidadTrabajadores?.let { add("Trabajadores: $it") }
        form.fechaContacto?.let { add("Fecha preferida: ${formatearFecha(it)}") }
    }.joinToString(". ")

    // El DatePicker entrega medianoche UTC: se formatea en UTC, no en la zona del equipo.
    private fun formatearFecha(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    /** Errores por campo del backend (nombres del API) a los campos del formulario local. */
    private fun erroresDelServidor(campos: Map<String, String>) = ContactErrors(
        nombre = campos["nombre_contacto"],
        correo = campos["email"],
        telefono = campos["telefono"],
        mensaje = campos["mensaje"]
    )
}
