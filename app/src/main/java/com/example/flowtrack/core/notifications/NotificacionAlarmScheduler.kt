package com.example.flowtrack.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.flowtrack.MainActivity
import com.example.flowtrack.core.workers.NotificacionScheduler
import com.example.flowtrack.data.firestore.repositories.NotificacionConfigRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.domain.model.NotificacionConfig
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.usecase.ObtenerResumenUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificacionAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val tarjetaRepository: TarjetaRepository,
    private val notificacionConfigRepository: NotificacionConfigRepository,
    private val obtenerResumenUseCase: ObtenerResumenUseCase,
) {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    suspend fun sincronizarSesionActiva() {
        val uid = auth.currentUser?.uid ?: return
        sincronizar(uid)
    }

    suspend fun sincronizar(uid: String) {
        val config = notificacionConfigRepository.observar(uid).first()
        if (!config.activa) {
            cancelarExactas(uid, config)
            NotificacionScheduler.cancelarFallback(context)
            return
        }

        if (!puedeProgramarExactas()) {
            NotificacionScheduler.aplicarFallback(context, config)
            return
        }

        NotificacionScheduler.cancelarFallback(context)
        programarRecordatorios(uid, config)
        programarResumenMensual(uid, config)
    }

    suspend fun cancelarExactas(uid: String, config: NotificacionConfig? = null) {
        val effectiveConfig = config ?: notificacionConfigRepository.observar(uid).first()
        val tarjetas = tarjetasDelUsuario(uid)

        tarjetas.forEach { tarjeta ->
            umbralesActivos(effectiveConfig).forEach { umbral ->
                cancelarPendingIntent(
                    crearIntentPago(uid, tarjeta.id, umbral),
                    requestCodePago(tarjeta.id, umbral),
                )
            }
        }

        cancelarPendingIntent(
            crearIntentResumen(uid),
            REQUEST_CODE_RESUMEN,
        )
    }

    suspend fun programarRecordatorios(uid: String, config: NotificacionConfig) {
        if (!config.activa) return
        val tarjetas = tarjetasActivas(uid)
        val ahora = Instant.now()
        val zona = zonaDesde(config)
        tarjetas.forEach { tarjeta ->
            umbralesActivos(config).forEach { umbral ->
                val whenMillis = proximaFechaRecordatorio(tarjeta, umbral, config, ahora, zona)?.toInstant()?.toEpochMilli()
                    ?: return@forEach
                programarExacta(
                    intent = crearIntentPago(uid, tarjeta.id, umbral),
                    requestCode = requestCodePago(tarjeta.id, umbral),
                    whenMillis = whenMillis,
                )
            }
        }
    }

    suspend fun programarResumenMensual(uid: String, config: NotificacionConfig) {
        if (!config.activa || !config.resumenMensual) return
        val ahora = Instant.now()
        val zona = zonaDesde(config)
        val whenMillis = proximaFechaResumen(config, ahora, zona).toInstant().toEpochMilli()
        programarExacta(
            intent = crearIntentResumen(uid),
            requestCode = REQUEST_CODE_RESUMEN,
            whenMillis = whenMillis,
        )
    }

    suspend fun manejarAlarma(intent: Intent) {
        val uid = intent.getStringExtra(NotificationAlarmContract.EXTRA_UID)
            ?: auth.currentUser?.uid
            ?: return
        val currentUid = auth.currentUser?.uid ?: return
        if (currentUid != uid) return

        when (intent.action) {
            NotificationAlarmContract.ACTION_PAGO -> {
                val tarjetaId = intent.getStringExtra(NotificationAlarmContract.EXTRA_TARJETA_ID) ?: return
                val umbral = intent.getIntExtra(NotificationAlarmContract.EXTRA_UMBRAL_DIAS, -1)
                if (umbral < 0) return
                manejarRecordatorioPago(uid, tarjetaId, umbral)
            }

            NotificationAlarmContract.ACTION_RESUMEN -> {
                manejarResumenMensual(uid)
            }
        }

        sincronizar(uid)
    }

    private suspend fun manejarRecordatorioPago(uid: String, tarjetaId: String, umbral: Int) {
        val config = notificacionConfigRepository.observar(uid).first()
        if (!config.activa) return
        if (umbral !in umbralesActivos(config)) return

        val tarjeta = tarjetasActivas(uid).firstOrNull { it.id == tarjetaId } ?: return
        val zona = zonaDesde(config)
        val hoy = LocalDate.now(zona)
        val fechaPago = proximaFechaPago(hoy, tarjeta.diaPago)
        val dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaPago).toInt()
        if (dias != umbral) return

        NotificationHelper.notificar(
            context = context,
            canalId = NotificationHelper.CANAL_PAGOS,
            notifId = requestCodePago(tarjeta.id, umbral),
            titulo = "Recordatorio de pago",
            texto = when (umbral) {
                0 -> "Hoy vence el pago de tu tarjeta ${tarjeta.alias}."
                1 -> "Mañana vence el pago de tu tarjeta ${tarjeta.alias}."
                else -> "Faltan $umbral días para el pago de tu tarjeta ${tarjeta.alias}."
            },
            contentIntent = pendingIntentApp(
                requestCodePago(tarjeta.id, umbral),
                NotificationRoute.ROUTE_TARJETAS,
            ),
        )
    }

    private suspend fun manejarResumenMensual(uid: String) {
        val config = notificacionConfigRepository.observar(uid).first()
        if (!config.activa || !config.resumenMensual) return

        val zona = zonaDesde(config)
        val hoy = LocalDate.now(zona)
        if (hoy.dayOfMonth != 1) return

        val mesAnterior = java.time.YearMonth.now(zona).minusMonths(1)
        val inicio = mesAnterior.atDay(1).atStartOfDay(zona).toInstant()
        val fin = mesAnterior.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()

        val resumen = when (val r = obtenerResumenUseCase.ejecutar(uid, inicio, fin)) {
            is com.example.flowtrack.core.result.AppResult.Success -> r.data
            is com.example.flowtrack.core.result.AppResult.Error -> return
        }

        NotificationHelper.notificar(
            context = context,
            canalId = NotificationHelper.CANAL_RESUMENES,
            notifId = REQUEST_CODE_RESUMEN,
            titulo = "Resumen de ${mesAnterior.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es"))}",
            texto = "Ingresos: ${resumen.ingresosTotales} · Gastos: ${resumen.gastosTotales}",
            contentIntent = pendingIntentApp(REQUEST_CODE_RESUMEN, NotificationRoute.ROUTE_RESUMEN),
        )
    }

    private fun puedeProgramarExactas(): Boolean {
        return alarmManager?.canScheduleExactAlarms() == true
    }

    private fun programarExacta(intent: Intent, requestCode: Int, whenMillis: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        }
    }

    private fun cancelarPendingIntent(intent: Intent, requestCode: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager?.cancel(pi)
    }

    private fun crearIntentPago(uid: String, tarjetaId: String, umbral: Int): Intent =
        Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = NotificationAlarmContract.ACTION_PAGO
            putExtra(NotificationAlarmContract.EXTRA_UID, uid)
            putExtra(NotificationAlarmContract.EXTRA_TARJETA_ID, tarjetaId)
            putExtra(NotificationAlarmContract.EXTRA_UMBRAL_DIAS, umbral)
        }

    private fun crearIntentResumen(uid: String): Intent =
        Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = NotificationAlarmContract.ACTION_RESUMEN
            putExtra(NotificationAlarmContract.EXTRA_UID, uid)
        }

    private fun pendingIntentApp(requestCode: Int, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationRoute.EXTRA_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun tarjetasActivas(uid: String): List<Tarjeta> = when (val r = tarjetaRepository.obtenerTarjetas(uid)) {
        is com.example.flowtrack.core.result.AppResult.Success -> r.data.filter { it.activa }
        is com.example.flowtrack.core.result.AppResult.Error -> emptyList()
    }

    private suspend fun tarjetasDelUsuario(uid: String): List<Tarjeta> = when (val r = tarjetaRepository.obtenerTarjetas(uid)) {
        is com.example.flowtrack.core.result.AppResult.Success -> r.data
        is com.example.flowtrack.core.result.AppResult.Error -> emptyList()
    }

    private fun proximaFechaRecordatorio(
        tarjeta: Tarjeta,
        umbral: Int,
        config: NotificacionConfig,
        now: Instant,
        zona: ZoneId,
    ): ZonedDateTime? {
        var corte = proximaFechaPago(LocalDate.now(zona), tarjeta.diaPago)
        var candidate = corte.minusDays(umbral.toLong()).atTime(config.horaNotificacion).atZone(zona)
        while (candidate.toInstant().isBefore(now)) {
            corte = corte.plusMonths(1)
            candidate = corte.minusDays(umbral.toLong()).atTime(config.horaNotificacion).atZone(zona)
        }
        return candidate
    }

    private fun proximaFechaResumen(config: NotificacionConfig, now: Instant, zona: ZoneId): ZonedDateTime {
        var candidate = java.time.YearMonth.now(zona).atDay(1).atTime(config.horaNotificacion).atZone(zona)
        while (candidate.toInstant().isBefore(now)) {
            candidate = candidate.plusMonths(1).withDayOfMonth(1)
        }
        return candidate
    }

    private fun proximaFechaPago(hoy: LocalDate, diaPago: Int): LocalDate {
        fun enMes(ym: java.time.YearMonth): LocalDate = ym.atDay(diaPago.coerceIn(1, ym.lengthOfMonth()))
        val esteMes = enMes(java.time.YearMonth.from(hoy))
        return if (!esteMes.isBefore(hoy)) esteMes else enMes(java.time.YearMonth.from(hoy).plusMonths(1))
    }

    private fun umbralesActivos(config: NotificacionConfig): List<Int> = buildList {
        if (config.pago7dias) add(7)
        if (config.pago3dias) add(3)
        if (config.pago1dia) add(1)
        if (config.pagoMismoDia) add(0)
    }

    private fun zonaDesde(config: NotificacionConfig): ZoneId =
        runCatching { ZoneId.of(config.zonaHoraria) }.getOrDefault(ZoneId.of("America/Santo_Domingo"))

    companion object {
        private const val REQUEST_CODE_RESUMEN = 90_001
        fun requestCodePago(tarjetaId: String, umbral: Int): Int =
            100_000 + (tarjetaId.hashCode().and(0x7FFF)) * 10 + umbral
    }
}
