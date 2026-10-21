package com.albaconecta.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.albaconecta.app.R
import com.albaconecta.app.ui.components.AnimatedEntrance

@Composable
fun HomeScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AnimatedEntrance {
                // Sello de marca: logo con su caja blanca ajustada al contenido
                // (sin fillMaxWidth/fillMaxSize el Surface "abraza" al logo) y
                // alineado a la esquina derecha de la pantalla
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        // Fondo blanco fijo: el logo tiene tipografía oscura y debe
                        // mantenerse legible también en modo oscuro
                        color = Color.White
                    ) {
                        Image(
                            painter = painterResource(R.drawable.corpaliv_logo),
                            contentDescription = "Logo de CORPALIV",
                            modifier = Modifier.width(180.dp).heightIn(max = 64.dp).padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
        item {
            AnimatedEntrance(delayMillis = 80) {
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
        }
        item {
            AnimatedEntrance(delayMillis = 160) {
                Text(
                    "Explora las áreas de la aplicación",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
            }
        }
        listOf(
            "Impacto" to "Conoce programas e iniciativas y su aporte a la comunidad.",
            "Talento Alba" to "Descubre el enfoque de inclusión y oportunidades laborales.",
            "Colaborar" to "Encuentra una forma sencilla de ponerte en contacto y participar."
        ).forEachIndexed { index, (title, description) ->
            item {
                // Entrada escalonada: cada tarjeta aparece 80 ms después de la anterior
                AnimatedEntrance(delayMillis = 240 + index * 80) {
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
