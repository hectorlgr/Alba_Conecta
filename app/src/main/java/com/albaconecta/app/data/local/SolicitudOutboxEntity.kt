package com.albaconecta.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cola de solicitudes pendientes de envío (outbox pattern).
 *
 * Estados válidos:
 * - PENDIENTE: recién creada, esperando Worker
 * - ENVIANDO: Worker la está procesando
 * - ENVIADA: servidor respondió 2xx con folio
 * - FALLIDA: error de red/servidor, se reintentará (máx 5)
 * - RECHAZADA: error 4xx que no se debe reintentar (ej. 409 IDEMPOTENCY_CONFLICT con payload distinto)
 */
@Entity(
    tableName = "solicitudes_outbox",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class SolicitudOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Clave de idempotencia enviada al backend (UUIDv4). Única por solicitud. */
    val idempotencyKey: String,
    /** JSON serializado del `SolicitudDto` enviado. */
    val payloadJson: String,
    /** Estado actual de la solicitud en la cola. */
    val estado: EstadoOutbox = EstadoOutbox.PENDIENTE,
    /** Número de intentos de envío (incrementa en cada reintento). */
    val intentos: Int = 0,
    /** Folio devuelto por el backend tras éxito (nullable hasta ENVIADA). */
    val folioRemoto: String? = null,
    /** Timestamp de creación (epoch millis UTC). */
    val fechaCreacion: Long = System.currentTimeMillis(),
    /** Timestamp de última actualización (epoch millis UTC). */
    val fechaActualizacion: Long = System.currentTimeMillis()
) {
    /** Crea una copia con estado actualizado y timestamp refrescado. */
    fun copyWith(
        estado: EstadoOutbox? = null,
        intentos: Int? = null,
        folioRemoto: String? = null,
        fechaActualizacion: Long = System.currentTimeMillis()
    ) = copy(
        estado = estado ?: this.estado,
        intentos = intentos ?: this.intentos,
        folioRemoto = folioRemoto ?: this.folioRemoto,
        fechaActualizacion = fechaActualizacion
    )
}

/** Estados de la cola outbox. Coinciden con la especificación de Fase 7. */
enum class EstadoOutbox {
    PENDIENTE,
    ENVIANDO,
    ENVIADA,
    FALLIDA,
    RECHAZADA
}