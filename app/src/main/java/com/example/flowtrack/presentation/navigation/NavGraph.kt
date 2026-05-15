package com.example.flowtrack.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.flowtrack.presentation.components.FinanzasBottomNav
import com.example.flowtrack.presentation.screens.configuracion.ConfiguracionScreen
import com.example.flowtrack.presentation.screens.dashboard.DashboardScreen
import com.example.flowtrack.presentation.screens.login.LoginScreen
import com.example.flowtrack.presentation.screens.resumen.ResumenScreen
import com.example.flowtrack.presentation.screens.tarjetas.TarjetasScreen
import com.example.flowtrack.presentation.screens.transacciones.TransaccionesScreen

sealed class Screen(val route: String) {
    object Login          : Screen("login")
    object Dashboard      : Screen("dashboard")
    object Transacciones  : Screen("transacciones")
    object Resumen        : Screen("resumen")
    object Tarjetas       : Screen("tarjetas")
    object Configuracion  : Screen("configuracion")
}

private val bottomNavRoutes = setOf(
    Screen.Dashboard.route,
    Screen.Transacciones.route,
    Screen.Resumen.route,
    Screen.Tarjetas.route,
    Screen.Configuracion.route,
)

private val fadeSpec = tween<Float>(durationMillis = 240)

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String         = Screen.Login.route,
) {
    val backStack = navController.currentBackStackEntryAsState()
    val showBottom = backStack.value?.destination?.route in bottomNavRoutes

    Scaffold(
        bottomBar = { if (showBottom) FinanzasBottomNav(navController) }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { fadeIn(fadeSpec) },
            exitTransition   = { fadeOut(fadeSpec) },
        ) {
            composable(Screen.Login.route)         { LoginScreen(navController) }
            composable(Screen.Dashboard.route)     { DashboardScreen() }
            composable(Screen.Transacciones.route) { TransaccionesScreen() }
            composable(Screen.Resumen.route)       { ResumenScreen() }
            composable(Screen.Tarjetas.route)      { TarjetasScreen() }
            composable(Screen.Configuracion.route) { ConfiguracionScreen() }
        }
    }
}
