package com.example.flowtrack.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.flowtrack.R

/**
 * Centraliza la creación de canales y el envío de notificaciones locales.
 * Las notificaciones se disparan desde los Workers (WorkManager).
 */
object NotificationHelper {

    const val CANAL_PAGOS = "recordatorios_pago"
    const val CANAL_RESUMENES = "resumenes"
    const val CANAL_ALERTAS = "alertas"

    /** Crea los canales de notificación (idempotente). Se llama en Application.onCreate. */
    fun crearCanales(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val canales = listOf(
            NotificationChannel(CANAL_PAGOS, "Recordatorios de pago", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Avisos antes de la fecha límite de pago de tus tarjetas" },
            NotificationChannel(CANAL_RESUMENES, "Resúmenes", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Resumen mensual de tus finanzas" },
            NotificationChannel(CANAL_ALERTAS, "Alertas de gasto", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Alertas cuando un gasto supera tu umbral" },
        )
        manager.createNotificationChannels(canales)
    }

    /** True si la app puede publicar notificaciones (permiso runtime en Android 13+). */
    fun puedeNotificar(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Publica una notificación. No-op silencioso si falta el permiso. */
    fun notificar(context: Context, canalId: String, notifId: Int, titulo: String, texto: String) {
        if (!puedeNotificar(context)) return
        val notif = NotificationCompat.Builder(context, canalId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
        } catch (_: SecurityException) {
            // El permiso pudo revocarse entre el check y el notify; ignorar.
        }
    }
}
