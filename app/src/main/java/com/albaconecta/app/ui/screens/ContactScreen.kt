package com.albaconecta.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.ui.components.AnimatedEntrance
import com.albaconecta.app.ui.components.ErrorStateIcon
import com.albaconecta.app.ui.components.PulsableButton
import com.albaconecta.app.viewmodel.ContactViewModel
import com.albaconecta.app.util.CameraUriFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(contactViewModel: ContactViewModel = viewModel()) {
    val form by contactViewModel.formState.collectAsStateWithLifecycle()
    val errors by contactViewModel.errors.collectAsStateWithLifecycle()
    val submitState by contactViewModel.submitState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = LocalActivity.current
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraPermissionPending by rememberSaveable { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capturedUri = pendingPhotoUri
        pendingPhotoUri = null
        if (capturedUri != null) {
            if (success) {
                photoUri?.let { CameraUriFactory.delete(context, Uri.parse(it)) }
                photoUri = capturedUri
            } else {
                CameraUriFactory.delete(context, Uri.parse(capturedUri))
            }
        }
    }
    fun launchCamera() {
        if (pendingPhotoUri != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraError = "Se necesita permiso de cámara para tomar una foto. Puedes habilitarlo desde Ajustes."
            return
        }
        cameraError = null
        try {
            val uri = CameraUriFactory.create(context)
            pendingPhotoUri = uri.toString()
            cameraLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingPhotoUri?.let { CameraUriFactory.delete(context, Uri.parse(it)) }
            pendingPhotoUri = null
            cameraError = "No hay una aplicación de cámara disponible."
        } catch (_: SecurityException) {
            pendingPhotoUri?.let { CameraUriFactory.delete(context, Uri.parse(it)) }
            pendingPhotoUri = null
            cameraError = "No se pudo acceder a la cámara. Revisa el permiso de cámara en Ajustes."
        } catch (_: Exception) {
            pendingPhotoUri?.let { CameraUriFactory.delete(context, Uri.parse(it)) }
            pendingPhotoUri = null
            cameraError = "No se pudo preparar la captura. Inténtalo nuevamente."
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionPending = false
        if (granted) {
            launchCamera()
        } else {
            cameraError = if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.CAMERA
                )) {
                "Permiso de cámara denegado. Puedes volver a intentarlo con Tomar foto."
            } else {
                "Permiso de cámara denegado. Puedes habilitarlo desde Ajustes de la aplicación."
            }
        }
    }
    LaunchedEffect(submitState.success) {
        if (submitState.success) {
            photoUri?.let { CameraUriFactory.delete(context, Uri.parse(it)) }
            photoUri = null
        }
    }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speechActive by remember { mutableStateOf(false) }
    var speechProcessing by remember { mutableStateOf(false) }
    var audioPermissionPending by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun releaseSpeech() {
        val recognizer = speechRecognizer
        // Ignore any callbacks arriving after cancellation or from a previous session.
        speechRecognizer = null
        speechActive = false
        speechProcessing = false
        if (recognizer != null) {
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }

    fun startDictation() {
        if (speechActive) return
        speechError = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speechError = "Se necesita permiso de micrófono para dictar. Puedes habilitarlo desde Ajustes."
            return
        }
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                speechError = "El reconocimiento de voz no está disponible en este dispositivo."
                return
            }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            speechActive = true
            speechProcessing = false
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onEndOfSpeech() {
                    if (speechRecognizer === recognizer) speechProcessing = true
                }

                override fun onError(error: Int) {
                    if (speechRecognizer !== recognizer) return
                    releaseSpeech()
                    speechError = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No se reconocieron palabras. Puedes volver a intentarlo."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "No se pudo acceder al micrófono. Revisa el permiso en Ajustes."
                        else -> "No se pudo completar el dictado. Inténtalo nuevamente."
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (speechRecognizer !== recognizer) return
                    val transcription = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                    releaseSpeech()
                    if (transcription != null) {
                        val currentMessage = contactViewModel.formState.value.mensaje
                        contactViewModel.updateMensaje(
                            if (currentMessage.isEmpty()) transcription else "$currentMessage $transcription"
                        )
                    } else {
                        speechError = "No se reconocieron palabras. Puedes volver a intentarlo."
                    }
                }
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            })
        } catch (_: SecurityException) {
            releaseSpeech()
            speechError = "No se pudo acceder al micrófono. Revisa el permiso en Ajustes."
        } catch (_: Exception) {
            releaseSpeech()
            speechError = "No se pudo iniciar el dictado. Inténtalo nuevamente."
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        audioPermissionPending = false
        if (granted) {
            startDictation()
        } else {
            speechError = "Permiso de micrófono denegado. Puedes volver a intentarlo o habilitarlo desde Ajustes."
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) releaseSpeech()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releaseSpeech()
        }
    }
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
        AnimatedEntrance {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Colaborar", style = MaterialTheme.typography.headlineMedium)
                AnimatedVisibility(
                    visible = submitState.success,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "♥ Gracias",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Formulario completado correctamente. Confirmación local de demostración.",
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        AnimatedEntrance(delayMillis = 80) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.nombre, onValueChange = contactViewModel::updateNombre,
                    label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = errors.nombre != null,
                    trailingIcon = { ErrorStateIcon(errors.nombre != null) },
                    supportingText = errors.nombre?.let { message -> { Text(message) } }
                )
                OutlinedTextField(
                    value = form.correo, onValueChange = contactViewModel::updateCorreo,
                    label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = errors.correo != null,
                    trailingIcon = { ErrorStateIcon(errors.correo != null) },
                    supportingText = errors.correo?.let { message -> { Text(message) } }
                )
                OutlinedTextField(
                    value = form.telefono, onValueChange = contactViewModel::updateTelefono,
                    label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = errors.telefono != null,
                    trailingIcon = { ErrorStateIcon(errors.telefono != null) },
                    supportingText = errors.telefono?.let { message -> { Text(message) } }
                )
                OutlinedTextField(
                    value = form.mensaje, onValueChange = contactViewModel::updateMensaje,
                    label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth(), minLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = errors.mensaje != null,
                    trailingIcon = { ErrorStateIcon(errors.mensaje != null) },
                    supportingText = errors.mensaje?.let { message -> { Text(message) } }
                )
            }
        }
        AnimatedEntrance(delayMillis = 160) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    enabled = !speechActive && !audioPermissionPending,
                    onClick = {
                        speechError = null

                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startDictation()
                        } else {
                            audioPermissionPending = true
                            audioPermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    }
                ) {
                    Text("Dictar")
                }

                if (speechActive) {
                    Text(
                        if (speechProcessing) {
                            "Procesando dictado..."
                        } else {
                            "Escuchando..."
                        }
                    )

                    TextButton(onClick = { releaseSpeech() }) {
                        Text("Cancelar dictado")
                    }
                }

                speechError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(
                    enabled = pendingPhotoUri == null &&
                            !cameraPermissionPending,
                    onClick = {
                        cameraError = null

                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchCamera()
                        } else {
                            cameraPermissionPending = true
                            cameraPermissionLauncher.launch(
                                Manifest.permission.CAMERA
                            )
                        }
                    }
                ) {
                    Text("Tomar foto")
                }
                if (photoUri != null) {
                    Text("Foto adjunta")
                    TextButton(onClick = {
                        photoUri?.let { CameraUriFactory.delete(context, Uri.parse(it)) }
                        photoUri = null
                    }) { Text("Quitar foto") }
                }
                cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        AnimatedEntrance(delayMillis = 240) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        trailingIcon = { ErrorStateIcon(errors.cantidadTrabajadores != null) },
                        supportingText = errors.cantidadTrabajadores?.let { message -> { Text(message) } }
                    )
                }
            }
        }
        AnimatedEntrance(delayMillis = 300) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = formattedDate, onValueChange = {}, readOnly = true,
                    label = { Text("Fecha preferida de contacto") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = errors.fechaContacto != null,
                    trailingIcon = { ErrorStateIcon(errors.fechaContacto != null) },
                    supportingText = errors.fechaContacto?.let { message -> { Text(message) } }
                )
                TextButton(onClick = { showDatePicker = true }) { Text("Seleccionar fecha") }
            }
        }
        AnimatedEntrance(delayMillis = 360) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = form.autorizaContacto, onCheckedChange = contactViewModel::updateAutorizaContacto)
                    Text("Autorizo que me contacten respecto de esta solicitud.")
                }
                errors.autorizaContacto?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                PulsableButton(
                    onClick = contactViewModel::submit,
                    text = "Enviar solicitud",
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
