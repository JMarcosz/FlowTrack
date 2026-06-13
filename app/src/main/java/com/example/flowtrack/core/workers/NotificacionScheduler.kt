package com.example.flowtrack.core.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.flowtrack.core.notifications.NotificationHelper
import com.example.flowtrack.domain.model.NotificacionConfig
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Programa o cancela los workers de notificación cuando no están disponibles
 * las alarmas exactas o como respaldo operativo.
 */
object NotificacionScheduler {

    private const val WORK_RECORDATORIO_PAGO = "recordatorio_pago"
    private const val WORK_RESUMEN_MENSUAL = "resumen_mensual"

    fun aplicarFallback(context: Context, config: NotificacionConfig) {
        val wm = WorkManager.getInstance(context)

        val recordatoriosActivos = config.activa &&
            (config.pago7dias || config.pago3dias || config.pago1dia || config.pagoMismoDia)
        if (recordatoriosActivos) {
            wm.enqueueUniquePeriodicWork(
                WORK_RECORDATORIO_PAGO,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<RecordatorioPagoWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayHastaSiguienteEjecucion(config, soloDiaUno = false), TimeUnit.MILLISECONDS)
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_RECORDATORIO_PAGO)
        }

        if (config.activa && config.resumenMensual) {
            wm.enqueueUniquePeriodicWork(
                WORK_RESUMEN_MENSUAL,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ResumenMensualWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayHastaSiguienteEjecucion(config, soloDiaUno = true), TimeUnit.MILLISECONDS)
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_RESUMEN_MENSUAL)
        }
    }

    fun cancelarFallback(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_RECORDATORIO_PAGO)
        wm.cancelUniqueWork(WORK_RESUMEN_MENSUAL)
    }

    /** Dispara una notificación sintética inmediata para verificar permisos y canales. */
    fun dispararPruebaInmediata(context: Context) {
        NotificationHelper.notificar(
            context = context,
            canalId = NotificationHelper.CANAL_ALERTAS,
            notifId = 9_001,
            titulo = "Prueba de notificación",
            texto = "FlowTrack puede mostrar notificaciones locales correctamente.",
        )
    }

    private fun delayHastaSiguienteEjecucion(config: NotificacionConfig, soloDiaUno: Boolean): Long {
        val zona = runCatching { ZoneId.of(config.zonaHoraria) }.getOrDefault(ZoneId.of("America/Santo_Domingo"))
        val ahora = Instant.now()
        var candidate = ZonedDateTime.now(zona)
            .withHour(config.horaNotificacion.hour)
            .withMinute(config.horaNotificacion.minute)
            .withSecond(0)
            .withNano(0)
            .let { base ->
                if (soloDiaUno) base.withDayOfMonth(1) else base
            }
        while (candidate.toInstant().isBefore(ahora)) {
            candidate = if (soloDiaUno) candidate.plusMonths(1).withDayOfMonth(1) else candidate.plusDays(1)
        }
        return java.time.Duration.between(ahora, candidate.toInstant()).toMillis().coerceAtLeast(0L)
    }
}
