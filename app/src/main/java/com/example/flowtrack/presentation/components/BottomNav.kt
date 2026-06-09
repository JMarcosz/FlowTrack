package com.example.flowtrack.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.flowtrack.presentation.navigation.Screen

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.Dashboard, "Inicio", Icons.Outlined.Home),
    NavItem(Screen.Transacciones, "Transacciones", Icons.AutoMirrored.Outlined.List),
    NavItem(Screen.Resumen, "Resumen", Icons.Outlined.BarChart),
    NavItem(Screen.Tarjetas, "Tarjetas", Icons.Outlined.CreditCard),
    NavItem(Screen.Configuracion, "Más", Icons.Outlined.Settings),
)

@Composable
fun FinanzasBottomNav(navController: NavController) {
    val backStack = navController.currentBackStackEntryAsState()
    val current = backStack.value?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = current == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(Screen.Dashboard.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}
