package com.albaconecta.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SolicitudOutboxDao {
    /**
     * Inserta una nueva solicitud en la cola outbox.
     * Si la idempotencyKey ya existe, falla (única).
     * El llamador debe manejar el conflicto según la lógica de negocio.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(solicitud: SolicitudOutboxEntity): Long

    /** Obtiene una solicitud por su clave de idempotencia. */
    @Query("SELECT * FROM solicitudes_outbox WHERE idempotencyKey = :clave LIMIT 1")
    suspend fun getByIdempotencyKey(clave: String): SolicitudOutboxEntity?

    /** Folio del backend para una clave; emite null hasta que el Worker lo guarda. */
    @Query("SELECT folioRemoto FROM solicitudes_outbox WHERE idempotencyKey = :clave LIMIT 1")
    fun observarFolio(clave: String): Flow<String?>

    /** Obtiene todas las solicitudes en estado PENDIENTE o FALLIDA (para reintento), ordenadas por fecha. */
    @Query("SELECT * FROM solicitudes_outbox WHERE estado IN ('PENDIENTE', 'FALLIDA') ORDER BY fechaCreacion ASC")
    suspend fun getPendientesYFallidas(): List<SolicitudOutboxEntity>

    /** Obtiene todas las solicitudes en estado ENVIANDO (para detectar workers colgados). */
    @Query("SELECT * FROM solicitudes_outbox WHERE estado = 'ENVIANDO'")
    suspend fun getEnviando(): List<SolicitudOutboxEntity>

    /** Actualiza el estado y contadores de una solicitud. */
    @Update
    suspend fun update(solicitud: SolicitudOutboxEntity)

    /** Cuenta solicitudes pendientes de envío (para badge en UI). */
    @Query("SELECT COUNT(*) FROM solicitudes_outbox WHERE estado IN ('PENDIENTE', 'FALLIDA', 'ENVIANDO')")
    suspend fun countPendientes(): Int

    /** Versión observada del contador: actualiza el aviso global cuando cambia el outbox. */
    @Query("SELECT COUNT(*) FROM solicitudes_outbox WHERE estado IN ('PENDIENTE', 'FALLIDA', 'ENVIANDO')")
    fun observarPendientes(): Flow<Int>

    /** Limpia solicitudes ENVIADA y RECHAZADA antiguas (opcional, para mantenimiento). */
    @Query("DELETE FROM solicitudes_outbox WHERE estado IN ('ENVIADA', 'RECHAZADA') AND fechaActualizacion < :antesDe")
    suspend fun limpiarAntiguas(antesDe: Long)
}