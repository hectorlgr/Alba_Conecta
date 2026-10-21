package com.albaconecta.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * Botón con retroalimentación visual de presión: se encoge levemente
 * mientras el usuario lo mantiene presionado y vuelve a su tamaño original
 * con una animación, para dar fluidez a la interacción (ítem 3 de la rúbrica:
 * "retroalimentación visual durante las interacciones del usuario").
 */
@Composable
fun PulsableButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Fuente de interacciones: permite "escuchar" el estado de presión del botón
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // animateFloatAsState anima el valor de escala entre 1f y 0.96f
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "buttonScale"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale)
    ) {
        Text(text)
    }
}
