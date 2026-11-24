package com.albaconecta.app.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import com.albaconecta.app.data.repository.SolicitudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker que procesa la cola outbox de solicitudes pendientes.
 *
 * Configuración:
 * - Backoff exponencial (reintentos automáticos de WorkManager)
 * - Periódico cada 15 min + backoff en fallos
 * - Máximo 5 intentos por solicitud (controlado en repositorio)
 *
 * Se encola automáticamente al crear una solicitud (ver SolicitudRepository.crear).
 */
class OutboxWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "outbox_sync"
        const val INTERVAL_MINUTOS = 15L
        const val BACKOFF_SEGUNDOS = 30L
        const val MAX_BACKOFF_SEGUNDOS = 300L // 5 min

        /** Encola el trabajo periódico (idempotente: reemplaza si ya existe). */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequest.Builder(OutboxWorker::class.java, INTERVAL_MINUTOS, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SEGUNDOS,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Sincronización inmediata tras guardar una solicitud, para obtener el folio sin esperar 15 min. */
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_now",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.from(OutboxWorker::class.java)
            )
        }

        /** Cancela el trabajo periódico (ej. logout). */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AlbaDatabase.getInstance(applicationContext)
        val repo = SolicitudRepository(database)

        return@withContext try {
            val procesadas = repo.procesarOutbox()
            if (procesadas > 0) {
                Result.success()
            } else {
                // Sin trabajo: success para no reintentar innecesariamente
                Result.success()
            }
        } catch (e: Exception) {
            // Fallo inesperado: reintentar con backoff
            Result.retry()
        }
    }
}