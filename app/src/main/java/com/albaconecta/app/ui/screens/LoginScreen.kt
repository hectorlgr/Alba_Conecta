package com.albaconecta.app.ui.screens

import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albaconecta.app.ui.components.ErrorStateIcon
import com.albaconecta.app.ui.components.PulsableButton
import com.albaconecta.app.util.BiometricAuthenticator
import com.albaconecta.app.util.BiometricAuthResult
import com.albaconecta.app.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onRegisterClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    loginViewModel: LoginViewModel = viewModel()
) {
    val form by loginViewModel.formState.collectAsStateWithLifecycle()
    val errors by loginViewModel.errors.collectAsStateWithLifecycle()
    val loginState by loginViewModel.loginState.collectAsStateWithLifecycle()
    val biometricState by loginViewModel.biometricState.collectAsStateWithLifecycle()

    LaunchedEffect(loginState.success) {
        if (loginState.success) onLoginSuccess()
    }

    // La Activity hospedante: BiometricPrompt exige una FragmentActivity (ver MainActivity).
    // 'as?' es un casteo seguro: si no lo fuera, devolveria null y el boton no apareceria.
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()

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
        // Bonus: acceso con huella. Solo aparece si el usuario lo activo en Perfil,
        // hay una cuenta en el dispositivo y hay biometria Clase 3 disponible.
        if (biometricState.showLoginButton && activity != null) {
            PulsableButton(
                onClick = {
                    // El prompt se lanza desde el evento (onClick), nunca desde el
                    // cuerpo del Composable: cada recomposicion lo relanzaria.
                    scope.launch {
                        val result = BiometricAuthenticator(activity).authenticate(
                            title = "Iniciar sesión",
                            subtitle = "Usa tu huella para entrar a Alba Conecta",
                            negativeButtonText = "Usar contraseña"
                        )
                        when (result) {
                            BiometricAuthResult.Success -> loginViewModel.onBiometricAuthenticated()
                            is BiometricAuthResult.Error -> loginViewModel.onBiometricError(result.message)
                            BiometricAuthResult.Cancelled -> Unit // Volvio a la contrasena: no es un error.
                        }
                    }
                },
                text = "Iniciar sesión con huella",
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginState.loading && !loginState.success
            )
        }
        TextButton(onClick = onRegisterClick, enabled = !loginState.loading && !loginState.success) {
            Text("Crear cuenta")
        }
    }
    }
}
