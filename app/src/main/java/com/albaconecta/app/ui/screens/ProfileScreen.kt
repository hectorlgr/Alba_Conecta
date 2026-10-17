package com.albaconecta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.viewmodel.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogoutSuccess: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val state by profileViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.logoutSuccess) {
        if (state.logoutSuccess) onLogoutSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineMedium)
        if (state.loading) {
            CircularProgressIndicator()
        }
        state.user?.let { user ->
            val birthDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(user.fechaNacimiento))

            Text("${user.nombre} ${user.apellido}", style = MaterialTheme.typography.titleLarge)
            Text("RUT: ${user.rut}")
            Text("Correo: ${user.correo}")
            Text("Teléfono: ${user.telefono}")
            Text("Fecha de nacimiento: $birthDate")
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = profileViewModel::logout,
            enabled = !state.loading && !state.logoutLoading && !state.logoutSuccess,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.logoutLoading) "Cerrando sesión..." else "Cerrar sesión")
        }
        TextButton(onClick = onBack, enabled = !state.logoutLoading && !state.logoutSuccess) {
            Text("Volver")
        }
    }
}
