package com.albaconecta.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onProfileClick: () -> Unit) {
    Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
        Text("Inicio")
        Button(onClick = onProfileClick) { Text("Perfil") }
    }
}
