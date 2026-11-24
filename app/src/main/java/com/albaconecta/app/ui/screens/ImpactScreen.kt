package com.albaconecta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.ui.components.AnimatedEntrance
import com.albaconecta.app.viewmodel.EstadoContenido
import com.albaconecta.app.viewmodel.IndicadoresViewModel

@Composable
fun ImpactScreen(indicadoresViewModel: IndicadoresViewModel = viewModel()) {
    val estadoIndicadores by indicadoresViewModel.estado.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AnimatedEntrance {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Impacto",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() }
                        )
                        // Botón de sincronización manual
                        IconButton(onClick = indicadoresViewModel::sincronizar) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar indicadores",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "Estos son los indicadores que la fundación reporta sobre su trabajo con la comunidad.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        // Indicadores desde el backend (base de datos), no hardcodeados.
        when (val estado = estadoIndicadores) {
            is EstadoContenido.Cargando -> item {
                CircularProgressIndicator()
            }

            is EstadoContenido.Error -> item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(estado.mensaje, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = indicadoresViewModel::cargar) { Text("Reintentar") }
                }
            }

            is EstadoContenido.Ok -> if (estado.datos.isEmpty()) {
                item { Text("Todavía no hay indicadores publicados.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                estado.datos.forEachIndexed { index, indicador ->
                    item(key = indicador.id) {
                        // Entrada escalonada: cada tarjeta aparece 80 ms después de la anterior
                        AnimatedEntrance(delayMillis = 100 + index * 80) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        indicador.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Text(
                                        "${formatearValor(indicador.valor)} ${indicador.unidad.orEmpty()}".trim(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    indicador.periodo?.let {
                                        Text("Periodo: $it", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    indicador.fuente?.let {
                                        Text("Fuente: $it", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Los indicadores son conteos o porcentajes: sin decimales si el valor es entero. */
private fun formatearValor(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toInt().toString() else "%.1f".format(valor)
