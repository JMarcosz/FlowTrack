package com.example.flowtrack.presentation.navigation

import com.example.flowtrack.core.notifications.NotificationRoute

fun notificationRouteFromLegacyString(route: String?): Any? {
    return when (route) {
        NotificationRoute.ROUTE_NOTIFICACIONES -> NotificacionesRoute
        NotificationRoute.ROUTE_TRANSACCIONES -> TransaccionesRoute
        NotificationRoute.ROUTE_RESUMEN -> ResumenRoute
        NotificationRoute.ROUTE_TARJETAS -> TarjetasRoute
        NotificationRoute.ROUTE_DASHBOARD -> DashboardRoute
        NotificationRoute.ROUTE_HISTORIAL -> HistorialRoute()
        NotificationRoute.ROUTE_PRESUPUESTOS -> PresupuestosRoute()
        NotificationRoute.ROUTE_METAS -> MetasRoute()
        NotificationRoute.ROUTE_EXPORTAR -> ExportarRoute()
        else -> null
    }
}
