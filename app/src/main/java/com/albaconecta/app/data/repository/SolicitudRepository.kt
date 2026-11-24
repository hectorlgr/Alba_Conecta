package com.albaconecta.app.data.repository

import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.EstadoOutbox
import com.albaconecta.app.data.local.SolicitudOutboxEntity
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

/** Resultado del envío inmediato (para UI). */
sealed interface ResultadoEnvio {
    data class Exito(val folio: String) : ResultadoEnvio
    data class Encolada(val mensaje: String = "Solicitud guardada. Se enviará cuando haya conexión.") : ResultadoEnvio
    data class Fallo(val mensaje: String, val campos: Map<String, String> = emptyMap()) : ResultadoEnvio
}

/**
 * Envío de solicitudes con patrón Outbox (Room + WorkManager).
 *
 * Flujo:
 * 1. Guarda en outbox (Room) con estado PENDIENTE y la misma Idempotency-Key.
 * 2. Devuelve ResultadoEnvio.Encolada inmediatamente (UI no bloquea).
 * 3. WorkManager procesa la cola en background con backoff exponencial.
 * 4. En éxito: marca ENVIADA, guarda folioRemoto, limpia foto temporal.
 * 5. En error 4xx no reintentable: marca RECHAZADA.
 * 6. En error 5xx/red: incrementa intentos, marca FALLIDA (reintentará si < 5).
 */
class SolicitudRepository(
    private val database: AlbaDatabase,
    private val api: () -> AlbaApi = { ApiClient.api }
) {

    companion object {
        const val MAX_INTENTOS = 5
    }

    suspend fun crear(
        solicitud: SolicitudDto,
        clave: String = UUID.randomUUID().toString()
    ): ResultadoEnvio = withContext(Dispatchers.IO) {
        val payloadJson = ApiClient.json.encodeToString(solicitud)

        // 1. Insertar en outbox (idempotencia a nivel BD)
        val entity = SolicitudOutboxEntity(
            idempotencyKey = clave,
            payloadJson = payloadJson,
            estado = EstadoOutbox.PENDIENTE
        )

        return@withContext try {
            database.solicitudOutboxDao().insert(entity)
            ResultadoEnvio.Encolada()
        } catch (e: Exception) {
            // Conflicto de UNIQUE en idempotencyKey: ya existe esta solicitud
            val existente = database.solicitudOutboxDao().getByIdempotencyKey(clave)
            when (existente?.estado) {
                EstadoOutbox.ENVIADA -> ResultadoEnvio.Exito(existente.folioRemoto ?: "—")
                EstadoOutbox.ENVIANDO -> ResultadoEnvio.Encolada("La solicitud se está enviando…")
                EstadoOutbox.PENDIENTE, EstadoOutbox.FALLIDA -> ResultadoEnvio.Encolada()
                EstadoOutbox.RECHAZADA -> ResultadoEnvio.Fallo("Esta solicitud fue rechazada y no se puede reintentar.")
                null -> ResultadoEnvio.Fallo("No se pudo guardar la solicitud localmente.")
            }
        }
    }

    /** Procesa una solicitud de la cola outbox (llamado por Worker). */
    suspend fun procesarOutbox(): Int = withContext(Dispatchers.IO) {
        var procesadas = 0
        val pendientes = database.solicitudOutboxDao().getPendientesYFallidas()

        for (entity in pendientes) {
            if (entity.intentos >= MAX_INTENTOS) {
                // Demasiados intentos: marcar como fallida definitivamente (no RECHAZADA, por si recupera red)
                database.solicitudOutboxDao().update(entity.copyWith(estado = EstadoOutbox.FALLIDA))
                continue
            }

            // Marcar ENVIANDO para evitar doble procesamiento
            database.solicitudOutboxDao().update(entity.copyWith(estado = EstadoOutbox.ENVIANDO, intentos = entity.intentos + 1))

            val solicitud = try {
                ApiClient.json.decodeFromString<SolicitudDto>(entity.payloadJson)
            } catch (_: SerializationException) {
                // Payload corrupto: marcar RECHAZADA (no se recupera)
                database.solicitudOutboxDao().update(entity.copyWith(estado = EstadoOutbox.RECHAZADA))
                continue
            }

            val resultado = enviarAlBackend(solicitud, entity.idempotencyKey)

            when (resultado) {
                is ResultadoBackend.Exito -> {
                    database.solicitudOutboxDao().update(
                        entity.copyWith(estado = EstadoOutbox.ENVIADA, folioRemoto = resultado.folio)
                    )
                    procesadas++
                }
                is ResultadoBackend.ErrorReintentable -> {
                    // Error de red o 5xx: queda FALLIDA para reintento
                    database.solicitudOutboxDao().update(entity.copyWith(estado = EstadoOutbox.FALLIDA))
                }
                is ResultadoBackend.ErrorNoReintentable -> {
                    // 4xx (excepto 409 con mismo payload): RECHAZADA
                    database.solicitudOutboxDao().update(entity.copyWith(estado = EstadoOutbox.RECHAZADA))
                }
            }
        }
        procesadas
    }

    /** Cuenta solicitudes pendientes de envío (para badge en UI). */
    suspend fun countPendientes(): Int = withContext(Dispatchers.IO) {
        database.solicitudOutboxDao().countPendientes()
    }

    /** Obtiene el folio de una solicitud ya enviada (por idempotencyKey). */
    suspend fun obtenerFolio(clave: String): String? = withContext(Dispatchers.IO) {
        database.solicitudOutboxDao().getByIdempotencyKey(clave)?.folioRemoto
    }

    /** Envío real al backend. Devuelve resultado tipado para decidir estado outbox. */
    private suspend fun enviarAlBackend(solicitud: SolicitudDto, clave: String): ResultadoBackend =
        try {
            val respuesta = api().crearSolicitud(clave, solicitud)
            val creada = respuesta.body()
            if (respuesta.isSuccessful && creada != null) {
                ResultadoBackend.Exito(creada.folio)
            } else {
                val problema = leerProblema(respuesta.errorBody()?.string())
                when (respuesta.code()) {
                    409 -> {
                        // IDEMPOTENCY_CONFLICT: si el payload coincide, el backend ya devolvió 2xx arriba.
                        // Si llegamos aquí, payload distinto → no reintentar.
                        ResultadoBackend.ErrorNoReintentable(
                            problema?.detail?.takeIf { it.isNotBlank() } ?: "Conflicto de idempotencia: payload distinto."
                        )
                    }
                    in 400..499 -> ResultadoBackend.ErrorNoReintentable(
                        problema?.detail?.takeIf { it.isNotBlank() } ?: "Error ${respuesta.code()}."
                    )
                    else -> ResultadoBackend.ErrorReintentable("Error del servidor (${respuesta.code()}).")
                }
            }
        } catch (_: IOException) {
            ResultadoBackend.ErrorReintentable("Sin conexión.")
        } catch (_: SerializationException) {
            ResultadoBackend.ErrorNoReintentable("Respuesta inválida del servidor.")
        }

    private fun leerProblema(cuerpo: String?): ProblemaDto? =
        cuerpo?.let { texto -> runCatching { ApiClient.json.decodeFromString<ProblemaDto>(texto) }.getOrNull() }
}

/** Resultado interno del envío al backend para mapear a estado outbox. */
private sealed interface ResultadoBackend {
    data class Exito(val folio: String) : ResultadoBackend
    data class ErrorReintentable(val mensaje: String) : ResultadoBackend
    data class ErrorNoReintentable(val mensaje: String) : ResultadoBackend
}
