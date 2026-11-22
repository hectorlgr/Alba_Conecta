package com.albaconecta.app.data.repository

import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.data.remote.AlbaApi
import com.albaconecta.app.data.remote.ApiClient
import com.albaconecta.app.data.remote.LoginDto
import com.albaconecta.app.data.remote.ProblemaDto
import com.albaconecta.app.data.remote.RefreshDto
import com.albaconecta.app.data.remote.RegistroDto
import com.albaconecta.app.data.remote.TokenDto
import com.albaconecta.app.data.remote.UsuarioDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

sealed interface ResultadoAuth {
    data class Ok(val usuario: UsuarioDto) : ResultadoAuth
    data class Fallo(val mensaje: String, val campos: Map<String, String> = emptyMap()) : ResultadoAuth
}

/**
 * Autenticación contra `ms-identidad`. Los usuarios viven en la base del backend: la app ya
 * no guarda contraseñas, solo los tokens de la sesión.
 * El refresh automático en 401 lo maneja el Authenticator compartido (OkHttp); este repo
 * solo hace login/registro/biometría/logout explícito.
 */
class AuthRepository(
    private val sesiones: SessionDataStore,
    private val api: () -> AlbaApi = { ApiClient.api }
) {

    suspend fun login(email: String, password: String): ResultadoAuth = intentar {
        val respuesta = api().login(LoginDto(email = email.trim().lowercase(), password = password))
        val token = respuesta.body()
        when {
            respuesta.isSuccessful && token != null -> {
                // Login exitoso: si hay biometría habilitada, actualizar el refresh token reservado
                val biometricEnabled = sesiones.biometricLoginEnabled.first()
                val resultado = iniciarSesion(token)
                if (biometricEnabled) {
                    // El login rotó el refresh token; guardar el nuevo como credencial biométrica
                    sesiones.actualizarBiometricRefreshToken(token.refreshToken)
                }
                resultado
            }
            respuesta.code() == 401 -> ResultadoAuth.Fallo("Correo o contraseña incorrectos.")
            respuesta.code() == 403 -> ResultadoAuth.Fallo("Tu cuenta está desactivada.")
            else -> fallo(respuesta.code(), respuesta.errorBody()?.string())
        }
    }

    /** Registro + login: `POST /auth/register` devuelve el usuario, no los tokens. */
    suspend fun registrar(nombre: String, email: String, password: String): ResultadoAuth = intentar {
        val respuesta = api().registrar(
            RegistroDto(nombre = nombre.trim(), email = email.trim().lowercase(), password = password)
        )
        when {
            respuesta.isSuccessful -> login(email, password)
            respuesta.code() == 409 -> ResultadoAuth.Fallo("Ya existe una cuenta con ese correo.")
            else -> fallo(respuesta.code(), respuesta.errorBody()?.string())
        }
    }

    /** Acceso con huella: canjea el refresh token reservado (distinto al de la sesión) y rota ambos tokens. */
    suspend fun entrarConBiometria(): ResultadoAuth = intentar {
        val reservado = sesiones.biometricRefreshToken.first()
            ?: return@intentar ResultadoAuth.Fallo("No hay una cuenta asociada a la huella en este dispositivo.")
        val token = canjearBiometrico(reservado)
        if (token == null) {
            // El refresh reservado expiró o fue revocado: desactivar biometría y pedir login normal
            sesiones.setBiometricLoginEnabled(false, null)
            return@intentar ResultadoAuth.Fallo(
                "La credencial de la huella expiró o fue revocada. Entra con tu contraseña para renovarla."
            )
        }
        sesiones.actualizarBiometricRefreshToken(token.refreshToken) // el anterior queda revocado
        iniciarSesion(token)
    }

    /**
     * Habilita la huella guardando el refresh token como credencial del dispositivo.
     * Deshabilitar la borra; solo se revoca en el servidor si no es también el token
     * de la sesión (revocarlo dejaría la sesión sin forma de renovarse).
     * Devuelve false si no había sesión activa.
     */
    suspend fun activarBiometria(habilitar: Boolean): Boolean = withContext(Dispatchers.IO) {
        val sesion = sesiones.sesion.first() ?: return@withContext false
        if (habilitar) {
            sesiones.setBiometricLoginEnabled(true, sesion.refreshToken)
        } else {
            val reservado = sesiones.biometricRefreshToken.first()
            // Best effort: si el servidor no responde, igual borramos la credencial local.
            if (reservado != null && reservado != sesion.refreshToken) {
                try {
                    api().logout("Bearer ${sesion.accessToken}", RefreshDto(reservado))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    // Sin red: la credencial local se elimina igualmente.
                }
            }
            sesiones.setBiometricLoginEnabled(false, null)
        }
        true
    }

    /** Revoca la sesión en el servidor (si se puede) y borra los tokens locales. */
    suspend fun cerrarSesion() = withContext(Dispatchers.IO) {
        val sesion = sesiones.sesion.first()
        val huellaActiva = sesiones.biometricLoginEnabled.first()
        // shortcut: con la huella activa el refresh token ES la credencial del dispositivo y
        // no se revoca al cerrar sesión; se revoca al desactivar la huella o al expirar.
        if (sesion != null && !huellaActiva) {
            val cuerpo = RefreshDto(sesion.refreshToken)
            try {
                api().logout("Bearer ${sesion.accessToken}", cuerpo)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                null // sin red: la sesión local se cierra igual
            }
        }
        sesiones.cerrarSesion()
    }

    /** Cierra la sesión local y borra la credencial de la huella (revocando ambos tokens). */
    suspend fun olvidarDispositivo() = withContext(Dispatchers.IO) {
        val sesion = sesiones.sesion.first()
        val reservado = sesiones.biometricRefreshToken.first()
        if (sesion != null) {
            val bearer = "Bearer ${sesion.accessToken}"
            try {
                api().logout(bearer, RefreshDto(sesion.refreshToken))
                if (reservado != null && reservado != sesion.refreshToken) {
                    api().logout(bearer, RefreshDto(reservado))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Sin red: igual se borran los datos locales.
            }
        }
        sesiones.setBiometricLoginEnabled(false, null)
        sesiones.cerrarSesion()
    }

    private suspend fun iniciarSesion(token: TokenDto): ResultadoAuth {
        sesiones.guardarSesion(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            usuarioId = token.usuario.id,
            nombre = token.usuario.nombre,
            email = token.usuario.email,
            rol = token.usuario.rol
        )
        return ResultadoAuth.Ok(token.usuario)
    }

    /** Canjea el refresh token BIOMÉTRICO (distinto al de sesión) usando la API compartida. */
    private suspend fun canjearBiometrico(refreshToken: String): TokenDto? = try {
        val respuesta = api().refrescar(RefreshDto(refreshToken))
        if (respuesta.isSuccessful) respuesta.body() else null
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    /** Un solo lugar para el manejo de red/serialización de todas las llamadas. */
    private suspend fun intentar(bloque: suspend () -> ResultadoAuth): ResultadoAuth =
        try {
            withContext(Dispatchers.IO) { bloque() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ResultadoAuth.Fallo("No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.")
        } catch (_: SerializationException) {
            ResultadoAuth.Fallo("El servidor respondió en un formato inesperado.")
        }

    private fun fallo(codigo: Int, cuerpo: String?): ResultadoAuth.Fallo {
        val problema = cuerpo?.let {
            runCatching { ApiClient.json.decodeFromString<ProblemaDto>(it) }.getOrNull()
        }
        return ResultadoAuth.Fallo(
            mensaje = problema?.detail?.takeIf { it.isNotBlank() }
                ?: "No pudimos completar la operación (error $codigo).",
            campos = problema?.errors?.associate { it.field to it.message }.orEmpty()
        )
    }
}
