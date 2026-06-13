package com.example.flowtrack.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.flowtrack.R
import androidx.core.net.toUri

/**
 * Centraliza la creación de canales y el envío de notificaciones locales.
 */
object NotificationHelper {

    const val CANAL_PAGOS = "recordatorios_pago"
    const val CANAL_RESUMENES = "resumenes"
    const val CANAL_ALERTAS = "alertas"
    const val CANAL_PUSH = "push"

    /** Crea los canales de notificación (idempotente). Se llama en Application.onCreate. */
    fun crearCanales(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val canales = listOf(
            NotificationChannel(CANAL_PAGOS, "Recordatorios de pago", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Avisos antes de la fecha límite de pago de tus tarjetas" },
            NotificationChannel(CANAL_RESUMENES, "Resúmenes", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Resumen mensual de tus finanzas" },
            NotificationChannel(CANAL_ALERTAS, "Alertas de gasto", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alertas cuando un gasto supera tu umbral" },
            NotificationChannel(CANAL_PUSH, "Notificaciones en tiempo real", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Eventos sincronizados desde Firebase" },
        )
        manager.createNotificationChannels(canales)
    }

    /** True si la app puede publicar notificaciones (permiso runtime en Android 13+). */
    fun puedeNotificar(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canalHabilitado(context: Context, canalId: String): Boolean {
        if (!puedeNotificar(context)) return false

        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(canalId)
            ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Publica una notificación local.
     */
    fun notificar(
        context: Context,
        canalId: String,
        notifId: Int,
        titulo: String,
        texto: String,
        contentIntent: android.app.PendingIntent? = null,
    ): NotificationDeliveryResult {
        if (!puedeNotificar(context)) return NotificationDeliveryResult.SinPermiso
        if (!canalHabilitado(context, canalId)) return NotificationDeliveryResult.CanalBloqueado

        val builder = NotificationCompat.Builder(context, canalId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        return try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
            NotificationDeliveryResult.Enviada
        } catch (e: SecurityException) {
            NotificationDeliveryResult.Error(e.message ?: "SecurityException")
        } catch (e: Exception) {
            NotificationDeliveryResult.Error(e.message ?: "Error publicando notificación")
        }
    }

    fun intentAjustesApp(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun intentAjustesAlarmasExactas(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
