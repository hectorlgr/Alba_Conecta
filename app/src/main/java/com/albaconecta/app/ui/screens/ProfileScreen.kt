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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    val sessionEnded = state.logoutSuccess || state.deleteSuccess
    val operationInProgress = state.logoutLoading || state.deleteLoading
    val canDelete = !state.loading && !operationInProgress && !sessionEnded && state.user != null

    LaunchedEffect(sessionEnded) {
        if (sessionEnded) onLogoutSuccess()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
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
            enabled = !state.loading && !operationInProgress && !sessionEnded,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.logoutLoading) "Cerrando sesión..." else "Cerrar sesión")
        }
        Button(
            onClick = { showDeleteDialog = true },
            enabled = canDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(if (state.deleteLoading) "Eliminando cuenta..." else "Eliminar cuenta")
        }
        TextButton(onClick = onBack, enabled = !operationInProgress && !sessionEnded) {
            Text("Volver")
        }
    }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar cuenta") },
            text = { Text("Esta acción eliminará la cuenta almacenada en este dispositivo.") },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
            confirmButton = {
                TextButton(
                    enabled = canDelete,
                    onClick = {
                        showDeleteDialog = false
                        profileViewModel.deleteAccount()
                    }
                ) {
                    Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
