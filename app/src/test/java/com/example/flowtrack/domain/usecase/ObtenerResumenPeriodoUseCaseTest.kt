package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tests del núcleo de agrupación de [ObtenerResumenPeriodoUseCase] (issue #2, bloque D).
 * Usa la función pura `calcular(...)`; los repositorios se mockean (no se invocan).
 */
class ObtenerResumenPeriodoUseCaseTest {

    private val zona = ZoneId.of("America/Santo_Domingo")
    private val useCase = ObtenerResumenPeriodoUseCase(mock(), mock())

    private fun fecha(y: Int, m: Int, d: Int): Instant =
        LocalDate.of(y, m, d).atStartOfDay(zona).toInstant()

    private fun tx(
        f: Instant,
        monto: String,
        tipo: TipoTransaccion,
        derivada: Boolean = false,
    ) = Transaccion(
        id = "tx-${f.epochSecond}-$monto-$tipo", uidUsuario = "u", cuentaId = "c", bancoCodigo = "BANRESERVAS",
        fecha = f, fechaPosteo = null, descripcionCorta = "x", descripcionOriginal = "x", descripcionNormalizada = "x",
        monto = BigDecimal(monto), tipo = tipo, moneda = Moneda.DOP, balanceDespues = null, referencia = null,
        serial = null, categoriaId = null, categoriaAutomatica = false, esDerivada = derivada,
        transaccionPadreId = null, derivadasIds = emptyList(), cargaId = "carga", notaUsuario = null,
        metadataBanco = emptyMap(), creadoEn = f,
    )

    private fun mov(
        f: Instant,
        monto: String,
        tipo: TipoMovimientoTarjeta,
    ) = MovimientoTarjeta(
        id = "mov-${f.epochSecond}-$monto-$tipo", uidUsuario = "u", tarjetaId = "t", bancoCodigo = "QIK",
        fechaTransaccion = f, fechaPosteo = null, descripcionOriginal = "x", descripcionNormalizada = "x",
        monto = BigDecimal(monto), montoUsd = null, tipoMovimiento = tipo, moneda = Moneda.DOP,
        numeroAutorizacion = null, categoriaId = null, categoriaAutomatica = false, cargaId = "carga",
        metadataBanco = emptyMap(), creadoEn = f,
    )

    @Test
    fun `agrupa por dia - un bucket por fecha distinta`() {
        val txs = listOf(
            tx(fecha(2026, 3, 2), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 3, 2), "50.00", TipoTransaccion.DEBITO),   // mismo día
            tx(fecha(2026, 3, 3), "200.00", TipoTransaccion.CREDITO),
        )
        val r = useCase.calcular(txs, emptyList(), TipoPeriodo.DIA)
        assertEquals(2, r.buckets.size)
        val dia2 = r.buckets.first { it.inicio == LocalDate.of(2026, 3, 2) }
        assertEquals(BigDecimal("150.00"), dia2.gastos)
        assertEquals(BigDecimal("200.00"), r.totalIngresos)
        assertEquals(BigDecimal("150.00"), r.totalGastos)
    }

    @Test
    fun `agrupa por semana - lunes a domingo en el mismo bucket`() {
        // 2026-03-02 es lunes; 2026-03-08 domingo (misma semana ISO); 2026-03-09 lunes (otra semana).
        val txs = listOf(
            tx(fecha(2026, 3, 2), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 3, 8), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 3, 9), "100.00", TipoTransaccion.DEBITO),
        )
        val r = useCase.calcular(txs, emptyList(), TipoPeriodo.SEMANA)
        assertEquals(2, r.buckets.size)
        val semana1 = r.buckets.first { it.inicio == LocalDate.of(2026, 3, 2) }
        assertEquals(BigDecimal("200.00"), semana1.gastos)
    }

    @Test
    fun `agrupa por mes - todas las fechas del mes en un bucket`() {
        val txs = listOf(
            tx(fecha(2026, 3, 1), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 3, 31), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 4, 5), "300.00", TipoTransaccion.DEBITO),
        )
        val r = useCase.calcular(txs, emptyList(), TipoPeriodo.MES)
        assertEquals(2, r.buckets.size)
        val marzo = r.buckets.first { it.inicio == LocalDate.of(2026, 3, 1) }
        assertEquals(BigDecimal("200.00"), marzo.gastos)
        assertEquals(LocalDate.of(2026, 4, 1), r.buckets.last().inicio)
    }

    @Test
    fun `incluye movimientos de tarjeta y clasifica ingreso vs gasto`() {
        val movs = listOf(
            mov(fecha(2026, 3, 5), "500.00", TipoMovimientoTarjeta.COMPRA),   // gasto
            mov(fecha(2026, 3, 5), "300.00", TipoMovimientoTarjeta.PAGO),     // ingreso
            mov(fecha(2026, 3, 5), "10.00", TipoMovimientoTarjeta.CASHBACK),  // ingreso
        )
        val r = useCase.calcular(emptyList(), movs, TipoPeriodo.DIA)
        assertEquals(1, r.buckets.size)
        assertEquals(BigDecimal("500.00"), r.totalGastos)
        assertEquals(BigDecimal("310.00"), r.totalIngresos)
    }

    @Test
    fun `excluye transacciones derivadas (DGII)`() {
        val txs = listOf(
            tx(fecha(2026, 3, 2), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 3, 2), "0.15", TipoTransaccion.DEBITO, derivada = true),
        )
        val r = useCase.calcular(txs, emptyList(), TipoPeriodo.DIA)
        assertEquals(BigDecimal("100.00"), r.totalGastos)
    }

    @Test
    fun `buckets ordenados ascendente por fecha`() {
        val txs = listOf(
            tx(fecha(2026, 4, 5), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 1, 5), "100.00", TipoTransaccion.DEBITO),
            tx(fecha(2026, 3, 5), "100.00", TipoTransaccion.DEBITO),
        )
        val r = useCase.calcular(txs, emptyList(), TipoPeriodo.MES)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
            r.buckets.map { it.inicio },
        )
    }
}
