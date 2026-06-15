package com.example.flowtrack.core.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.TasaCambio
import kotlinx.coroutines.runBlocking
import org.mockito.Answers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class TasaCambioWorkerTest {

    @Test
    fun `doWork sincroniza tasa actual y historico`() = runBlocking {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        val params = mock<WorkerParameters>()
        val taskExecutor = mock<TaskExecutor>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(params.taskExecutor).thenReturn(taskExecutor)
        val repository = mock<TasaCambioRepository>()

        val tasa = TasaCambio(
            compra = BigDecimal("58.50"),
            venta = BigDecimal("59.10"),
            fecha = LocalDate.of(2026, 1, 1),
            fuente = "Mock",
        )

        repository.stub {
            onBlocking { obtenerTasaDelDia() } doReturn AppResult.Success(tasa)
            onBlocking { obtenerHistorico(30) } doReturn AppResult.Success(listOf(tasa))
        }

        val worker = TasaCambioWorker(context, params, repository)

        assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork())
    }
}
