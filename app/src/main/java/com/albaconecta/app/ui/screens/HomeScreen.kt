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
fun HomeScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bienvenido a Alba Conecta",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Alba Conecta es un espacio para conocer iniciativas de inclusión, impacto social y oportunidades de colaboración.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Conoce nuestro propósito",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Buscamos promover la inclusión social y laboral y facilitar el acceso a información sobre iniciativas y formas de colaborar.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        item {
            Text(
                "Explora las áreas de la aplicación",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        }
        listOf(
            "Impacto" to "Conoce programas e iniciativas y su aporte a la comunidad.",
            "Talento Alba" to "Descubre el enfoque de inclusión y oportunidades laborales.",
            "Colaborar" to "Encuentra una forma sencilla de ponerte en contacto y participar."
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
