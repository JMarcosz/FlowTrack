package com.example.flowtrack.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.flowtrack.core.notifications.NotificationHelper
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.NotificacionConfigRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.domain.model.NotificacionConfig
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Worker diario que revisa la próxima fecha de pago de cada tarjeta (según `Tarjeta.diaPago`)
 * y dispara recordatorios 7/3/1/mismo-día según los toggles de [NotificacionConfig].
 */
@HiltWorker
class RecordatorioPagoWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val tarjetaRepository: TarjetaRepository,
    private val notificacionConfigRepository: NotificacionConfigRepository,
    private val auth: FirebaseAuth,
) : CoroutineWorker(appContext, params) {

    private val zona = ZoneId.of("America/Santo_Domingo")

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()
        val config = notificacionConfigRepository.observar(uid).first()
        if (!config.activa) return Result.success()

        val tarjetas = when (val r = tarjetaRepository.obtenerTarjetas(uid)) {
            is AppResult.Success -> r.data.filter { it.activa }
            is AppResult.Error -> return Result.retry()
        }

        val hoy = LocalDate.now(zona)
        tarjetas.forEach { tarjeta ->
            val fechaPago = proximaFechaPago(hoy, tarjeta.diaPago)
            val dias = ChronoUnit.DAYS.between(hoy, fechaPago).toInt()
            val umbral = umbralAplicable(dias, config) ?: return@forEach

            val texto = when (umbral) {
                0 -> "Hoy vence el pago de tu tarjeta ${tarjeta.alias}."
                1 -> "Mañana vence el pago de tu tarjeta ${tarjeta.alias}."
                else -> "Faltan $umbral días para el pago de tu tarjeta ${tarjeta.alias}."
            }
            NotificationHelper.notificar(
                context = applicationContext,
                canalId = NotificationHelper.CANAL_PAGOS,
                notifId = (tarjeta.id.hashCode() and 0x7FFFFFFF) + umbral,
                titulo = "Recordatorio de pago",
                texto = texto,
            )
        }
        return Result.success()
    }

    /** Devuelve el umbral (7/3/1/0) que aplica para [dias] si su toggle está activo; null si ninguno. */
    private fun umbralAplicable(dias: Int, config: NotificacionConfig): Int? = when (dias) {
        7 -> if (config.pago7dias) 7 else null
        3 -> if (config.pago3dias) 3 else null
        1 -> if (config.pago1dia) 1 else null
        0 -> if (config.pagoMismoDia) 0 else null
        else -> null
    }

    /** Próxima ocurrencia del día de pago, clamp a la longitud del mes. */
    private fun proximaFechaPago(hoy: LocalDate, diaPago: Int): LocalDate {
        fun enMes(ym: YearMonth): LocalDate = ym.atDay(diaPago.coerceIn(1, ym.lengthOfMonth()))
        val esteMes = enMes(YearMonth.from(hoy))
        return if (!esteMes.isBefore(hoy)) esteMes else enMes(YearMonth.from(hoy).plusMonths(1))
    }
}
