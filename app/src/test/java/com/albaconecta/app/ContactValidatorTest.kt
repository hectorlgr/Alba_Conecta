package com.albaconecta.app

import com.albaconecta.app.validation.ContactValidator
import com.albaconecta.app.viewmodel.ContactErrors
import com.albaconecta.app.viewmodel.ContactFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ContactValidatorTest {
    private fun date(day: Int, hour: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, 0)
        }.timeInMillis

    private val validForm = ContactFormState(
        nombre = " Ana ", correo = " ana@example.cl ", telefono = " +56912345678 ",
        mensaje = "Quiero colaborar", representaEmpresa = true, cantidadTrabajadores = 10,
        fechaContacto = date(17), autorizaContacto = true
    )
    private fun validate(form: ContactFormState) = ContactValidator.validate(form, date(17, 18))

    @Test fun validFormHasNoErrors() {
        assertEquals(ContactErrors(), validate(validForm))
    }
    @Test fun invalidNameIsRejected() {
        assertNotNull(validate(validForm.copy(nombre = " A ")).nombre)
        assertNotNull(validate(validForm.copy(nombre = "  ")).nombre)
    }
    @Test fun invalidEmailIsRejected() {
        assertNotNull(validate(validForm.copy(correo = "ana@correo")).correo)
    }
    @Test fun invalidPhoneIsRejected() {
        assertNotNull(validate(validForm.copy(telefono = "123abc")).telefono)
    }
    @Test fun blankMessageIsRejected() {
        assertNotNull(validate(validForm.copy(mensaje = "")).mensaje)
        assertNotNull(validate(validForm.copy(mensaje = "  ")).mensaje)
    }
    @Test fun companyRequiresWorkerCount() {
        assertNotNull(validate(validForm.copy(cantidadTrabajadores = null)).cantidadTrabajadores)
    }
    @Test fun companyRequiresPositiveWorkerCount() {
        assertNotNull(validate(validForm.copy(cantidadTrabajadores = 0)).cantidadTrabajadores)
        assertNotNull(validate(validForm.copy(cantidadTrabajadores = -1)).cantidadTrabajadores)
    }
    @Test fun individualDoesNotRequireWorkerCount() {
        assertEquals(ContactErrors(), validate(validForm.copy(representaEmpresa = false, cantidadTrabajadores = null)))
    }
    @Test fun dateIsRequired() {
        assertNotNull(validate(validForm.copy(fechaContacto = null)).fechaContacto)
    }
    @Test fun pastDateIsRejected() {
        assertNotNull(validate(validForm.copy(fechaContacto = date(16))).fechaContacto)
    }
    @Test fun todayIsValidRegardlessOfCurrentTime() {
        assertEquals(ContactErrors(), validate(validForm.copy(fechaContacto = date(17))))
    }
    @Test fun futureDateIsValid() {
        assertEquals(ContactErrors(), validate(validForm.copy(fechaContacto = date(18))))
    }
    @Test fun authorizationIsRequired() {
        assertNotNull(validate(validForm.copy(autorizaContacto = false)).autorizaContacto)
    }
}
