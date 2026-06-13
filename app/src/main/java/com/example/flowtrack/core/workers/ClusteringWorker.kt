package com.example.flowtrack.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.usecase.AnalizarTransaccionesUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
}
