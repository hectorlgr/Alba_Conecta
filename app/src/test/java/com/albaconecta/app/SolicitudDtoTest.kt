package com.albaconecta.app

import com.albaconecta.app.data.remote.DetalleDto
import com.albaconecta.app.data.remote.SolicitudDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SolicitudDtoTest {

    // Misma configuración que ApiClient.
    private val json = Json { explicitNulls = false; encodeDefaults = true }

    @Test fun elCuerpoEnviadoUsaLosNombresDelContrato() {
        val dto = SolicitudDto(
            nombreContacto = "Ana",
            email = "ana@demo.cl",
            mensaje = "hola",
            aceptaPrivacidad = true,
            detalles = listOf(DetalleDto(tipoColaboracionCodigo = "OTRO", descripcion = "x"))
        )
        val enviado = json.encodeToString(dto)

        // El backend rechaza campos desconocidos: si alguien agrega uno, esta prueba falla.
        assertEquals(
            setOf("nombre_contacto", "email", "mensaje", "acepta_privacidad", "detalles"),
            Json.parseToJsonElement(enviado).jsonObject.keys
        )
        // telefono nulo no viaja (explicitNulls = false).
        assertEquals(false, enviado.contains("telefono"))
    }
}
