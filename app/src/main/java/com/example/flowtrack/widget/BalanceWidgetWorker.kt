package com.example.flowtrack.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoTransaccion
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@HiltWorker
class BalanceWidgetWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transaccionRepository: TransaccionRepository,
    private val auth: FirebaseAuth,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()

        val zona = ZoneId.of("America/Santo_Domingo")
        val ahora = YearMonth.now(zona)
        val inicio = ahora.atDay(1).atStartOfDay(zona).toInstant()
        val fin = ahora.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()
        val periodoStr = "${ahora.month.value}/${ahora.year}"

        val res = transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 0)
        if (res !is AppResult.Success) return Result.retry()

        val txs = res.data.filter { !it.esDerivada }
        val ingresos = txs.filter { it.tipo == TipoTransaccion.CREDITO }
            .fold(java.math.BigDecimal.ZERO) { acc, t -> acc + t.monto }
        val gastos = txs.filter { it.tipo == TipoTransaccion.DEBITO }
            .fold(java.math.BigDecimal.ZERO) { acc, t -> acc + t.monto }

        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(BalanceWidget::class.java)
        ids.forEach { glanceId ->
            updateAppWidgetState(context, widgetStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.INGRESOS] = ingresos.toDouble()
                    this[WidgetKeys.GASTOS]   = gastos.toDouble()
                    this[WidgetKeys.PERIODO]  = periodoStr
                }
            }
            BalanceWidget().update(context, glanceId)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "balance_widget_update"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<BalanceWidgetWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
