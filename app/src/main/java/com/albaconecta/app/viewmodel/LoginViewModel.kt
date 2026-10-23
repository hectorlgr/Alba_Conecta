package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.data.repository.UserRepository
import com.albaconecta.app.util.BiometricAuthenticator
import com.albaconecta.app.util.PasswordHasher
import com.albaconecta.app.util.shouldShowBiometricLogin
import com.albaconecta.app.validation.LoginValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginFormState(
    val correo: String = "",
    val password: String = ""
)

data class LoginErrors(
    val correo: String? = null,
    val password: String? = null
)

data class LoginState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Estado del acceso biometrico en la pantalla de login.
 * Vive en un StateFlow separado porque login() reasigna LoginState completo,
 * y cualquier campo nuevo que le agregaramos ahi se perderia en cada intento.
 */
data class BiometricState(
    val enabled: Boolean = false,   // el usuario lo activo desde Perfil
    val available: Boolean = false, // hay sensor y una huella fuerte (Clase 3) inscrita
    val userExists: Boolean = false // hay una cuenta en este dispositivo
) {
    /** Delega en la funcion pura de util: la misma logica que se testea. */
    val showLoginButton: Boolean
        get() = shouldShowBiometricLogin(enabled, available, userExists)
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val userRepository by lazy {
        val database = AlbaDatabase.getInstance(application)
        UserRepository(database.userDao())
    }
    private val sessionDataStore by lazy { SessionDataStore(application) }

    private val _formState = MutableStateFlow(LoginFormState())
    val formState: StateFlow<LoginFormState> = _formState.asStateFlow()

    private val _errors = MutableStateFlow(LoginErrors())
    val errors: StateFlow<LoginErrors> = _errors.asStateFlow()

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _biometricState = MutableStateFlow(BiometricState())
    val biometricState: StateFlow<BiometricState> = _biometricState.asStateFlow()

    init {
        // Disponibilidad + activacion: solo se necesita el Context del Application,
        // nunca la Activity (el ViewModel sobrevive a la rotacion de pantalla).
        viewModelScope.launch {
            try {
                val enabled = sessionDataStore.biometricLoginEnabled.first()
                val userExists = userRepository.getExistingUser() != null
                val available = BiometricAuthenticator.canAuthenticate(getApplication<Application>())
                _biometricState.value = BiometricState(
                    enabled = enabled,
                    available = available,
                    userExists = userExists
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Ante cualquier fallo el estado por defecto oculta el boton (fail-safe).
            }
        }
    }

    fun updateCorreo(value: String) {
        _formState.update { it.copy(correo = value) }
        if (_errors.value.correo != null) {
            val validation = LoginValidator.validate(_formState.value)
            _errors.update { it.copy(correo = validation.correo) }
        }
        _loginState.update { it.copy(errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _formState.update { it.copy(password = value) }
        if (_errors.value.password != null) {
            val validation = LoginValidator.validate(_formState.value)
            _errors.update { it.copy(password = validation.password) }
        }
        _loginState.update { it.copy(errorMessage = null) }
    }

    fun login() {
        if (_loginState.value.loading || _loginState.value.success) return
        val form = _formState.value
        val validation = LoginValidator.validate(form)
        _errors.value = validation
        _loginState.value = LoginState()
        if (validation != LoginErrors()) return

        _loginState.value = LoginState(loading = true)
        viewModelScope.launch {
            try {
                val user = userRepository.getUserByEmail(form.correo)
                val validPassword = user != null && withContext(Dispatchers.Default) {
                    PasswordHasher.verify(
                        password = form.password,
                        storedHash = user.passwordHash,
                        storedSalt = user.passwordSalt
                    )
                }
                if (user == null || !validPassword) {
                    _loginState.value = LoginState(errorMessage = "Correo o contraseña incorrectos.")
                    return@launch
                }

                sessionDataStore.saveSession(user.id)
                _loginState.value = LoginState(success = true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _loginState.value = LoginState(
                    errorMessage = "No se pudo iniciar sesión. Inténtalo nuevamente."
                )
            } finally {
                _loginState.update { it.copy(loading = false) }
            }
        }
    }

    /**
     * La Screen llama esto cuando el sistema reporta una biometria exitosa.
     * Converge en el mismo 'success = true' del login con contrasena, asi el
     * LaunchedEffect de LoginScreen navega sin ningun cambio.
     */
    fun onBiometricAuthenticated() {
        if (_loginState.value.loading || _loginState.value.success) return
        _loginState.value = LoginState(loading = true)
        viewModelScope.launch {
            try {
                // Una cuenta por dispositivo: el usuario existente es el que entra.
                val user = userRepository.getExistingUser()
                if (user == null) {
                    _loginState.value = LoginState(errorMessage = "No hay una cuenta en este dispositivo.")
                    return@launch
                }
                sessionDataStore.saveSession(user.id)
                _loginState.value = LoginState(success = true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _loginState.value = LoginState(
                    errorMessage = "No se pudo iniciar sesión. Inténtalo nuevamente."
                )
            } finally {
                _loginState.update { it.copy(loading = false) }
            }
        }
    }

    /** Muestra en el formulario un mensaje de error del sensor (lockout, hardware, etc.). */
    fun onBiometricError(message: String) {
        if (_loginState.value.success) return
        _loginState.update { it.copy(errorMessage = message) }
    }
}
