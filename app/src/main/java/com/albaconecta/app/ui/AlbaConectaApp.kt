package com.albaconecta.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.albaconecta.app.navigation.AppNavigation
import com.albaconecta.app.navigation.Routes
import com.albaconecta.app.viewmodel.AppViewModel

@Composable
fun AlbaConectaApp(appViewModel: AppViewModel = viewModel()) {
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()

    if (sessionState.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Keep the initial route stable; later session changes require explicit navigation.
    val startDestination = rememberSaveable {
        if (!sessionState.error && sessionState.loggedUserId != null) {
            Routes.HOME
        } else {
            Routes.LOGIN
        }
    }
    val navController = rememberNavController()

    AppNavigation(
        navController = navController,
        startDestination = startDestination
    )
}
