package com.example.flowtrack.core.notifications

sealed interface NotificationDeliveryResult {
    data object Enviada : NotificationDeliveryResult
    data object SinPermiso : NotificationDeliveryResult
    data object CanalBloqueado : NotificationDeliveryResult
    data class Error(val mensaje: String) : NotificationDeliveryResult
}
