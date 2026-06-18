package com.example.flowtrack.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object NotificacionesRoute

@Serializable
data object LoginRoute

@Serializable
data object DashboardRoute

@Serializable
data object TransaccionesRoute

@Serializable
data object ResumenRoute

@Serializable
data object TarjetasRoute

@Serializable
data object ConfiguracionRoute

@Serializable
data object DuplicadosRoute

@Serializable
data object UploadRoute

@Serializable
data object RevisionRoute

@Serializable
data object PerfilRoute

@Serializable
data object CategoriasRoute

@Serializable
data object ReglasRoute

@Serializable
data object PrivacidadRoute

@Serializable
data class ExportarRoute(
    val fromSidebar: Boolean = false,
)

@Serializable
data class HistorialRoute(
    val fromSidebar: Boolean = false,
)

@Serializable
data class ConversorRoute(
    val fromSidebar: Boolean = false,
)

@Serializable
data class SugerenciasRoute(
    val fromSidebar: Boolean = false,
)

@Serializable
data class BancosYCuentasRoute(
    val fromSidebar: Boolean = false,
)

@Serializable
data class PresupuestosRoute(
    val fromSidebar: Boolean = false,
)

@Serializable
data class MetasRoute(
    val fromSidebar: Boolean = false,
)
