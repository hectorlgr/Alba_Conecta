package com.albaconecta.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.ui.components.ErrorStateIcon
import com.albaconecta.app.ui.components.PulsableButton
import com.albaconecta.app.ui.components.SuccessIcon
import com.albaconecta.app.util.LocalNotificationHelper
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
    var rutValue by remember {
        mutableStateOf(normalizeRutInput(TextFieldValue(form.rut)))
    }
    LaunchedEffect(form.rut) {
        val normalized = normalizeRutInput(TextFieldValue(form.rut)).text
        if (rutValue.text != normalized) {
            rutValue = TextFieldValue(
                normalized,
                TextRange(
                    rutValue.selection.start.coerceIn(0, normalized.length),
                    rutValue.selection.end.coerceIn(0, normalized.length)
                )
            )
        }
    }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (registerViewModel.consumeRegistrationNotification() && granted) {
            LocalNotificationHelper.showRegistrationSuccess(context)
        }
    }

    LaunchedEffect(registration.success) {
        if (registration.success && registerViewModel.beginRegistrationNotification()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (registerViewModel.consumeRegistrationNotification()) {
                LocalNotificationHelper.showRegistrationSuccess(context)
            }
        }
    }

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
            value = rutValue,
            onValueChange = { proposed ->
                val normalized = normalizeRutInput(proposed)
                // Reject overlong edits instead of silently removing the check digit.
                if (normalized.text.length <= 9) {
                    rutValue = normalized
                    if (normalized.text != form.rut) {
                        registerViewModel.updateRut(normalized.text)
                    }
                }
            },
            label = { Text("RUT") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            visualTransformation = RutVisualTransformation,
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
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Seleccionar fecha de nacimiento"
                    )
                }
            },
            supportingText = errors.fechaNacimiento?.let { message -> { Text(message) } }
        )
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

private fun normalizeRutInput(value: TextFieldValue): TextFieldValue {
    fun isRutCharacter(character: Char): Boolean =
        character in '0'..'9' || character == 'k' || character == 'K'

    val normalized = value.text.filter(::isRutCharacter).uppercase(Locale.ROOT)
    if (normalized == value.text) return value

    fun mapOffset(offset: Int): Int =
        value.text.take(offset).count(::isRutCharacter)

    fun mapRange(range: TextRange): TextRange =
        TextRange(mapOffset(range.start), mapOffset(range.end))

    return TextFieldValue(
        text = normalized,
        selection = mapRange(value.selection),
        composition = value.composition?.let(::mapRange)
    )
}

private object RutVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = StringBuilder()
        val originalOffsets = IntArray(text.length + 1)
        val transformedOffsets = mutableListOf(0)
        val bodyLength = (text.length - 1).coerceAtLeast(0)

        text.text.forEachIndexed { index, character ->
            val separator = when {
                index > 0 && index == bodyLength -> '-'
                index > 0 && index < bodyLength && (bodyLength - index) % 3 == 0 -> '.'
                else -> null
            }
            if (separator != null) {
                formatted.append(separator)
                // Both sides of a visual separator refer to the same internal boundary.
                transformedOffsets.add(index)
            }
            originalOffsets[index] = formatted.length
            formatted.append(character)
            transformedOffsets.add(index + 1)
        }
        originalOffsets[text.length] = formatted.length

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalOffsets[offset.coerceIn(0, text.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transformedOffsets[offset.coerceIn(0, formatted.length)]
        }
        return TransformedText(AnnotatedString(formatted.toString()), offsetMapping)
    }
}
