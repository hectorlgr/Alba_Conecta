package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.data.repository.UserRepository
import com.albaconecta.app.util.PasswordHasher
import com.albaconecta.app.validation.LoginValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun updateCorreo(value: String) { _formState.update { it.copy(correo = value) } }
    fun updatePassword(value: String) { _formState.update { it.copy(password = value) } }

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
}
