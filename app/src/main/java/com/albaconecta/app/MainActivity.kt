package com.albaconecta.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.albaconecta.app.ui.theme.AlbaConectaTheme
import com.albaconecta.app.ui.AlbaConectaApp
import com.albaconecta.app.data.remote.ApiClient

// FragmentActivity (subclase de ComponentActivity): BiometricPrompt la exige para
// montar su dialogo del sistema mediante un Fragment interno que gestiona la libreria.
// setContent y enableEdgeToEdge se heredan de ComponentActivity, asi nada mas cambia.
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            AlbaConectaTheme() {
                AlbaConectaApp()
            }
        }
    }
}