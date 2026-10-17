package com.albaconecta.app

import com.albaconecta.app.validation.LoginValidator
import com.albaconecta.app.viewmodel.LoginErrors
import com.albaconecta.app.viewmodel.LoginFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LoginValidatorTest {
    private val validForm = LoginFormState(correo = " Ana@example.cl ", password = "Clave123")

    @Test fun validCredentialsHaveNoLocalErrors() {
        assertEquals(LoginErrors(), LoginValidator.validate(validForm))
    }

    @Test fun emptyOrWhitespaceEmailIsRejected() {
        assertNotNull(LoginValidator.validate(validForm.copy(correo = "")).correo)
        assertNotNull(LoginValidator.validate(validForm.copy(correo = "   ")).correo)
    }

    @Test fun malformedEmailIsRejected() {
        assertNotNull(LoginValidator.validate(validForm.copy(correo = "ana@correo")).correo)
    }

    @Test fun emptyPasswordIsRejected() {
        assertNotNull(LoginValidator.validate(validForm.copy(password = "")).password)
    }

    @Test fun passwordCaseAndSpacesArePreserved() {
        val form = validForm.copy(password = " ClAvE123 ")
        assertEquals(LoginErrors(), LoginValidator.validate(form))
        assertEquals(" ClAvE123 ", form.password)
        assertEquals(LoginErrors(), LoginValidator.validate(validForm.copy(password = " ")))
    }

    @Test fun shortNonEmptyPasswordIsAcceptedForAuthentication() {
        assertEquals(LoginErrors(), LoginValidator.validate(validForm.copy(password = "a")))
    }
}
