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

@Composable
fun TalentScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Talento Alba",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Este espacio busca visibilizar capacidades, promover oportunidades de inclusión laboral y acercar a personas y organizaciones.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        listOf(
            "Desarrollo de habilidades" to "Fortalecer capacidades y favorecer el desarrollo personal y laboral de las personas.",
            "Oportunidades inclusivas" to "Promover entornos donde las personas puedan participar y aportar de acuerdo con sus capacidades.",
            "Vínculo con organizaciones" to "Acercar organizaciones y oportunidades de colaboración orientadas a la inclusión laboral."
        ).forEach { (title, description) ->
            item {
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
