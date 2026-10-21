package com.albaconecta.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Iconos de estado para los campos de formulario.
 *  estos helpers muestran un ícono solo cuando el campo o la
 * operación lo amerita, tomando el color del ColorScheme del tema.
 */

/** Advertencia (triángulo con exclamación) que acompaña a un campo con error de validación. */
@Composable
fun ErrorStateIcon(hasError: Boolean) {
    if (hasError) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = "Campo con error",
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/** Check que acompaña a un mensaje de éxito. */
@Composable
fun SuccessIcon() {
    Icon(
        imageVector = Icons.Outlined.CheckCircle,
        contentDescription = "Operación exitosa",
        tint = MaterialTheme.colorScheme.primary
    )
}
