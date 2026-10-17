package com.albaconecta.app

import com.albaconecta.app.validation.RegisterValidator
import com.albaconecta.app.viewmodel.RegisterErrors
import com.albaconecta.app.viewmodel.RegisterFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RegisterValidatorTest {
    private val validForm = RegisterFormState(
        nombre = " Ana ", apellido = "Pérez", rut = "12.345.678-9",
        correo = " ana@example.cl ", telefono = "+56912345678", fechaNacimiento = 946684800000L,
        password = "Clave123", confirmPassword = "Clave123", aceptaTerminos = true
    )

    @Test fun validFormHasNoErrors() {
        assertEquals(RegisterErrors(), RegisterValidator.validate(validForm))
        assertEquals(RegisterErrors(), RegisterValidator.validate(validForm.copy(rut = "12345678-k", telefono = "912345678")))
    }

    @Test fun invalidEmailIsRejected() {
        assertNotNull(RegisterValidator.validate(validForm.copy(correo = "ana@correo")).correo)
    }

    @Test fun invalidRutFormatIsRejected() {
        assertNotNull(RegisterValidator.validate(validForm.copy(rut = "123456789")).rut)
    }

    @Test fun invalidPhoneIsRejected() {
        assertNotNull(RegisterValidator.validate(validForm.copy(telefono = "123abc")).telefono)
    }

    @Test fun shortPasswordIsRejected() {
        assertNotNull(RegisterValidator.validate(validForm.copy(password = "1234567")).password)
    }

    @Test fun confirmationMustMatchCaseAndSpaces() {
        assertNotNull(RegisterValidator.validate(validForm.copy(confirmPassword = "clave123")).confirmPassword)
        assertNotNull(RegisterValidator.validate(validForm.copy(confirmPassword = " Clave123 ")).confirmPassword)
        assertEquals(RegisterErrors(), RegisterValidator.validate(validForm.copy(password = " clave  ", confirmPassword = " clave  ")))
    }

    @Test fun termsAreRequired() {
        assertNotNull(RegisterValidator.validate(validForm.copy(aceptaTerminos = false)).aceptaTerminos)
    }

    @Test fun birthDateIsRequired() {
        assertNotNull(RegisterValidator.validate(validForm.copy(fechaNacimiento = null)).fechaNacimiento)
    }

    @Test fun namesMustHaveTwoNonPaddingCharacters() {
        val errors = RegisterValidator.validate(validForm.copy(nombre = "  ", apellido = " A "))
        assertNotNull(errors.nombre)
        assertNotNull(errors.apellido)
    }
}
