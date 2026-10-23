package com.albaconecta.app.viewmodel

import androidx.lifecycle.ViewModel
import com.albaconecta.app.validation.ContactValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ContactFormState(
    val nombre: String = "",
    val correo: String = "",
    val telefono: String = "",
    val mensaje: String = "",
    val representaEmpresa: Boolean = false,
    val cantidadTrabajadores: Int? = null,
    val fechaContacto: Long? = null,
    val autorizaContacto: Boolean = false
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

data class ContactSubmitState(val success: Boolean = false)

class ContactViewModel : ViewModel() {
    private val _formState = MutableStateFlow(ContactFormState())
    val formState: StateFlow<ContactFormState> = _formState.asStateFlow()
    private val _errors = MutableStateFlow(ContactErrors())
    val errors: StateFlow<ContactErrors> = _errors.asStateFlow()
    private val _submitState = MutableStateFlow(ContactSubmitState())
    val submitState: StateFlow<ContactSubmitState> = _submitState.asStateFlow()

    private fun edit(transform: (ContactFormState) -> ContactFormState) {
        _submitState.value = ContactSubmitState()
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

    fun submit() {
        val result = ContactValidator.validate(_formState.value)
        _errors.value = result
        if (result != ContactErrors()) {
            _submitState.value = ContactSubmitState()
            return
        }
        // Local demonstration only: no request is sent or persisted.
        _formState.value = ContactFormState()
        _errors.value = ContactErrors()
        _submitState.value = ContactSubmitState(success = true)
    }
}
