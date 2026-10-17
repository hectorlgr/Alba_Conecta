package com.albaconecta.app

import com.albaconecta.app.validation.RegisterValidator
import com.albaconecta.app.viewmodel.RegisterErrors
import com.albaconecta.app.viewmodel.RegisterFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RegisterValidatorTest {
    private val referenceDate = utcDate(2026, 10, 16)

    private fun validate(form: RegisterFormState): RegisterErrors =
        RegisterValidator.validate(form, referenceDate)

    private fun utcDate(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day)
        }.timeInMillis

    private val validForm = RegisterFormState(
        nombre = " Ana ", apellido = "Pérez", rut = "12.345.678-9",
        correo = " ana@example.cl ", telefono = "+56912345678", fechaNacimiento = 946684800000L,
        password = "Clave123", confirmPassword = "Clave123", aceptaTerminos = true
    )

    @Test fun validFormHasNoErrors() {
        assertEquals(RegisterErrors(), validate(validForm))
        assertEquals(RegisterErrors(), validate(validForm.copy(rut = "12345678-k", telefono = "912345678")))
    }

    @Test fun invalidEmailIsRejected() {
        assertNotNull(validate(validForm.copy(correo = "ana@correo")).correo)
    }

    @Test fun invalidRutFormatIsRejected() {
        assertNotNull(validate(validForm.copy(rut = "123456789")).rut)
    }

    @Test fun invalidPhoneIsRejected() {
        assertNotNull(validate(validForm.copy(telefono = "123abc")).telefono)
    }

    @Test fun shortPasswordIsRejected() {
        assertNotNull(validate(validForm.copy(password = "1234567")).password)
    }

    @Test fun confirmationMustMatchCaseAndSpaces() {
        assertNotNull(validate(validForm.copy(confirmPassword = "clave123")).confirmPassword)
        assertNotNull(validate(validForm.copy(confirmPassword = " Clave123 ")).confirmPassword)
        assertEquals(RegisterErrors(), validate(validForm.copy(password = " clave  ", confirmPassword = " clave  ")))
    }

    @Test fun termsAreRequired() {
        assertNotNull(validate(validForm.copy(aceptaTerminos = false)).aceptaTerminos)
    }

    @Test fun birthDateIsRequired() {
        assertEquals("Debes seleccionar tu fecha de nacimiento.", validate(validForm.copy(fechaNacimiento = null)).fechaNacimiento)
    }

    @Test fun namesMustHaveTwoNonPaddingCharacters() {
        val errors = validate(validForm.copy(nombre = "  ", apellido = " A "))
        assertNotNull(errors.nombre)
        assertNotNull(errors.apellido)
    }

    @Test fun exactlyEighteenIsValid() {
        assertNull(validate(validForm.copy(fechaNacimiento = utcDate(2008, 10, 16))).fechaNacimiento)
    }

    @Test fun dayBeforeEighteenthBirthdayIsInvalid() {
        assertEquals("Debes ser mayor de edad para registrarte.",
            validate(validForm.copy(fechaNacimiento = utcDate(2008, 10, 17))).fechaNacimiento)
    }

    @Test fun olderThanEighteenIsValid() {
        assertNull(validate(validForm.copy(fechaNacimiento = utcDate(2008, 10, 15))).fechaNacimiento)
    }

    @Test fun futureBirthDateIsInvalid() {
        assertEquals("La fecha de nacimiento no puede estar en el futuro.",
            validate(validForm.copy(fechaNacimiento = utcDate(2026, 10, 17))).fechaNacimiento)
    }

    @Test fun leapDayBirthdayReachesEighteenOnMarchFirstInNonLeapYear() {
        val form = validForm.copy(fechaNacimiento = utcDate(2008, 2, 29))
        assertEquals("Debes ser mayor de edad para registrarte.",
            RegisterValidator.validate(form, utcDate(2026, 2, 28)).fechaNacimiento)
        assertNull(RegisterValidator.validate(form, utcDate(2026, 3, 1)).fechaNacimiento)
    }

    @Test fun referenceTimeWithinUtcDayDoesNotChangeAge() {
        val form = validForm.copy(fechaNacimiento = utcDate(2008, 10, 17))
        val endOfDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = referenceDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }.timeInMillis
        assertEquals(validate(form).fechaNacimiento,
            RegisterValidator.validate(form, endOfDay).fechaNacimiento)
    }
}
