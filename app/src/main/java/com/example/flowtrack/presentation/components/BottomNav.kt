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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.flowtrack.R
import com.example.flowtrack.presentation.navigation.ConfiguracionRoute
import com.example.flowtrack.presentation.navigation.DashboardRoute
import com.example.flowtrack.presentation.navigation.ResumenRoute
import com.example.flowtrack.presentation.navigation.TarjetasRoute
import com.example.flowtrack.presentation.navigation.TransaccionesRoute

private data class NavItem(
    val labelResId: Int,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun FinanzasBottomNav(
    currentDestination: NavDestination?,
    onNavigateToDashboard: () -> Unit,
    onNavigateToTransacciones: () -> Unit,
    onNavigateToResumen: () -> Unit,
    onNavigateToTarjetas: () -> Unit,
    onNavigateToConfiguracion: () -> Unit,
) {
    val navItems = listOf(
        NavItem(
            labelResId = R.string.bottom_nav_inicio,
            icon = Icons.Outlined.Home,
            selected = currentDestination?.hasRoute<DashboardRoute>() == true,
            onClick = onNavigateToDashboard,
        ),
        NavItem(
            labelResId = R.string.bottom_nav_transacciones,
            icon = Icons.AutoMirrored.Outlined.List,
            selected = currentDestination?.hasRoute<TransaccionesRoute>() == true,
            onClick = onNavigateToTransacciones,
        ),
        NavItem(
            labelResId = R.string.bottom_nav_resumen,
            icon = Icons.Outlined.BarChart,
            selected = currentDestination?.hasRoute<ResumenRoute>() == true,
            onClick = onNavigateToResumen,
        ),
        NavItem(
            labelResId = R.string.bottom_nav_tarjetas,
            icon = Icons.Outlined.CreditCard,
            selected = currentDestination?.hasRoute<TarjetasRoute>() == true,
            onClick = onNavigateToTarjetas,
        ),
        NavItem(
            labelResId = R.string.bottom_nav_mas,
            icon = Icons.Outlined.Settings,
            selected = currentDestination?.hasRoute<ConfiguracionRoute>() == true,
            onClick = onNavigateToConfiguracion,
        ),
    )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        navItems.forEach { item ->
            val label = stringResource(item.labelResId)
            NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = { Icon(item.icon, contentDescription = label) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}
