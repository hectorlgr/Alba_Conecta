package com.albaconecta.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_preferences")

/** Sesión activa: los tokens del backend + lo mínimo del usuario para pintar la UI. */
data class SesionLocal(
    val usuarioId: String,
    val nombre: String,
    val email: String,
    val rol: String,
    val accessToken: String,
    val refreshToken: String
)

/**
 * Sesión en DataStore (no en Room): los tokens son credenciales y los emite el backend.
 * `access_token` se renueva con `refresh_token`; ambos se borran al cerrar sesión.
 */
class SessionDataStore(context: Context) {
    private val dataStore = context.applicationContext.sessionDataStore

    /** NULL si no hay sesión (falta el access token o el usuario). */
    val sesion: Flow<SesionLocal?> = dataStore.data.map { p ->
        val access = p[ACCESS_TOKEN]
        val refresh = p[REFRESH_TOKEN]
        val usuarioId = p[USUARIO_ID]
        if (access.isNullOrBlank() || refresh.isNullOrBlank() || usuarioId.isNullOrBlank()) {
            null
        } else {
            SesionLocal(
                usuarioId = usuarioId,
                nombre = p[USUARIO_NOMBRE].orEmpty(),
                email = p[USUARIO_EMAIL].orEmpty(),
                rol = p[USUARIO_ROL].orEmpty(),
                accessToken = access,
                refreshToken = refresh
            )
        }
    }

    suspend fun guardarSesion(
        accessToken: String,
        refreshToken: String,
        usuarioId: String,
        nombre: String,
        email: String,
        rol: String
    ) {
        dataStore.edit { p ->
            p[ACCESS_TOKEN] = accessToken
            p[REFRESH_TOKEN] = refreshToken
            p[USUARIO_ID] = usuarioId
            p[USUARIO_NOMBRE] = nombre
            p[USUARIO_EMAIL] = email
            p[USUARIO_ROL] = rol
        }
    }

    /** Tras un refresh solo cambian los tokens; el usuario sigue siendo el mismo. */
    suspend fun actualizarTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { p ->
            p[ACCESS_TOKEN] = accessToken
            p[REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun cerrarSesion() {
        dataStore.edit { p ->
            p.remove(ACCESS_TOKEN)
            p.remove(REFRESH_TOKEN)
            p.remove(USUARIO_ID)
            p.remove(USUARIO_NOMBRE)
            p.remove(USUARIO_EMAIL)
            p.remove(USUARIO_ROL)
        }
    }

    /**
     * ¿El usuario activó el acceso biométrico? Es una CREDENCIAL, no una sesión:
     * sobrevive a cerrarSesion() y solo se borra al desactivarla o eliminar la cuenta.
     */
    val biometricLoginEnabled: Flow<Boolean> = dataStore.data.map { p ->
        p[BIOMETRIC_LOGIN_ENABLED] ?: false
    }

    /**
     * Refresh token reservado para la huella. Es un token distinto del de la sesión: al
     * cerrar sesión se revoca el de la sesión y este sigue sirviendo para volver a entrar.
     */
    val biometricRefreshToken: Flow<String?> = dataStore.data.map { p ->
        p[BIOMETRIC_REFRESH_TOKEN]
    }

    suspend fun setBiometricLoginEnabled(enabled: Boolean, refreshToken: String?) {
        dataStore.edit { p ->
            p[BIOMETRIC_LOGIN_ENABLED] = enabled
            if (enabled && refreshToken != null) p[BIOMETRIC_REFRESH_TOKEN] = refreshToken
            else p.remove(BIOMETRIC_REFRESH_TOKEN) // sin credencial no hay dueño
        }
    }

    suspend fun actualizarBiometricRefreshToken(refreshToken: String) {
        dataStore.edit { p -> p[BIOMETRIC_REFRESH_TOKEN] = refreshToken }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USUARIO_ID = stringPreferencesKey("usuario_id")
        val USUARIO_NOMBRE = stringPreferencesKey("usuario_nombre")
        val USUARIO_EMAIL = stringPreferencesKey("usuario_email")
        val USUARIO_ROL = stringPreferencesKey("usuario_rol")
        val BIOMETRIC_LOGIN_ENABLED = booleanPreferencesKey("biometric_login_enabled")
        val BIOMETRIC_REFRESH_TOKEN = stringPreferencesKey("biometric_refresh_token")
    }
}
