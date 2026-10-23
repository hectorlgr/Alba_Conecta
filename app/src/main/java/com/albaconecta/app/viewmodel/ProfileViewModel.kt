package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.data.local.UserEntity
import com.albaconecta.app.data.repository.UserRepository
import com.albaconecta.app.util.BiometricAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val loading: Boolean = true,
    val user: UserEntity? = null,
    val errorMessage: String? = null,
    val logoutLoading: Boolean = false,
    val logoutSuccess: Boolean = false,
    val deleteLoading: Boolean = false,
    val deleteSuccess: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionDataStore = SessionDataStore(application)
    private val userRepository by lazy {
        val database = AlbaDatabase.getInstance(application)
        UserRepository(database.userDao())
    }

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val loggedUserId = sessionDataStore.loggedUserId.first()
                if (loggedUserId == null) {
                    _state.value = ProfileState(
                        loading = false,
                        errorMessage = "No hay una sesión activa."
                    )
                } else {
                    val user = userRepository.getUserById(loggedUserId)
                    _state.value = ProfileState(
                        loading = false,
                        user = user,
                        errorMessage = if (user == null) "No se pudo cargar la cuenta." else null
                    )
                    // Despues del estado base se agregan los datos biometricos con
                    // copy(): las asignaciones completas de arriba los dejarian en false.
                    loadBiometricPreferences()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.value = ProfileState(
                    loading = false,
                    errorMessage = "No se pudo cargar la cuenta."
                )
            }
        }
    }

    /** Carga si el usuario activo la huella y si el dispositivo la soporta. */
    private suspend fun loadBiometricPreferences() {
        val enabled = sessionDataStore.biometricLoginEnabled.first()
        val available = BiometricAuthenticator.canAuthenticate(getApplication<Application>())
        _state.update { it.copy(biometricEnabled = enabled, biometricAvailable = available) }
    }

    /** Activa o desactiva el login biometrico (llamado desde el Switch del Perfil). */
    fun setBiometricLoginEnabled(enabled: Boolean) {
        val current = _state.value
        if (current.loading || current.logoutLoading || current.deleteLoading ||
            current.logoutSuccess || current.deleteSuccess
        ) return
        if (enabled == current.biometricEnabled) return
        viewModelScope.launch {
            try {
                sessionDataStore.setBiometricLoginEnabled(enabled)
                _state.update { it.copy(biometricEnabled = enabled) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.update {
                    it.copy(errorMessage = "No se pudo actualizar el acceso con huella. Inténtalo nuevamente.")
                }
            }
        }
    }

    fun logout() {
        val current = _state.value
        if (current.loading || current.logoutLoading || current.deleteLoading ||
            current.logoutSuccess || current.deleteSuccess) return
        _state.update { it.copy(logoutLoading = true, logoutSuccess = false, errorMessage = null) }
        viewModelScope.launch {
            try {
                sessionDataStore.clearSession()
                _state.update { it.copy(user = null, logoutLoading = false, logoutSuccess = true) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        logoutLoading = false,
                        logoutSuccess = false,
                        errorMessage = "No se pudo cerrar sesión. Inténtalo nuevamente."
                    )
                }
            } finally {
                _state.update { it.copy(logoutLoading = false) }
            }
        }
    }

    fun deleteAccount() {
        val current = _state.value
        if (current.loading || current.logoutLoading || current.deleteLoading ||
            current.logoutSuccess || current.deleteSuccess) return
        val user = current.user ?: return
        _state.update { it.copy(deleteLoading = true, deleteSuccess = false, errorMessage = null) }

        viewModelScope.launch {
            try {
                sessionDataStore.clearSession()
                try {
                    userRepository.deleteUser(user)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (deleteException: Exception) {
                    try {
                        sessionDataStore.saveSession(user.id)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        // Restoration is best-effort; deletion must still be reported as failed.
                    }
                    throw deleteException
                }
                // La credencial biometrica muere con la cuenta. Borrado best-effort:
                // si fallara, ya no existiria un usuario al que iniciar sesion con huella.
                try {
                    sessionDataStore.setBiometricLoginEnabled(false)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    // Se ignora deliberadamente.
                }
                _state.update {
                    it.copy(
                        user = null,
                        deleteLoading = false,
                        deleteSuccess = true,
                        errorMessage = null,
                        biometricEnabled = false
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        deleteSuccess = false,
                        errorMessage = "No se pudo eliminar la cuenta. Inténtalo nuevamente."
                    )
                }
            } finally {
                _state.update { it.copy(deleteLoading = false) }
            }
        }
    }
}
