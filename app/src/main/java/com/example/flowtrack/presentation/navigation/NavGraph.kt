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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
import com.example.flowtrack.presentation.screens.revision.RevisionScreen
import com.example.flowtrack.presentation.screens.sugerencias.SugerenciasScreen
import com.example.flowtrack.presentation.screens.tarjetas.TarjetasScreen
import com.example.flowtrack.presentation.screens.transacciones.TransaccionesScreen
import com.example.flowtrack.presentation.screens.metas.MetasScreen
import com.example.flowtrack.presentation.screens.presupuestos.PresupuestosScreen
import com.example.flowtrack.presentation.screens.upload.UploadScreen

private val fadeSpec = tween<Float>(durationMillis = 240)

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: Any             = LoginRoute,
    initialRoute: Any? = null,
) {
    val backStack  = navController.currentBackStackEntryAsState()
    val destination = backStack.value?.destination
    val showBottom = destination?.hasRoute<DashboardRoute>() == true ||
        destination?.hasRoute<TransaccionesRoute>() == true ||
        destination?.hasRoute<ResumenRoute>() == true ||
        destination?.hasRoute<TarjetasRoute>() == true ||
        destination?.hasRoute<ConfiguracionRoute>() == true
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }
    LaunchedEffect(initialRoute) {
        initialRoute?.let { route ->
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FlowTrackDrawer(
                currentDestination = destination,
                onNavigateToMetas = {
                    scope.launch { drawerState.close() }
                    navController.navigate(MetasRoute(fromSidebar = true))
                },
                onNavigateToPresupuestos = {
                    scope.launch { drawerState.close() }
                    navController.navigate(PresupuestosRoute(fromSidebar = true))
                },
                onNavigateToBancosYCuentas = {
                    scope.launch { drawerState.close() }
                    navController.navigate(BancosYCuentasRoute(fromSidebar = true))
                },
                onNavigateToHistorial = {
                    scope.launch { drawerState.close() }
                    navController.navigate(HistorialRoute(fromSidebar = true))
                },
                onNavigateToExportar = {
                    scope.launch { drawerState.close() }
                    navController.navigate(ExportarRoute(fromSidebar = true))
                },
                onNavigateToSugerencias = {
                    scope.launch { drawerState.close() }
                    navController.navigate(SugerenciasRoute(fromSidebar = true))
                },
                onNavigateToConversor = {
                    scope.launch { drawerState.close() }
                    navController.navigate(ConversorRoute(fromSidebar = true))
                },
            )
        },
    ) {
        Scaffold(
            bottomBar = {
                if (showBottom) {
                    FinanzasBottomNav(
                        currentDestination = destination,
                        onNavigateToDashboard = {
                            navController.navigate(DashboardRoute) {
                                popUpTo(LoginRoute) { inclusive = false }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToTransacciones = {
                            navController.navigate(TransaccionesRoute) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToResumen = {
                            navController.navigate(ResumenRoute) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToTarjetas = {
                            navController.navigate(TarjetasRoute) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToConfiguracion = {
                            navController.navigate(ConfiguracionRoute) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
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
                composable<LoginRoute> {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(DashboardRoute) {
                                popUpTo(LoginRoute) { inclusive = true }
                            }
                        },
                    )
                }
                composable<DashboardRoute> {
                    DashboardScreen(
                        onMenuClick = openDrawer,
                        onNavigateToUpload = { navController.navigate(UploadRoute) },
                        onNavigateToNotificaciones = { navController.navigate(NotificacionesRoute) },
                        onNavigateToResumen = { navController.navigate(ResumenRoute) },
                    )
                }
                composable<TransaccionesRoute> {
                    TransaccionesScreen(
                        onNavigateToUpload = { navController.navigate(UploadRoute) },
                        onMenuClick = openDrawer,
                    )
                }
                composable<ResumenRoute> {
                    ResumenScreen(
                        onMenuClick = openDrawer,
                    )
                }
                composable<TarjetasRoute> { TarjetasScreen(onMenuClick = openDrawer) }
                composable<ConfiguracionRoute> {
                    ConfiguracionScreen(
                        onMenuClick = openDrawer,
                        onNavigateToPerfil = { navController.navigate(PerfilRoute) },
                        onNavigateToNotificaciones = { navController.navigate(NotificacionesRoute) },
                        onNavigateToCategorias = { navController.navigate(CategoriasRoute) },
                        onNavigateToReglas = { navController.navigate(ReglasRoute) },
                        onNavigateToHistorial = { navController.navigate(HistorialRoute()) },
                        onNavigateToSugerencias = { navController.navigate(SugerenciasRoute()) },
                        onNavigateToExportar = { navController.navigate(ExportarRoute()) },
                        onNavigateToPrivacidad = { navController.navigate(PrivacidadRoute) },
                        onNavigateToLogin = { navController.navigate(LoginRoute) { popUpTo(0) { inclusive = true } } },
                    )
                }
                composable<ExportarRoute> { entry ->
                    val route = entry.toRoute<ExportarRoute>()
                    ExportarScreen(
                        onNavigateBack = { navController.popBackStack() },
                        fromSidebar = route.fromSidebar,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable<UploadRoute>        {
                    UploadScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToHistorial = { navController.navigate(HistorialRoute()) },
                        onNavigateToDashboard = {
                            navController.navigate(DashboardRoute) {
                                popUpTo(DashboardRoute) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable<HistorialRoute> { entry ->
                    val route = entry.toRoute<HistorialRoute>()
                    HistorialScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToUpload = { navController.navigate(UploadRoute) },
                        onDrawerReopen = openDrawer,
                        fromSidebar = route.fromSidebar,
                    )
                }
                composable<RevisionRoute>      {
                    RevisionScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDuplicados = { navController.navigate(DuplicadosRoute) },
                        onNavigateToHistorial = {
                            navController.navigate(HistorialRoute())
                        },
                    )
                }
                composable<DuplicadosRoute>    { DuplicadosScreen(onNavigateBack = { navController.popBackStack() }) }
                composable<ConversorRoute> { entry ->
                    val route = entry.toRoute<ConversorRoute>()
                    ConversorScreen(
                        onNavigateBack = { navController.popBackStack() },
                        fromSidebar = route.fromSidebar,
                        onMenuClick = openDrawer,
                    )
                }
                composable<SugerenciasRoute> { entry ->
                    val route = entry.toRoute<SugerenciasRoute>()
                    SugerenciasScreen(
                        onNavigateBack = { navController.popBackStack() },
                        fromSidebar = route.fromSidebar,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable<CategoriasRoute>    { CategoriasScreen(onNavigateBack = { navController.popBackStack() }) }
                composable<BancosYCuentasRoute> { entry ->
                    val route = entry.toRoute<BancosYCuentasRoute>()
                    BancosYCuentasScreen(
                        onNavigateBack = { navController.popBackStack() },
                        fromSidebar = route.fromSidebar,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable<NotificacionesRoute> { NotificacionesScreen(onNavigateBack = { navController.popBackStack() }) }
                composable<PerfilRoute>         { PerfilScreen(onNavigateBack = { navController.popBackStack() }, onNavigateToLogin = { navController.navigate(LoginRoute) { popUpTo(0) { inclusive = true } } }) }
                composable<ReglasRoute>         { ReglasScreen(onNavigateBack = { navController.popBackStack() }) }
                composable<PresupuestosRoute> { entry ->
                    val route = entry.toRoute<PresupuestosRoute>()
                    PresupuestosScreen(
                        onNavigateBack = { navController.popBackStack() },
                        fromSidebar = route.fromSidebar,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable<MetasRoute> { entry ->
                    val route = entry.toRoute<MetasRoute>()
                    MetasScreen(
                        onNavigateBack = { navController.popBackStack() },
                        fromSidebar = route.fromSidebar,
                        onDrawerReopen = openDrawer,
                    )
                }
                composable<PrivacidadRoute>     {
                    PrivacidadSeguridadScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToExportar = { navController.navigate(ExportarRoute()) },
                    )
                }
            }
        }
    }
}
