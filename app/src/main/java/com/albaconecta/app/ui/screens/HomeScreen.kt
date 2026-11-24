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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.R
import com.albaconecta.app.ui.components.AnimatedEntrance
import com.albaconecta.app.viewmodel.EstadoContenido
import com.albaconecta.app.viewmodel.ProgramasViewModel

@Composable
fun HomeScreen(programasViewModel: ProgramasViewModel = viewModel()) {
    val estadoProgramas by programasViewModel.estado.collectAsStateWithLifecycle()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Programas de la fundación",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    // Botón de sincronización manual
                    IconButton(onClick = programasViewModel::sincronizar) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sincronizar programas",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        // Contenido desde el backend (base de datos), no hardcodeado.
        when (val estado = estadoProgramas) {
            is EstadoContenido.Cargando -> item {
                CircularProgressIndicator()
            }

            is EstadoContenido.Error -> item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(estado.mensaje, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = programasViewModel::cargar) { Text("Reintentar") }
                }
            }

            is EstadoContenido.Ok -> if (estado.datos.isEmpty()) {
                item { Text("Todavía no hay programas publicados.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                estado.datos.forEachIndexed { index, programa ->
                    item(key = programa.id) {
                        // Entrada escalonada: cada tarjeta aparece 80 ms después de la anterior
                        AnimatedEntrance(delayMillis = 240 + index * 80) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        programa.titulo,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Text(programa.descripcion, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
