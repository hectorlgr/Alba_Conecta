package com.albaconecta.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.data.local.UserEntity
import com.albaconecta.app.data.repository.UserRepository
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
    val deleteSuccess: Boolean = false
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
                _state.update {
                    it.copy(user = null, deleteLoading = false, deleteSuccess = true, errorMessage = null)
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
