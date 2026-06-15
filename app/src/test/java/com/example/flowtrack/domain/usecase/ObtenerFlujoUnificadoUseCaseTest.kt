package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class ObtenerFlujoUnificadoUseCaseTest {

    private val transaccionRepository: TransaccionRepository = mock()
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository = mock()
    private val useCase = ObtenerFlujoUnificadoUseCase(transaccionRepository, movimientoTarjetaRepository)

    @Test
    fun `retorna transacciones y movimientos en un mismo flujo`() = runTest {
        val tx = Transaccion(
            id = "tx-1",
            uidUsuario = "uid",
            cuentaId = "cta-1",
            bancoCodigo = "POPULAR",
            fecha = Instant.parse("2026-01-01T10:00:00Z"),
            fechaPosteo = null,
            descripcionCorta = "Compra",
            descripcionOriginal = "Compra",
            descripcionNormalizada = "compra",
            monto = BigDecimal("100.00"),
            tipo = TipoTransaccion.DEBITO,
            moneda = Moneda.DOP,
            balanceDespues = null,
            referencia = null,
            serial = null,
            categoriaId = "cat-1",
            categoriaAutomatica = false,
            esDerivada = false,
            transaccionPadreId = null,
            derivadasIds = emptyList(),
            cargaId = "carga-1",
            notaUsuario = null,
            metadataBanco = emptyMap(),
            creadoEn = Instant.parse("2026-01-01T10:00:00Z"),
        )
        val mov = MovimientoTarjeta(
            id = "mov-1",
            uidUsuario = "uid",
            tarjetaId = "tar-1",
            bancoCodigo = "QIK",
            fechaTransaccion = Instant.parse("2026-01-02T10:00:00Z"),
            fechaPosteo = null,
            descripcionOriginal = "Pago",
            descripcionNormalizada = "pago",
            monto = BigDecimal("50.00"),
            montoUsd = null,
            tipoMovimiento = TipoMovimientoTarjeta.PAGO,
            moneda = Moneda.DOP,
            numeroAutorizacion = null,
            categoriaId = "cat-2",
            categoriaAutomatica = false,
            cargaId = "carga-2",
            metadataBanco = emptyMap(),
            creadoEn = Instant.parse("2026-01-02T10:00:00Z"),
        )

        whenever(
            transaccionRepository.obtenerTransacciones(eq("uid"), anyOrNull(), anyOrNull(), eq(0), anyOrNull())
        ).thenReturn(AppResult.Success(listOf(tx)))
        whenever(
            movimientoTarjetaRepository.obtenerMovimientos(eq("uid"), any(), anyOrNull())
        ).thenReturn(AppResult.Success(listOf(mov)))

        val result = useCase.ejecutar(
            uid = "uid",
            inicio = Instant.parse("2026-01-01T00:00:00Z"),
            fin = Instant.parse("2026-01-31T23:59:59Z"),
        )

        assertTrue(result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertEquals(listOf(tx), data.transacciones)
        assertEquals(listOf(mov), data.movimientos)
    }
}
