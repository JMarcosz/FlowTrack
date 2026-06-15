package com.example.flowtrack.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TasaCambioWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tasaCambioRepository: TasaCambioRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val actual = tasaCambioRepository.obtenerTasaDelDia()) {
            is AppResult.Success -> {
                val historico = tasaCambioRepository.obtenerHistorico(30)
                if (historico is AppResult.Error) Result.retry() else Result.success()
            }
            is AppResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "tasa_cambio_sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<TasaCambioWorker>(
                repeatInterval = 24,
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
