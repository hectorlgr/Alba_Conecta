package com.albaconecta.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.albaconecta.app.ui.screens.HomeScreen
import com.albaconecta.app.ui.screens.LoginScreen
import com.albaconecta.app.ui.screens.RegisterScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onRegisterClick = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onBackToLogin = { navController.popBackStack() }
            )
        }
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
