package com.albaconecta.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.ui.components.ErrorStateIcon
import com.albaconecta.app.ui.components.PulsableButton
import com.albaconecta.app.ui.components.SuccessIcon
import com.albaconecta.app.viewmodel.RegisterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBackToLogin: () -> Unit,
    registerViewModel: RegisterViewModel = viewModel()
) {
    val form by registerViewModel.formState.collectAsStateWithLifecycle()
    val errors by registerViewModel.errors.collectAsStateWithLifecycle()
    val registration by registerViewModel.registrationState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    val formattedDate = remember(form.fechaNacimiento) {
        form.fechaNacimiento?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(it))
        }.orEmpty()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Registro", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = form.nombre, onValueChange = registerViewModel::updateNombre,
            label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = errors.nombre != null,
            trailingIcon = { ErrorStateIcon(errors.nombre != null) },
            supportingText = errors.nombre?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.apellido, onValueChange = registerViewModel::updateApellido,
            label = { Text("Apellido") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = errors.apellido != null,
            trailingIcon = { ErrorStateIcon(errors.apellido != null) },
            supportingText = errors.apellido?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.rut, onValueChange = registerViewModel::updateRut,
            label = { Text("RUT") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = errors.rut != null,
            trailingIcon = { ErrorStateIcon(errors.rut != null) },
            supportingText = errors.rut?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.correo, onValueChange = registerViewModel::updateCorreo,
            label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = errors.correo != null,
            trailingIcon = { ErrorStateIcon(errors.correo != null) },
            supportingText = errors.correo?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.telefono, onValueChange = registerViewModel::updateTelefono,
            label = { Text("Número de teléfono") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = errors.telefono != null,
            trailingIcon = { ErrorStateIcon(errors.telefono != null) },
            supportingText = errors.telefono?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = formattedDate, onValueChange = {}, readOnly = true,
            label = { Text("Fecha de nacimiento") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            isError = errors.fechaNacimiento != null,
            trailingIcon = { ErrorStateIcon(errors.fechaNacimiento != null) },
            supportingText = errors.fechaNacimiento?.let { message -> { Text(message) } }
        )
        TextButton(onClick = { showDatePicker = true }) { Text("Seleccionar fecha") }
        OutlinedTextField(
            value = form.password, onValueChange = registerViewModel::updatePassword,
            label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            isError = errors.password != null,
            trailingIcon = { ErrorStateIcon(errors.password != null) },
            supportingText = errors.password?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.confirmPassword, onValueChange = registerViewModel::updateConfirmPassword,
            label = { Text("Confirmar contraseña") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            isError = errors.confirmPassword != null,
            trailingIcon = { ErrorStateIcon(errors.confirmPassword != null) },
            supportingText = errors.confirmPassword?.let { message -> { Text(message) } }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.aceptaTerminos, onCheckedChange = registerViewModel::updateAceptaTerminos)
            Text("Acepto los términos y condiciones")
        }
        errors.aceptaTerminos?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        registration.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        // Éxito: icono de check + mensaje, apareciendo con una animación de escala
        AnimatedVisibility(
            visible = registration.success,
            enter = scaleIn(tween(300)) + fadeIn(tween(300))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SuccessIcon()
                Text("Cuenta creada correctamente.", color = MaterialTheme.colorScheme.primary)
            }
        }
        PulsableButton(
            onClick = registerViewModel::register,
            text = if (registration.loading) "Creando cuenta..." else "Crear cuenta",
            modifier = Modifier.fillMaxWidth(),
            enabled = !registration.loading && !registration.success
        )
        TextButton(onClick = onBackToLogin) { Text("Volver a iniciar sesión") }
    }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = form.fechaNacimiento)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    registerViewModel.updateFechaNacimiento(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
