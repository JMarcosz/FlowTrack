package com.example.flowtrack.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavHostController
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
import com.example.flowtrack.presentation.screens.exportar.ExportarScreen
import com.example.flowtrack.presentation.screens.conversor.ConversorScreen
import com.example.flowtrack.presentation.screens.notificaciones.NotificacionesScreen
import com.example.flowtrack.presentation.screens.perfil.PerfilScreen
import com.example.flowtrack.presentation.screens.privacidad.PrivacidadSeguridadScreen
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
    object Exportar        : Screen("exportar")
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
    object Privacidad      : Screen("privacidad")
}

private val bottomNavRoutes = setOf(
    Screen.Dashboard.route,
    Screen.Transacciones.route,
    Screen.Resumen.route,
    Screen.Tarjetas.route,
    Screen.Configuracion.route,
)

private val fadeSpec = tween<Float>(durationMillis = 240)
private const val FROM_SIDEBAR_ARG = "fromSidebar"

private fun rutaConOrigen(route: String): String = "$route?$FROM_SIDEBAR_ARG={$FROM_SIDEBAR_ARG}"

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String         = Screen.Login.route,
    initialRoute: String? = null,
) {
    val backStack  = navController.currentBackStackEntryAsState()
    val showBottom = backStack.value?.destination?.route in bottomNavRoutes
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }
    val currentRoute = backStack.value?.destination?.route

    LaunchedEffect(initialRoute) {
        initialRoute?.let { route ->
            if (currentRoute != route) {
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FlowTrackDrawer(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
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
                composable(
                    route = rutaConOrigen(Screen.Exportar.route),
                    arguments = listOf(
                        navArgument(FROM_SIDEBAR_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) { entry ->
                    ExportarScreen(
                        navController,
                        fromSidebar = entry.arguments?.getBoolean(FROM_SIDEBAR_ARG) ?: false,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable(Screen.Upload.route)        { UploadScreen(navController) }
                composable(
                    route = rutaConOrigen(Screen.Historial.route),
                    arguments = listOf(
                        navArgument(FROM_SIDEBAR_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) { entry ->
                    HistorialScreen(
                        navController,
                        fromSidebar = entry.arguments?.getBoolean(FROM_SIDEBAR_ARG) ?: false,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable(Screen.Revision.route)      { RevisionScreen(navController) }
                composable(Screen.Duplicados.route)    { DuplicadosScreen(navController) }
                composable(
                    route = rutaConOrigen(Screen.Conversor.route),
                    arguments = listOf(
                        navArgument(FROM_SIDEBAR_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) { entry ->
                    ConversorScreen(
                        navController,
                        fromSidebar = entry.arguments?.getBoolean(FROM_SIDEBAR_ARG) ?: false,
                        onMenuClick = openDrawer,
                    )
                }
                composable(
                    route = rutaConOrigen(Screen.Sugerencias.route),
                    arguments = listOf(
                        navArgument(FROM_SIDEBAR_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) { entry ->
                    SugerenciasScreen(
                        navController,
                        fromSidebar = entry.arguments?.getBoolean(FROM_SIDEBAR_ARG) ?: false,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable(Screen.Categorias.route)    { CategoriasScreen(navController) }
                composable(
                    route = rutaConOrigen(Screen.BancosYCuentas.route),
                    arguments = listOf(
                        navArgument(FROM_SIDEBAR_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) { entry ->
                    BancosYCuentasScreen(
                        navController,
                        fromSidebar = entry.arguments?.getBoolean(FROM_SIDEBAR_ARG) ?: false,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable(Screen.Notificaciones.route) { NotificacionesScreen(navController) }
                composable(Screen.Perfil.route)         { PerfilScreen(navController) }
                composable(Screen.Reglas.route)         { ReglasScreen(navController) }
                composable(
                    route = rutaConOrigen(Screen.Presupuestos.route),
                    arguments = listOf(
                        navArgument(FROM_SIDEBAR_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) { entry ->
                    PresupuestosScreen(
                        navController,
                        fromSidebar = entry.arguments?.getBoolean(FROM_SIDEBAR_ARG) ?: false,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable(
                    route = rutaConOrigen(Screen.Metas.route),
                    arguments = listOf(
                        navArgument(FROM_SIDEBAR_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) { entry ->
                    MetasScreen(
                        navController,
                        fromSidebar = entry.arguments?.getBoolean(FROM_SIDEBAR_ARG) ?: false,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable(Screen.Privacidad.route)     { PrivacidadSeguridadScreen(navController) }
            }
        }
    }
}
