package com.albaconecta.app.viewmodel

import androidx.lifecycle.ViewModel
import com.albaconecta.app.validation.RegisterValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RegisterFormState(
    val nombre: String = "",
    val apellido: String = "",
    val rut: String = "",
    val correo: String = "",
    val telefono: String = "",
    val fechaNacimiento: Long? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val aceptaTerminos: Boolean = false
)

data class RegisterErrors(
    val nombre: String? = null,
    val apellido: String? = null,
    val rut: String? = null,
    val correo: String? = null,
    val telefono: String? = null,
    val fechaNacimiento: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null,
    val aceptaTerminos: String? = null
)

class RegisterViewModel : ViewModel() {
    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    private val _errors = MutableStateFlow(RegisterErrors())
    val errors: StateFlow<RegisterErrors> = _errors.asStateFlow()

    fun updateNombre(value: String) { _formState.update { it.copy(nombre = value) } }
    fun updateApellido(value: String) { _formState.update { it.copy(apellido = value) } }
    fun updateRut(value: String) { _formState.update { it.copy(rut = value) } }
    fun updateCorreo(value: String) { _formState.update { it.copy(correo = value) } }
    fun updateTelefono(value: String) { _formState.update { it.copy(telefono = value) } }
    fun updateFechaNacimiento(value: Long?) { _formState.update { it.copy(fechaNacimiento = value) } }
    fun updatePassword(value: String) { _formState.update { it.copy(password = value) } }
    fun updateConfirmPassword(value: String) { _formState.update { it.copy(confirmPassword = value) } }
    fun updateAceptaTerminos(value: Boolean) { _formState.update { it.copy(aceptaTerminos = value) } }

    fun validateForm(): Boolean {
        val result = RegisterValidator.validate(_formState.value)
        _errors.value = result
        return result == RegisterErrors()
    }
}
