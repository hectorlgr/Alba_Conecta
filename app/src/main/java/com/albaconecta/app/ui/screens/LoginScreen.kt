package com.albaconecta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.ui.components.ErrorStateIcon
import com.albaconecta.app.ui.components.PulsableButton
import com.albaconecta.app.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onRegisterClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    loginViewModel: LoginViewModel = viewModel()
) {
    val form by loginViewModel.formState.collectAsStateWithLifecycle()
    val errors by loginViewModel.errors.collectAsStateWithLifecycle()
    val loginState by loginViewModel.loginState.collectAsStateWithLifecycle()

    LaunchedEffect(loginState.success) {
        if (loginState.success) onLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = form.correo, onValueChange = loginViewModel::updateCorreo,
            label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = errors.correo != null,
            trailingIcon = { ErrorStateIcon(errors.correo != null) },
            supportingText = errors.correo?.let { message -> { Text(message) } }
        )
        OutlinedTextField(
            value = form.password, onValueChange = loginViewModel::updatePassword,
            label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            isError = errors.password != null,
            trailingIcon = { ErrorStateIcon(errors.password != null) },
            supportingText = errors.password?.let { message -> { Text(message) } }
        )
        loginState.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        PulsableButton(
            onClick = loginViewModel::login,
            text = if (loginState.loading) "Iniciando sesión..." else "Iniciar sesión",
            modifier = Modifier.fillMaxWidth(),
            enabled = !loginState.loading && !loginState.success
        )
        TextButton(onClick = onRegisterClick, enabled = !loginState.loading && !loginState.success) {
            Text("Crear cuenta")
        }
    }
}
