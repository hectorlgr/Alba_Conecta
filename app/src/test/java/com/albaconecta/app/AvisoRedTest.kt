package com.albaconecta.app

import com.albaconecta.app.util.avisoRed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisoRedTest {
    @Test fun sinAvisoConRedYSinPendientes() {
        assertNull(avisoRed(true, 0))
    }

    @Test fun avisoOfflineSinPendientes() {
        assertEquals("Sin conexión · mostrando datos guardados", avisoRed(false, 0))
    }

    @Test fun avisoOfflineConUnaSolicitud() {
        assertEquals("Sin conexión · 1 solicitud por enviar", avisoRed(false, 1))
    }

    @Test fun avisoOfflineConVariasSolicitudes() {
        assertEquals("Sin conexión · 3 solicitudes por enviar", avisoRed(false, 3))
    }

    @Test fun avisoConRedYPendientes() {
        assertEquals("1 solicitud pendiente de envío", avisoRed(true, 1))
        assertEquals("2 solicitudes pendientes de envío", avisoRed(true, 2))
    }
}
