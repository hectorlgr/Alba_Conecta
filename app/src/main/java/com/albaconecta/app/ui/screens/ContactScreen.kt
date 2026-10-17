package com.albaconecta.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.viewmodel.ContactViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(contactViewModel: ContactViewModel = viewModel()) {
    val form by contactViewModel.formState.collectAsStateWithLifecycle()
    val errors by contactViewModel.errors.collectAsStateWithLifecycle()
    val submitState by contactViewModel.submitState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    val formattedDate = remember(form.fechaContacto) {
        form.fechaContacto?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(it))
        }.orEmpty()
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Colaborar", style = MaterialTheme.typography.headlineMedium)
        AnimatedVisibility(visible = submitState.success) {
            Text("Formulario completado correctamente. Confirmación local de demostración.",
                color = MaterialTheme.colorScheme.primary)
        }
        OutlinedTextField(
            value = form.nombre, onValueChange = contactViewModel::updateNombre,
            label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = errors.nombre != null,
            supportingText = errors.nombre?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.correo, onValueChange = contactViewModel::updateCorreo,
            label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = errors.correo != null,
            supportingText = errors.correo?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.telefono, onValueChange = contactViewModel::updateTelefono,
            label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = errors.telefono != null,
            supportingText = errors.telefono?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.mensaje, onValueChange = contactViewModel::updateMensaje,
            label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth(), minLines = 4,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = errors.mensaje != null,
            supportingText = errors.mensaje?.let { message -> { Text(message) } }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Represento a una empresa u organización", modifier = Modifier.weight(1f))
            Switch(checked = form.representaEmpresa, onCheckedChange = contactViewModel::updateRepresentaEmpresa)
        }
        AnimatedVisibility(visible = form.representaEmpresa) {
            OutlinedTextField(
                value = form.cantidadTrabajadores?.toString().orEmpty(),
                onValueChange = contactViewModel::updateCantidadTrabajadores,
                label = { Text("Cantidad de trabajadores") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = errors.cantidadTrabajadores != null,
                supportingText = errors.cantidadTrabajadores?.let { message -> { Text(message) } }
            )
        }
        OutlinedTextField(
            value = formattedDate, onValueChange = {}, readOnly = true,
            label = { Text("Fecha preferida de contacto") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            isError = errors.fechaContacto != null,
            supportingText = errors.fechaContacto?.let { message -> { Text(message) } }
        )
        TextButton(onClick = { showDatePicker = true }) { Text("Seleccionar fecha") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.autorizaContacto, onCheckedChange = contactViewModel::updateAutorizaContacto)
            Text("Autorizo que me contacten respecto de esta solicitud.")
        }
        errors.autorizaContacto?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = contactViewModel::submit, modifier = Modifier.fillMaxWidth()) {
            Text("Enviar solicitud")
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = form.fechaContacto)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    contactViewModel.updateFechaContacto(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = datePickerState) }
    }
}
