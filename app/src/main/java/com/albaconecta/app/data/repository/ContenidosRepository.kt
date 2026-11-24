package com.albaconecta.app.data.repository

import com.albaconecta.app.data.local.AlbaDatabase
import com.albaconecta.app.data.local.IndicadorEntity
import com.albaconecta.app.data.local.ProgramaEntity
import com.albaconecta.app.data.remote.AlbaApi
import com.albaconecta.app.data.remote.ApiClient
import com.albaconecta.app.data.remote.ApoyoDto
import com.albaconecta.app.data.remote.CandidatoDto
import com.albaconecta.app.data.remote.HistoriaDto
import com.albaconecta.app.data.remote.IndicadorDto
import com.albaconecta.app.data.remote.PaginaDto
import com.albaconecta.app.data.remote.ProgramaDto
import com.albaconecta.app.data.remote.TipoColaboracionDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * Lectura de contenidos públicos (`ms-contenidos`) con caché Room.
 * Estrategia: leer Room primero → mostrar inmediato → sincronizar en background si hay red.
 */
class ContenidosRepository(
    private val database: AlbaDatabase,
    private val api: () -> AlbaApi = { ApiClient.api }
) {

    /** Programas: caché local + sync opcional. */
    suspend fun programas(): List<ProgramaDto> = withContext(Dispatchers.IO) {
        val cache = database.programaDao().getAll()
        if (cache.isNotEmpty()) {
            cache.map { it.toDto() }
        } else {
            // Sin caché la red es la única fuente: si falla, avisar en vez de fingir
            // que no hay programas publicados (estado distinto del de sin conexión).
            if (!sincronizarProgramas()) throw IOException("Sin conexión y sin caché de programas")
            database.programaDao().getAll().map { it.toDto() }
        }
    }

    /** Indicadores: caché local + sync opcional. */
    suspend fun indicadores(): List<IndicadorDto> = withContext(Dispatchers.IO) {
        val cache = database.indicadorDao().getAll()
        if (cache.isNotEmpty()) {
            cache.map { it.toDto() }
        } else {
            if (!sincronizarIndicadores()) throw IOException("Sin conexión y sin caché de indicadores")
            database.indicadorDao().getAll().map { it.toDto() }
        }
    }

    /** Historias: sin caché (solo lectura remota). */
    suspend fun historias(): List<HistoriaDto> = withContext(Dispatchers.IO) {
        val respuesta = api().historias()
        val pagina = respuesta.body()
        if (respuesta.isSuccessful && pagina != null) pagina.items
        else throw IOException("HTTP ${respuesta.code()}")
    }

    /** Fuerza sincronización de programas (llamado desde UI con pull-to-refresh o Worker). */
    suspend fun sincronizarProgramas(): Boolean = withContext(Dispatchers.IO) {
        try {
            val respuesta = api().programas()
            if (respuesta.isSuccessful) {
                respuesta.body()?.items?.let { lista ->
                    val entities = lista.map { it.toEntity() }
                    database.programaDao().upsertAll(entities)
                    true
                } ?: false
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Fuerza sincronización de indicadores. */
    suspend fun sincronizarIndicadores(): Boolean = withContext(Dispatchers.IO) {
        try {
            val respuesta = api().indicadores()
            if (respuesta.isSuccessful) {
                respuesta.body()?.items?.let { lista ->
                    val entities = lista.map { it.toEntity() }
                    database.indicadorDao().upsertAll(entities)
                    true
                } ?: false
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Sincroniza ambos (programas e indicadores). Devuelve true si al menos uno tuvo éxito. */
    suspend fun sincronizarTodo(): Boolean = withContext(Dispatchers.IO) {
        val p = sincronizarProgramas()
        val i = sincronizarIndicadores()
        p || i
    }
}

// --- Mapeos DTO ↔ Entity ---

private fun ProgramaDto.toEntity(): ProgramaEntity = ProgramaEntity(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoriaId,
    imagenUrl = imagenUrl,
    ultimaSincronizacion = System.currentTimeMillis()
)

private fun ProgramaEntity.toDto(): ProgramaDto = ProgramaDto(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoriaId,
    imagenUrl = imagenUrl
)

private fun IndicadorDto.toEntity(): IndicadorEntity = IndicadorEntity(
    id = id,
    nombre = nombre,
    valor = valor,
    unidad = unidad,
    periodo = periodo,
    fuente = fuente,
    ultimaSincronizacion = System.currentTimeMillis()
)

private fun IndicadorEntity.toDto(): IndicadorDto = IndicadorDto(
    id = id,
    nombre = nombre,
    valor = valor,
    unidad = unidad,
    periodo = periodo,
    fuente = fuente
)

/** Tipos de colaboración desde `ms-vinculacion` (público, sin auth). */
class TipoColaboracionRepository(private val api: () -> AlbaApi = { ApiClient.api }) {

    suspend fun obtener(): List<TipoColaboracionDto> = withContext(Dispatchers.IO) {
        val respuesta = api().tiposColaboracion()
        if (respuesta.isSuccessful) respuesta.body() ?: emptyList()
        else throw IOException("HTTP ${respuesta.code()}")
    }
}

/** Talento público desde `ms-talento` (candidatos anonimizados + apoyos). */
class TalentoRepository(private val api: () -> AlbaApi = { ApiClient.api }) {

    suspend fun candidatos(): List<CandidatoDto> = withContext(Dispatchers.IO) {
        val respuesta = api().candidatos()
        if (respuesta.isSuccessful) respuesta.body()?.items ?: emptyList()
        else throw IOException("HTTP ${respuesta.code()}")
    }

    suspend fun apoyos(): List<ApoyoDto> = withContext(Dispatchers.IO) {
        val respuesta = api().apoyos()
        if (respuesta.isSuccessful) respuesta.body() ?: emptyList()
        else throw IOException("HTTP ${respuesta.code()}")
    }
}
