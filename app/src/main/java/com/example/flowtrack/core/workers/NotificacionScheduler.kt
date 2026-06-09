package com.example.flowtrack.core.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.flowtrack.domain.model.NotificacionConfig
import java.util.concurrent.TimeUnit

/**
 * Programa o cancela los workers de notificación según [NotificacionConfig].
 * Ambos workers corren a diario; cada uno decide internamente si hay algo que notificar.
 */
object NotificacionScheduler {

    private const val WORK_RECORDATORIO_PAGO = "recordatorio_pago"
    private const val WORK_RESUMEN_MENSUAL = "resumen_mensual"

    fun aplicar(context: Context, config: NotificacionConfig) {
        val wm = WorkManager.getInstance(context)

        val recordatoriosActivos = config.activa &&
            (config.pago7dias || config.pago3dias || config.pago1dia || config.pagoMismoDia)
        if (recordatoriosActivos) {
            wm.enqueueUniquePeriodicWork(
                WORK_RECORDATORIO_PAGO,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<RecordatorioPagoWorker>(1, TimeUnit.DAYS).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_RECORDATORIO_PAGO)
        }

        if (config.activa && config.resumenMensual) {
            wm.enqueueUniquePeriodicWork(
                WORK_RESUMEN_MENSUAL,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ResumenMensualWorker>(1, TimeUnit.DAYS).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_RESUMEN_MENSUAL)
        }
    }

    /** Dispara una verificación inmediata de recordatorios (para probar en el dispositivo). */
    fun dispararPruebaInmediata(context: Context) {
        WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<RecordatorioPagoWorker>().build()
        )
    }
}
