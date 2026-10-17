package com.albaconecta.app.validation

import com.albaconecta.app.viewmodel.LoginErrors
import com.albaconecta.app.viewmodel.LoginFormState

object LoginValidator {
    private val correoRegex = Regex("""[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}""")

    fun validate(form: LoginFormState): LoginErrors = LoginErrors(
        correo = when {
            form.correo.isBlank() -> "Ingresa tu correo"
            !correoRegex.matches(form.correo.trim()) -> "Ingresa un correo válido"
            else -> null
        },
        password = if (form.password.isEmpty()) "Ingresa tu contraseña" else null
    )
}
