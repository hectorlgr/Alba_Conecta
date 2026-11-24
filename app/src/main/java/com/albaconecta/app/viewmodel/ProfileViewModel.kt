package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.SesionLocal
import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.data.local.UserEntity
import com.albaconecta.app.data.repository.AuthRepository
import com.albaconecta.app.data.repository.UserRepository
import com.albaconecta.app.util.BiometricAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Datos de perfil que sí puede ver la UI: sin `passwordHash` ni `passwordSalt`.
 * RUT, teléfono y fecha son nulos para cuentas que solo existen en el servidor
 * (el backend no guarda esos datos).
 */
data class ProfileUi(
    val nombre: String,
    val apellido: String,
    val rut: String,
    val correo: String,
    val telefono: String,
    val fechaNacimiento: Long?
)

data class ProfileState(
    val loading: Boolean = true,
    val user: ProfileUi? = null,
    val errorMessage: String? = null,
    val logoutLoading: Boolean = false,
    val logoutSuccess: Boolean = false,
    val deleteLoading: Boolean = false,
    val deleteSuccess: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val editing: Boolean = false,
    val saveLoading: Boolean = false,
    val saveSuccess: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionDataStore = SessionDataStore(application)
    private val authRepository = AuthRepository(sessionDataStore)
    private val userRepository by lazy {
        val database = AlbaDatabase.getInstance(application)
        UserRepository(database.userDao())
    }

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    // La entidad local completa no sale del ViewModel.
    private var fichaLocal: UserEntity? = null

    init {
        viewModelScope.launch {
            try {
                val sesion = sessionDataStore.sesion.first()
                if (sesion == null) {
                    _state.value = ProfileState(
                        loading = false,
                        errorMessage = "No hay una sesión activa."
                    )
                } else {
                    fichaLocal = userRepository.getUserByEmail(sesion.email)
                    _state.value = ProfileState(loading = false, user = aProfileUi(sesion))
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

    /** La sesión manda (nombre, correo, rol vienen del servidor); la ficha local aporta el resto. */
    private fun aProfileUi(sesion: SesionLocal) = ProfileUi(
        nombre = fichaLocal?.nombre ?: sesion.nombre,
        apellido = fichaLocal?.apellido.orEmpty(),
        rut = fichaLocal?.rut.orEmpty(),
        correo = sesion.email,
        telefono = fichaLocal?.telefono.orEmpty(),
        fechaNacimiento = fichaLocal?.fechaNacimiento
    )

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
                val ok = authRepository.activarBiometria(enabled)
                _state.update {
                    if (ok) it.copy(biometricEnabled = enabled)
                    else it.copy(errorMessage = "Inicia sesión para activar el acceso con huella.")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.update {
                    it.copy(errorMessage = "No se pudo actualizar el acceso con huella. Inténtalo nuevamente.")
                }
            }
        }
    }

    /** Entra en modo edición (nombre y teléfono). */
    fun empezarEdicion() {
        _state.update { it.copy(editing = true, errorMessage = null, saveSuccess = false) }
    }

    /** Cancela la edición y restaura los valores originales. */
    fun cancelarEdicion() {
        val user = _state.value.user
        val ficha = fichaLocal
        if (user != null && ficha != null) {
            _state.update { it.copy(editing = false, errorMessage = null, saveSuccess = false) }
        }
    }

    /** Actualiza el nombre en el estado local (solo UI). */
    fun actualizarNombre(nombre: String) {
        _state.update { state ->
            state.user?.let { u ->
                state.copy(user = u.copy(nombre = nombre.trim()))
            } ?: state
        }
    }

    /** Actualiza el teléfono en el estado local (solo UI). */
    fun actualizarTelefono(telefono: String) {
        _state.update { state ->
            state.user?.let { u ->
                state.copy(user = u.copy(telefono = telefono.trim()))
            } ?: state
        }
    }

    /** Guarda los cambios en Room (persistencia local). */
    fun guardarCambios() {
        val current = _state.value
        if (current.loading || current.saveLoading || !current.editing || current.user == null || fichaLocal == null) return
        _state.update { it.copy(saveLoading = true, errorMessage = null, saveSuccess = false) }

        viewModelScope.launch {
            try {
                val user = current.user!!
                val ficha = fichaLocal!!.copy(
                    nombre = user.nombre.trim(),
                    telefono = user.telefono.trim()
                )
                userRepository.updateUser(ficha)
                fichaLocal = ficha
                _state.update { it.copy(saveLoading = false, editing = false, saveSuccess = true, errorMessage = null) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.update { it.copy(saveLoading = false, saveSuccess = false, errorMessage = "No se pudieron guardar los cambios. Inténtalo nuevamente.") }
            }
        }
    }

    /** Resetea el flag de éxito (llamado desde UI tras mostrar feedback). */
    fun resetSaveSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }

    fun logout() {
        val current = _state.value
        if (current.loading || current.logoutLoading || current.deleteLoading ||
            current.logoutSuccess || current.deleteSuccess
        ) return
        _state.update { it.copy(logoutLoading = true, logoutSuccess = false, errorMessage = null) }
        viewModelScope.launch {
            try {
                // Revoca los tokens en el servidor y limpia la sesión local.
                authRepository.cerrarSesion()
                fichaLocal = null
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

    /**
     * Borra la ficha local (RUT, teléfono, fecha de nacimiento) y cierra la sesión.
     * La cuenta del servidor NO se borra: el backend todavía no expone DELETE /usuarios/me.
     */
    fun borrarDatosLocales() {
        val current = _state.value
        if (current.loading || current.logoutLoading || current.deleteLoading ||
            current.logoutSuccess || current.deleteSuccess
        ) return
        val ficha = fichaLocal
        _state.update { it.copy(deleteLoading = true, deleteSuccess = false, errorMessage = null) }

        viewModelScope.launch {
            try {
                if (ficha != null) userRepository.deleteUser(ficha)
                authRepository.olvidarDispositivo()
                fichaLocal = null
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
                        errorMessage = "No se pudieron borrar los datos locales. Inténtalo nuevamente."
                    )
                }
            } finally {
                _state.update { it.copy(deleteLoading = false) }
            }
        }
    }
}
