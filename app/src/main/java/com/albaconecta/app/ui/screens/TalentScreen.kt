package com.albaconecta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albaconecta.app.ui.components.AnimatedEntrance
import com.albaconecta.app.viewmodel.TalentoViewModel
import com.albaconecta.app.viewmodel.EstadoContenido

@Composable
fun TalentScreen(talentoViewModel: TalentoViewModel = viewModel()) {
    // Collect StateFlow directamente para evitar problemas con sealed interface genérica
    val candidatosState = talentoViewModel.candidatos.collectAsStateWithLifecycle()
    val apoyosState = talentoViewModel.apoyos.collectAsStateWithLifecycle()

    val candidatos = when (val state = candidatosState.value) {
        is EstadoContenido.Ok -> state.datos
        else -> emptyList<com.albaconecta.app.data.remote.CandidatoDto>()
    }
    val apoyos = when (val state = apoyosState.value) {
        is EstadoContenido.Ok -> state.datos.associateBy { it.codigo }
        else -> emptyMap<String, com.albaconecta.app.data.remote.ApoyoDto>()
    }
    val loading = candidatosState.value is EstadoContenido.Cargando || apoyosState.value is EstadoContenido.Cargando
    val error = candidatosState.value is EstadoContenido.Error || apoyosState.value is EstadoContenido.Error

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            AnimatedEntrance {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Talento Alba",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { heading() }
                    )
                    Text(
                        "Candidatos anonimizados con apoyos declarados. Sin apoyos = no evaluable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (error) {
                        Text(
                            "No se pudo cargar el catálogo. Revisa tu conexión.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        items(candidatos) { candidato ->
            AnimatedEntrance(delayMillis = 50) {
                val tieneApoyos = candidato.apoyos.isNotEmpty()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tieneApoyos) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Candidato ${candidato.codigoPublico} — ${candidato.iniciales}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (!tieneApoyos) {
                                Text(
                                    "No evaluable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Text(candidato.resumen, style = MaterialTheme.typography.bodyMedium)
                        if (tieneApoyos) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Apoyos declarados:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                candidato.apoyos.forEach { codigo ->
                                    val apoyo = apoyos[codigo]
                                    Text(
                                        "• ${apoyo?.nombre ?: codigo} ${apoyo?.descripcion?.let { "— $it" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    "Actualizado: ${formatearFecha(candidato.fechaActualizacion)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (candidatos.isEmpty() && !loading) {
            item {
                Text(
                    "No hay candidatos publicados en este momento.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .semantics { heading() }
                )
            }
        }
    }
}

// El DatePicker entrega medianoche UTC: se formatea en UTC.
private fun formatearFecha(iso: String): String {
    try {
        // java.time no disponible en API < 26 sin desugaring; usamos SimpleDateFormat
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(iso)
        val out = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
        out.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return out.format(date)
    } catch (_: Exception) {
        return iso
    }
}
