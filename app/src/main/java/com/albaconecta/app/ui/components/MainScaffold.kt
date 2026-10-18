package com.albaconecta.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
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
                title = { Text("Alba Conecta") },
                actions = {
                    accountActionLabel?.let { label ->
                        TextButton(onClick = onAccountClick) { Text(label) }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Routes.HOME to "Inicio",
                    Routes.IMPACT to "Impacto",
                    Routes.TALENT to "Talento",
                    Routes.CONTACT to "Colaborar"
                ).forEach { (route, label) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = { onDestinationClick(route) },
                        icon = { Text(label.take(1), modifier = Modifier.clearAndSetSemantics { }) },
                        label = { Text(label) }
                    )
                }
            }
        },
        content = content
    )
}
