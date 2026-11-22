package com.albaconecta.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuerpo de POST /api/v1/vinculacion/solicitudes. Refleja `SolicitudIn` del backend:
 * el servidor rechaza campos desconocidos, así que aquí solo van los del contrato.
 */
@Serializable
data class SolicitudDto(
    @SerialName("nombre_contacto") val nombreContacto: String,
    val email: String? = null,
    val telefono: String? = null,
    val mensaje: String,
    @SerialName("acepta_privacidad") val aceptaPrivacidad: Boolean,
    val detalles: List<DetalleDto>
)

@Serializable
data class DetalleDto(
    @SerialName("tipo_colaboracion_codigo") val tipoColaboracionCodigo: String,
    val descripcion: String? = null
)

/**
 * Solo los campos que usa la app. La respuesta real trae más (id, observaciones internas…);
 * `ignoreUnknownKeys` los descarta.
 */
@Serializable
data class SolicitudCreadaDto(
    val folio: String,
    val estado: String,
    @SerialName("fecha_creacion") val fechaCreacion: String
)

/** Error RFC 7807 (`application/problem+json`). */
@Serializable
data class ProblemaDto(
    val detail: String = "",
    val code: String = "",
    val errors: List<ErrorCampoDto> = emptyList()
)

/** `field` usa los nombres del API (email, telefono, …). */
@Serializable
data class ErrorCampoDto(
    val field: String,
    val message: String
)
