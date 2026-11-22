package com.albaconecta.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sobre de paginación del backend: `{items, total, page, limit, has_more}`. */
@Serializable
data class PaginaDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0
)

/** `GET /api/v1/contenidos/programas` (solo los publicados, sin eliminados). */
@Serializable
data class ProgramaDto(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerialName("categoria_id") val categoriaId: String? = null,
    @SerialName("imagen_url") val imagenUrl: String? = null
)

/** `GET /api/v1/contenidos/indicadores` — alimenta la pantalla de Impacto. */
@Serializable
data class IndicadorDto(
    val id: String,
    val nombre: String,
    val valor: Double,
    val unidad: String? = null,
    val periodo: String? = null,
    val fuente: String? = null
)

/** `GET /api/v1/contenidos/historias` — solo las autorizadas. */
@Serializable
data class HistoriaDto(
    val id: String,
    val titulo: String,
    val resumen: String
)
