package com.example.flowtrack.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.usecase.AnalizarTransaccionesUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ClusteringWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analizarTransaccionesUseCase: AnalizarTransaccionesUseCase,
    private val auth: FirebaseAuth
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.failure()

        return when (val result = analizarTransaccionesUseCase.ejecutar(uid)) {
            is AppResult.Success -> {
                // Si retorna > 0, significa que encontró y guardó reglas.
                Result.success()
            }
            is AppResult.Error -> {
                Result.retry()
            }
        }
    }

    companion object {
        private const val WORK_NAME_PERIODICO = "clustering_periodic"
        private const val WORK_NAME_IMPORTACION = "clustering_after_import"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClusteringWorker>(
                repeatInterval = 7,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODICO,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueAfterImport(context: Context) {
            val request = OneTimeWorkRequestBuilder<ClusteringWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMPORTACION,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
