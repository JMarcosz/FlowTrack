package com.example.flowtrack.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import com.example.flowtrack.presentation.components.FinanzasBottomNav
import com.example.flowtrack.presentation.components.FlowTrackDrawer
import com.example.flowtrack.presentation.screens.categorias.CategoriasScreen
import com.example.flowtrack.presentation.screens.bancos.BancosYCuentasScreen
import com.example.flowtrack.presentation.screens.reglas.ReglasScreen
import com.example.flowtrack.presentation.screens.configuracion.ConfiguracionScreen
import com.example.flowtrack.presentation.screens.conversor.ConversorScreen
import com.example.flowtrack.presentation.screens.notificaciones.NotificacionesScreen
import com.example.flowtrack.presentation.screens.perfil.PerfilScreen
import com.example.flowtrack.presentation.screens.dashboard.DashboardScreen
import com.example.flowtrack.presentation.screens.duplicados.DuplicadosScreen
import com.example.flowtrack.presentation.screens.historial.HistorialScreen
import com.example.flowtrack.presentation.screens.login.LoginScreen
import com.example.flowtrack.presentation.screens.resumen.ResumenScreen
import com.example.flowtrack.presentation.screens.resumen.ResumenPeriodoScreen
import com.example.flowtrack.presentation.screens.revision.RevisionScreen
import com.example.flowtrack.presentation.screens.sugerencias.SugerenciasScreen
import com.example.flowtrack.presentation.screens.tarjetas.TarjetasScreen
import com.example.flowtrack.presentation.screens.transacciones.TransaccionesScreen
import com.example.flowtrack.presentation.screens.metas.MetasScreen
import com.example.flowtrack.presentation.screens.presupuestos.PresupuestosScreen
import com.example.flowtrack.presentation.screens.upload.UploadScreen

sealed class Screen(val route: String) {
    object Login           : Screen("login")
    object Dashboard       : Screen("dashboard")
    object Transacciones   : Screen("transacciones")
    object Resumen         : Screen("resumen")
    object ResumenPeriodo  : Screen("resumen_periodo")
    object Tarjetas        : Screen("tarjetas")
    object Configuracion   : Screen("configuracion")
    object Upload          : Screen("upload")
    object Historial       : Screen("historial")
    object Revision        : Screen("revision")
    object Duplicados      : Screen("duplicados")
    object Conversor       : Screen("conversor")
    object Sugerencias     : Screen("sugerencias")
    object Categorias      : Screen("categorias")
    object BancosYCuentas  : Screen("bancos_y_cuentas")
    object Notificaciones  : Screen("notificaciones")
    object Perfil          : Screen("perfil")
    object Reglas          : Screen("reglas")
    object Presupuestos    : Screen("presupuestos")
    object Metas           : Screen("metas")
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
    val backStack  = navController.currentBackStackEntryAsState()
    val showBottom = backStack.value?.destination?.route in bottomNavRoutes
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }
    val currentRoute = backStack.value?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FlowTrackDrawer(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) {
        Scaffold(
            bottomBar = { if (showBottom) FinanzasBottomNav(navController) }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = startDestination,
                modifier         = Modifier.padding(innerPadding),
                enterTransition  = { fadeIn(fadeSpec) + scaleIn(initialScale = 0.96f, animationSpec = fadeSpec) },
                exitTransition   = { fadeOut(fadeSpec) + scaleOut(targetScale = 1.04f, animationSpec = fadeSpec) },
                popEnterTransition = { fadeIn(fadeSpec) + scaleIn(initialScale = 1.04f, animationSpec = fadeSpec) },
                popExitTransition = { fadeOut(fadeSpec) + scaleOut(targetScale = 0.96f, animationSpec = fadeSpec) },
            ) {
                composable(Screen.Login.route)         { LoginScreen(navController) }
                composable(Screen.Dashboard.route)     { DashboardScreen(navController, onMenuClick = openDrawer) }
                composable(Screen.Transacciones.route) { TransaccionesScreen(navController, onMenuClick = openDrawer) }
                composable(Screen.Resumen.route)       {
                    ResumenScreen(
                        onVerPorPeriodo = { navController.navigate(Screen.ResumenPeriodo.route) },
                        onMenuClick = openDrawer,
                    )
                }
                composable(Screen.ResumenPeriodo.route) { ResumenPeriodoScreen(navController) }
                composable(Screen.Tarjetas.route)      { TarjetasScreen(onMenuClick = openDrawer) }
                composable(Screen.Configuracion.route) { ConfiguracionScreen(navController, onMenuClick = openDrawer) }
                composable(Screen.Upload.route)        { UploadScreen(navController) }
                composable(Screen.Historial.route)     { HistorialScreen(navController) }
                composable(Screen.Revision.route)      { RevisionScreen(navController) }
                composable(Screen.Duplicados.route)    { DuplicadosScreen(navController) }
                composable(Screen.Conversor.route)     { ConversorScreen() }
                composable(Screen.Sugerencias.route)   { SugerenciasScreen() }
                composable(Screen.Categorias.route)    { CategoriasScreen(navController) }
                composable(Screen.BancosYCuentas.route) { BancosYCuentasScreen(navController) }
                composable(Screen.Notificaciones.route) { NotificacionesScreen(navController) }
                composable(Screen.Perfil.route)         { PerfilScreen(navController) }
                composable(Screen.Reglas.route)         { ReglasScreen(navController) }
                composable(Screen.Presupuestos.route)   { PresupuestosScreen(navController) }
                composable(Screen.Metas.route)          { MetasScreen(navController) }
            }
        }
    }
}
