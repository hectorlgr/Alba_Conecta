package com.albaconecta.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginDto(
    val email: String,
    val password: String
)

/** `RegistroIn` del backend. Ojo: NO acepta RUT ni fecha de nacimiento (esos quedan locales). */
@Serializable
data class RegistroDto(
    val nombre: String,
    val email: String,
    val password: String,
    val tipo: String = "COLABORADOR",
    val organizacion: String? = null
)

@Serializable
data class UsuarioDto(
    val id: String,
    val nombre: String,
    val email: String,
    val rol: String,
    val tipo: String = "COLABORADOR",
    val organizacion: String? = null,
    val activo: Boolean = true,
    @SerialName("fecha_creacion") val fechaCreacion: String = ""
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int = 0,
    val usuario: UsuarioDto
)

@Serializable
data class RefreshDto(
    @SerialName("refresh_token") val refreshToken: String
)
