package com.albaconecta.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Entrada animada reutilizable: el contenido aparece con un fundido y un
 * deslizamiento vertical la primera vez que se muestra en pantalla.
 *
 * delayMillis permite "escalonar" elementos consecutivos (efecto cascada),
 * ideal para las tarjetas de las pantallas informativas.
 *
 * Se usa `remember` (y no `rememberSaveable`) a propósito: al navegar entre
 * pantallas el estado se destruye, de modo que la animación se reproduce en
 * CADA visita. Con rememberSaveable el estado se restauraría en `true` al
 * volver y la pantalla aparecería estática.
 */
@Composable
fun AnimatedEntrance(
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 350)) +
            slideInVertically(tween(durationMillis = 350)) { it / 4 }
    ) {
        content()
    }
}
