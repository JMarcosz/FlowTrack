package com.example.flowtrack.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.core.notifications.NotificationHelper
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.NotificacionConfigRepository
import com.example.flowtrack.domain.usecase.ObtenerResumenUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Worker diario que, el día 1 de cada mes, publica un resumen del mes anterior
 * (ingresos/gastos) si el toggle `resumenMensual` está activo.
 */
@HiltWorker
class ResumenMensualWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val obtenerResumenUseCase: ObtenerResumenUseCase,
    private val notificacionConfigRepository: NotificacionConfigRepository,
    private val auth: FirebaseAuth,
) : CoroutineWorker(appContext, params) {

    private val zona = ZoneId.of("America/Santo_Domingo")

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()
        // Solo actúa el primer día del mes.
        if (LocalDate.now(zona).dayOfMonth != 1) return Result.success()

        val config = notificacionConfigRepository.observar(uid).first()
        if (!config.activa || !config.resumenMensual) return Result.success()

        val mesAnterior = YearMonth.now(zona).minusMonths(1)
        val inicio = mesAnterior.atDay(1).atStartOfDay(zona).toInstant()
        val fin = mesAnterior.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()

        val resumen = when (val r = obtenerResumenUseCase.ejecutar(uid, inicio, fin)) {
            is AppResult.Success -> r.data
            is AppResult.Error -> return Result.retry()
        }

        NotificationHelper.notificar(
            context = applicationContext,
            canalId = NotificationHelper.CANAL_RESUMENES,
            notifId = 90_000 + mesAnterior.monthValue,
            titulo = "Resumen de ${mesAnterior.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es"))}",
            texto = "Ingresos: ${formatMoney(resumen.ingresosTotales)} · Gastos: ${formatMoney(resumen.gastosTotales)}",
        )
        return Result.success()
    }
}
