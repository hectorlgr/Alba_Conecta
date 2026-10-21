package com.albaconecta.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.albaconecta.app.ui.components.MainScaffold
import com.albaconecta.app.ui.screens.ContactScreen
import com.albaconecta.app.ui.screens.ImpactScreen
import com.albaconecta.app.ui.screens.TalentScreen
import com.albaconecta.app.ui.screens.HomeScreen
import com.albaconecta.app.ui.screens.LoginScreen
import com.albaconecta.app.ui.screens.ProfileScreen
import com.albaconecta.app.ui.screens.RegisterScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    sessionLoading: Boolean,
    isAuthenticated: Boolean,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Transiciones entre pantallas: fundido + deslizamiento horizontal sutil
        enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 8 } },
        exitTransition = { fadeOut(tween(200)) },
        // Al retroceder (botón "atrás"), la animación se reproduce en sentido inverso
        popEnterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { -it / 8 } },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 8 } }
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onRegisterClick = { navController.navigate(Routes.REGISTER) },
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onBackToLogin = { navController.popBackStack() }
            )
        }
        listOf(Routes.HOME, Routes.IMPACT, Routes.TALENT, Routes.CONTACT).forEach { route ->
            composable(route) {
                MainScaffold(
                    currentRoute = route,
                    onDestinationClick = { destination ->
                        if (destination != route) {
                            navController.navigate(destination) {
                                popUpTo(Routes.HOME)
                                launchSingleTop = true
                            }
                        }
                    },
                    accountActionLabel = when {
                        sessionLoading -> null
                        isAuthenticated -> "Perfil"
                        else -> "Iniciar sesión"
                    },
                    onAccountClick = {
                        val destination = if (isAuthenticated) Routes.PROFILE else Routes.LOGIN
                        navController.navigate(destination) { launchSingleTop = true }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (route) {
                            Routes.HOME -> HomeScreen()
                            Routes.IMPACT -> ImpactScreen()
                            Routes.TALENT -> TalentScreen()
                            Routes.CONTACT -> ContactScreen()
                        }
                    }
                }
            }
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
