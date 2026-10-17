package com.albaconecta.app.validation

import com.albaconecta.app.viewmodel.RegisterErrors
import com.albaconecta.app.viewmodel.RegisterFormState
import java.util.Calendar
import java.util.TimeZone

object RegisterValidator {
    private val rutRegex = Regex("""(?:\d{7,8}|\d{1,2}\.\d{3}\.\d{3})-[0-9kK]""")
    private val correoRegex = Regex("""[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}""")
    private val telefonoRegex = Regex("""(?:\+56\s?)?[2-9]\d{8}""")

    fun validate(
        form: RegisterFormState,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): RegisterErrors = RegisterErrors(
        nombre = when {
            form.nombre.isBlank() -> "Ingresa tu nombre"
            form.nombre.trim().length < 2 -> "El nombre debe tener al menos 2 caracteres"
            else -> null
        },
        apellido = when {
            form.apellido.isBlank() -> "Ingresa tu apellido"
            form.apellido.trim().length < 2 -> "El apellido debe tener al menos 2 caracteres"
            else -> null
        },
        rut = when {
            form.rut.isBlank() -> "Ingresa tu RUT"
            !rutRegex.matches(form.rut.trim()) -> "Usa un RUT como 12.345.678-9 o 12345678-9"
            else -> null
        },
        correo = when {
            form.correo.isBlank() -> "Ingresa tu correo"
            !correoRegex.matches(form.correo.trim()) -> "Ingresa un correo válido"
            else -> null
        },
        telefono = when {
            form.telefono.isBlank() -> "Ingresa tu teléfono"
            !telefonoRegex.matches(form.telefono.trim()) -> "Usa 9 dígitos, opcionalmente precedidos por +56"
            else -> null
        },
        fechaNacimiento = validateBirthDate(form.fechaNacimiento, referenceTimeMillis),
        password = when {
            form.password.isEmpty() -> "Ingresa una contraseña"
            form.password.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
            else -> null
        },
        confirmPassword = when {
            form.confirmPassword.isEmpty() -> "Confirma tu contraseña"
            form.confirmPassword != form.password -> "Las contraseñas no coinciden"
            else -> null
        },
        aceptaTerminos = if (!form.aceptaTerminos) "Debes aceptar los términos y condiciones" else null
    )

    private fun validateBirthDate(birthDateMillis: Long?, referenceTimeMillis: Long): String? {
        if (birthDateMillis == null) return "Debes seleccionar tu fecha de nacimiento."

        val today = utcDate(referenceTimeMillis)
        val birthDate = utcDate(birthDateMillis)
        if (birthDate.after(today)) return "La fecha de nacimiento no puede estar en el futuro."

        val adultBirthDateLimit = today.clone() as Calendar
        adultBirthDateLimit.add(Calendar.YEAR, -18)
        return if (birthDate.after(adultBirthDateLimit)) {
            "Debes ser mayor de edad para registrarte."
        } else {
            null
        }
    }

    private fun utcDate(timeMillis: Long): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
