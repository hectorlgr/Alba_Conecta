package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.UserEntity
import com.albaconecta.app.data.repository.UserRepository
import com.albaconecta.app.util.PasswordHasher
import com.albaconecta.app.validation.RegisterValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

data class RegistrationState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null
)

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val userRepository by lazy {
        val database = AlbaDatabase.getInstance(application)
        UserRepository(database.userDao())
    }

    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    private val _errors = MutableStateFlow(RegisterErrors())
    val errors: StateFlow<RegisterErrors> = _errors.asStateFlow()

    private val _registrationState = MutableStateFlow(RegistrationState())
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    // Survive screen recreation without clearing the visual success state.
    private var registrationNotificationStarted = false
    private var registrationNotificationConsumed = false

    fun beginRegistrationNotification(): Boolean {
        if (!_registrationState.value.success || registrationNotificationStarted) return false
        registrationNotificationStarted = true
        return true
    }

    fun consumeRegistrationNotification(): Boolean {
        if (!registrationNotificationStarted || registrationNotificationConsumed) return false
        // Consume before publishing, including denial or a failed publication.
        registrationNotificationConsumed = true
        return true
    }

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

    fun register() {
        if (_registrationState.value.loading || _registrationState.value.success) return
        _registrationState.value = RegistrationState()
        if (!validateForm()) return

        val form = _formState.value
        _registrationState.value = RegistrationState(loading = true)
        viewModelScope.launch {
            try {
                if (userRepository.getExistingUser() != null) {
                    _registrationState.value = RegistrationState(
                        errorMessage = "Ya existe una cuenta registrada en este dispositivo."
                    )
                    return@launch
                }

                val credentials = withContext(Dispatchers.Default) {
                    PasswordHasher.generate(form.password)
                }
                val user = UserEntity(
                    id = 0,
                    nombre = form.nombre.trim(),
                    apellido = form.apellido.trim(),
                    rut = form.rut.trim(),
                    correo = form.correo,
                    telefono = form.telefono.trim(),
                    fechaNacimiento = requireNotNull(form.fechaNacimiento),
                    passwordHash = credentials.hash,
                    passwordSalt = credentials.salt
                )
                userRepository.insertUser(user)
                _registrationState.value = RegistrationState(success = true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _registrationState.value = RegistrationState(
                    errorMessage = "No se pudo crear la cuenta. Inténtalo nuevamente."
                )
            } finally {
                _registrationState.update { it.copy(loading = false) }
            }
        }
    }
}
