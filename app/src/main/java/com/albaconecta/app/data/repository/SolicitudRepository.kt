package com.albaconecta.app.data.repository

import com.albaconecta.app.data.remote.AlbaApi
import com.albaconecta.app.data.remote.ApiClient
import com.albaconecta.app.data.remote.ProblemaDto
import com.albaconecta.app.data.remote.SolicitudCreadaDto
import com.albaconecta.app.data.remote.SolicitudDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.UUID

/** Resultado del envío. Sealed interface = conjunto cerrado de casos (solo Exito o Fallo). */
sealed interface ResultadoEnvio {
    data class Exito(val folio: String) : ResultadoEnvio

    /** `campos` trae los errores por campo del backend, con sus nombres del API. */
    data class Fallo(val mensaje: String, val campos: Map<String, String> = emptyMap()) : ResultadoEnvio
}

/**
 * Envío de solicitudes al backend.
 *
 * `api` es un proveedor (lambda) y no el cliente: así Retrofit se construye en el primer
 * envío y no al abrir la pantalla (en release todavía no hay servidor configurado).
 */
class SolicitudRepository(private val api: () -> AlbaApi = { ApiClient.api }) {

    suspend fun crear(
        solicitud: SolicitudDto,
        clave: String = UUID.randomUUID().toString() // UUIDv4 nuevo por envío
    ): ResultadoEnvio = withContext(Dispatchers.IO) {
        try {
            val respuesta = api().crearSolicitud(clave, solicitud)
            val creada: SolicitudCreadaDto? = respuesta.body()
            if (respuesta.isSuccessful && creada != null) {
                ResultadoEnvio.Exito(creada.folio)
            } else {
                val problema = leerProblema(respuesta.errorBody()?.string())
                ResultadoEnvio.Fallo(
                    mensaje = problema?.detail?.takeIf { it.isNotBlank() }
                        ?: "No pudimos enviar la solicitud (error ${respuesta.code()}).",
                    campos = problema?.errors?.associate { it.field to it.message }.orEmpty()
                )
            }
        } catch (e: CancellationException) {
            throw e // cancelar la corrutina no es un error de red: se propaga
        } catch (_: IOException) {
            ResultadoEnvio.Fallo("No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.")
        } catch (_: SerializationException) {
            ResultadoEnvio.Fallo("El servidor respondió en un formato inesperado.")
        }
    }

    private fun leerProblema(cuerpo: String?): ProblemaDto? =
        cuerpo?.let { texto -> runCatching { ApiClient.json.decodeFromString<ProblemaDto>(texto) }.getOrNull() }
}
