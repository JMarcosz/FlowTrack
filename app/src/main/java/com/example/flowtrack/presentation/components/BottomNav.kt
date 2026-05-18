package com.example.flowtrack.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.flowtrack.presentation.navigation.Screen

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.Dashboard,     "Inicio",         Icons.Outlined.Home),
    NavItem(Screen.Transacciones, "Transacciones",  Icons.Outlined.List),
    NavItem(Screen.Resumen,       "Resumen",        Icons.Outlined.BarChart),
    NavItem(Screen.Tarjetas,      "Tarjetas",       Icons.Outlined.CreditCard),
    NavItem(Screen.Configuracion, "Más",            Icons.Outlined.Settings),
)

@Composable
fun FinanzasBottomNav(navController: NavController) {
    val backStack = navController.currentBackStackEntryAsState()
    val current   = backStack.value?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = current == item.screen.route,
                onClick  = {
                    if (item.screen == Screen.Dashboard) {
                        // Mismo patrón que el login: navegar a Dashboard
                        // y limpiar todo lo que esté encima de él.
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    } else {
                        // Para el resto de tabs: limpiar la pila hasta Dashboard
                        // (sin saveState/restoreState para evitar conflictos de estado)
                        // y navegar al destino.
                        navController.navigate(item.screen.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                icon  = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}
