package com.albaconecta.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.albaconecta.app.navigation.AppNavigation
import com.albaconecta.app.navigation.Routes
import com.albaconecta.app.viewmodel.AppViewModel

@Composable
fun AlbaConectaApp(appViewModel: AppViewModel = viewModel()) {
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()

    val navController = rememberNavController()

    AppNavigation(
        navController = navController,
        startDestination = Routes.HOME,
        sessionLoading = sessionState.loading,
        isAuthenticated = !sessionState.loading && !sessionState.error && sessionState.loggedUserId != null
    )
}
