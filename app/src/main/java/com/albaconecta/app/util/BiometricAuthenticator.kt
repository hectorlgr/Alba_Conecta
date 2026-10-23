package com.albaconecta.app.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Resultado de un intento de autenticación biométrica.
 *
 * 'sealed class' restringe las subclases a este archivo: el compilador nos obliga
 * a manejar TODOS los casos en un 'when', así ninguna rama puede quedar olvidada.
 */
sealed class BiometricAuthResult {
    /** La huella coincidió: el sistema operativo autorizó continuar. */
    data object Success : BiometricAuthResult()

    /** El usuario canceló o eligió el botón negativo ("Usar contraseña"): no es un error. */
    data object Cancelled : BiometricAuthResult()

    /** Error terminal del sensor (lockout tras varios fallos, hardware, etc.). */
    data class Error(val code: Int, val message: String) : BiometricAuthResult()
}

/**
 * Lógica pura: ¿debe mostrarse el botón de login biométrico?
 * Sin estado ni dependencias de Android → testeable con JUnit4 puro
 * (misma filosofía que los validadores de validation/).
 */
fun shouldShowBiometricLogin(enabled: Boolean, available: Boolean, userExists: Boolean): Boolean =
    enabled && available && userExists

/**
 * Lógica pura: ¿el código de error representa una cancelación voluntaria del usuario?
 * ERROR_USER_CANCELED y ERROR_NEGATIVE_BUTTON no deben mostrarse como errores.
 */
fun isUserInitiatedCancellation(errorCode: Int): Boolean =
    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON

/**
 * Wrapper delgado sobre BiometricPrompt (el diálogo biométrico del sistema).
 *
 * División de responsabilidades (importante para el lifecycle):
 * - La SCREEN crea la instancia: es la única que tiene la FragmentActivity que
 *   exige la API. El ViewModel NUNCA guarda una Activity (el ViewModel sobrevive
 *   a la rotación de pantalla y retenerla sería un memory leak).
 * - Por eso la verificación de disponibilidad es 'companion' y solo pide Context:
 *   los ViewModels la consultan con el Application de AndroidViewModel.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    companion object {
        /** ¿El dispositivo tiene sensor Y hay una huella "fuerte" (Clase 3) inscrita? */
        fun canAuthenticate(context: Context): Boolean =
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Muestra el diálogo del sistema y SUSPENDE la corrutina hasta que el usuario
     * responde. Patrón oficial kotlinx.coroutines 'suspendCancellableCoroutine':
     * convierte la API de callbacks en una función suspend.
     *
     * Debe llamarse desde el hilo principal: un onClick con scope.launch lo garantiza.
     * Si la corrutina se cancela (la pantalla sale de composición), el diálogo
     * se cierra vía invokeOnCancellation → cancelación estructurada.
     */
    suspend fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String
    ): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity), // callbacks devueltos en el hilo principal
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    continuation.resume(BiometricAuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (isUserInitiatedCancellation(errorCode)) {
                        continuation.resume(BiometricAuthResult.Cancelled)
                    } else {
                        continuation.resume(BiometricAuthResult.Error(errorCode, errString.toString()))
                    }
                }

                // onAuthenticationFailed (huella no reconocida) NO se sobreescribe:
                // el diálogo sigue abierto y el usuario reintenta; no es un error terminal.
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title) // build() lanza IllegalArgumentException si queda vacío
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText) // el usuario SIEMPRE necesita una salida
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false) // con cara: sin "tocar para confirmar" extra
            .build()

        prompt.authenticate(promptInfo)

        // Si la corrutina se cancela, cerramos el diálogo para no dejarlo huérfano.
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
