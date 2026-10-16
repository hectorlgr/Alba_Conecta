package com.albaconecta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.albaconecta.app.ui.theme.AlbaConectaTheme
import com.albaconecta.app.ui.AlbaConectaApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlbaConectaTheme() {
                AlbaConectaApp()
            }
        }
    }
}