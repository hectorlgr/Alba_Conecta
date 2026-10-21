package com.albaconecta.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.albaconecta.app.R
import com.albaconecta.app.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    currentRoute: String,
    onDestinationClick: (String) -> Unit,
    accountActionLabel: String?,
    onAccountClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Encabezado de marca: isotipo CORPALIV + nombre de la app
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.corpaliv_isotipo),
                            // Icono decorativo: el texto "Alba Conecta" ya describe la marca
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Alba Conecta")
                    }
                },
                actions = {
                    accountActionLabel?.let { label ->
                        TextButton(onClick = onAccountClick) { Text(label) }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                // Triple: (ruta, etiqueta, icono) por cada destino de la barra inferior
                listOf(
                    Triple(Routes.HOME, "Inicio", Icons.Outlined.Home),
                    Triple(Routes.IMPACT, "Impacto", Icons.AutoMirrored.Outlined.List),
                    Triple(Routes.TALENT, "Talento", Icons.Outlined.Person),
                    Triple(Routes.CONTACT, "Colaborar", Icons.Outlined.FavoriteBorder)
                ).forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = { onDestinationClick(route) },
                        // Icono decorativo: el label del ítem ya describe el destino
                        icon = { Icon(imageVector = icon, contentDescription = null) },
                        label = { Text(label) }
                    )
                }
            }
        },
        content = content
    )
}
