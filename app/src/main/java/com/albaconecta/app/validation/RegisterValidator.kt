package com.albaconecta.app.validation

import com.albaconecta.app.viewmodel.RegisterErrors
import com.albaconecta.app.viewmodel.RegisterFormState
import java.util.Calendar
import java.util.TimeZone

object RegisterValidator {
    private val passwordRegex = Regex("""^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,}$""")
    private val rutRegex = Regex("""(?:\d{7,8}|\d{1,2}\.\d{3}\.\d{3})-[0-9kK]""")
    private val correoRegex = Regex("""[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}""")
    private val telefonoRegex = Regex("""(?:\+56\s?)?[2-9]\d{8}""")

    fun validate(
        form: RegisterFormState,
        referenceTimeMillis: Long = System.currentTimeMillis(),
        referenceTimeZone: TimeZone = TimeZone.getDefault()
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
        fechaNacimiento = validateBirthDate(form.fechaNacimiento, referenceTimeMillis, referenceTimeZone),
        password = when {
            form.password.isEmpty() -> "Ingresa una contraseña"
            !passwordRegex.matches(form.password) ->
                "La contraseña debe tener al menos 8 caracteres, una minúscula, una mayúscula y un dígito"
            else -> null
        },
        confirmPassword = when {
            form.confirmPassword.isEmpty() -> "Confirma tu contraseña"
            form.confirmPassword != form.password -> "Las contraseñas no coinciden"
            else -> null
        },
        aceptaTerminos = if (!form.aceptaTerminos) "Debes aceptar los términos y condiciones" else null
    )

    private fun validateBirthDate(
        birthDateMillis: Long?,
        referenceTimeMillis: Long,
        referenceTimeZone: TimeZone
    ): String? {
        if (birthDateMillis == null) return "Debes seleccionar tu fecha de nacimiento."

        val today = calendarDate(referenceTimeMillis, referenceTimeZone)
        val birthDate = calendarDate(birthDateMillis, TimeZone.getTimeZone("UTC"))
        if (birthDate.after(today)) return "La fecha de nacimiento no puede estar en el futuro."

        val adultBirthDateLimit = today.clone() as Calendar
        adultBirthDateLimit.add(Calendar.YEAR, -18)
        return if (birthDate.after(adultBirthDateLimit)) {
            "Debes ser mayor de edad para registrarte."
        } else {
            null
        }
    }

    private fun calendarDate(timeMillis: Long, timeZone: TimeZone): Calendar {
        val source = Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMillis
        }
        // Compare only calendar fields in a common zone, not local/UTC instants.
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(source.get(Calendar.YEAR), source.get(Calendar.MONTH), source.get(Calendar.DAY_OF_MONTH))
        }
    }
}
