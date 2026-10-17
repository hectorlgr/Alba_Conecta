package com.albaconecta.app.validation

import com.albaconecta.app.viewmodel.ContactErrors
import com.albaconecta.app.viewmodel.ContactFormState
import java.util.Calendar
import java.util.TimeZone

object ContactValidator {
    private val correoRegex = Regex("""[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}""")
    private val telefonoRegex = Regex("""(?:\+56\s?)?[2-9]\d{8}""")

    fun validate(
        form: ContactFormState,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): ContactErrors = ContactErrors(
        nombre = when {
            form.nombre.isBlank() -> "Ingresa tu nombre"
            form.nombre.trim().length < 2 -> "El nombre debe tener al menos 2 caracteres"
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
        mensaje = if (form.mensaje.isBlank()) "Ingresa un mensaje" else null,
        cantidadTrabajadores = when {
            !form.representaEmpresa -> null
            form.cantidadTrabajadores == null -> "Ingresa la cantidad de trabajadores"
            form.cantidadTrabajadores <= 0 -> "La cantidad de trabajadores debe ser mayor que 0"
            else -> null
        },
        fechaContacto = when {
            form.fechaContacto == null -> "Selecciona una fecha de contacto"
            utcDate(form.fechaContacto).before(utcDate(referenceTimeMillis)) ->
                "La fecha de contacto no puede estar en el pasado"
            else -> null
        },
        autorizaContacto = if (!form.autorizaContacto) {
            "Debes autorizar el contacto para enviar la solicitud."
        } else null
    )

    private fun utcDate(timeMillis: Long): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
