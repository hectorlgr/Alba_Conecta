package com.albaconecta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.albaconecta.app.ui.components.AnimatedEntrance

@Composable
fun ImpactScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AnimatedEntrance {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Impacto",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        "Este espacio permite conocer iniciativas orientadas a la inclusión social y laboral y su aporte a la comunidad.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        listOf(
            "Inclusión social" to "Promover la participación, la autonomía y las oportunidades de integración en la vida cotidiana.",
            "Inclusión laboral" to "Favorecer oportunidades de participación en entornos laborales y el desarrollo de habilidades.",
            "Trabajo con la comunidad" to "Generar vínculos entre personas y organizaciones para acercar oportunidades de colaboración."
        ).forEachIndexed { index, (title, description) ->
            item {
                // Entrada escalonada: cada tarjeta aparece 80 ms después de la anterior
                AnimatedEntrance(delayMillis = 100 + index * 80) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
