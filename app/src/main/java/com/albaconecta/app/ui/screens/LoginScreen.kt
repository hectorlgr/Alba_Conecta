package com.albaconecta.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun LoginScreen(onRegisterClick: () -> Unit) {
    Column {
        Text("Iniciar sesión")
        Button(onClick = onRegisterClick) {
            Text("Crear cuenta")
        }
    }
}
