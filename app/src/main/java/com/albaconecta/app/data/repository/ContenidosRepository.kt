package com.albaconecta.app.data.repository

import com.albaconecta.app.data.remote.AlbaApi
import com.albaconecta.app.data.remote.ApiClient
import com.albaconecta.app.data.remote.HistoriaDto
import com.albaconecta.app.data.remote.IndicadorDto
import com.albaconecta.app.data.remote.PaginaDto
import com.albaconecta.app.data.remote.ProgramaDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * Lectura de contenidos públicos (`ms-contenidos`). Sin caché local: si no hay red,
 * la pantalla muestra un aviso y un botón para reintentar.
 */
class ContenidosRepository(private val api: () -> AlbaApi = { ApiClient.api }) {

    suspend fun programas(): List<ProgramaDto> = pedir { api().programas() }

    suspend fun indicadores(): List<IndicadorDto> = pedir { api().indicadores() }

    suspend fun historias(): List<HistoriaDto> = pedir { api().historias() }

    /** Ejecuta la llamada en IO y convierte cualquier respuesta no-2xx en IOException. */
    private suspend fun <T> pedir(llamada: suspend () -> Response<PaginaDto<T>>): List<T> =
        withContext(Dispatchers.IO) {
            val respuesta = llamada()
            val pagina = respuesta.body()
            if (respuesta.isSuccessful && pagina != null) pagina.items
            else throw IOException("HTTP ${respuesta.code()}")
        }
}
