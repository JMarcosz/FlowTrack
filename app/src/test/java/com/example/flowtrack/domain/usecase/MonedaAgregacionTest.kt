package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.model.Transaccion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class MonedaAgregacionTest {

    @Test
    fun `aDop convierte USD a DOP con tasa de compra`() {
        val tasa = TasaCambio(
            compra = BigDecimal("58.50"),
            venta = BigDecimal("59.10"),
            fecha = LocalDate.of(2026, 1, 1),
            fuente = "Mock",
        )

        assertEquals(0, BigDecimal("10.00").aDop(Moneda.USD, tasa).compareTo(BigDecimal("585.00")))
        assertEquals(0, BigDecimal("10.00").aDop(Moneda.DOP, tasa).compareTo(BigDecimal("10.00")))
    }

    @Test
    fun `normalizarMoneda deja los totales listos para agregacion`() {
        val tasa = TasaCambio(
            compra = BigDecimal("58.50"),
            venta = BigDecimal("59.10"),
            fecha = LocalDate.of(2026, 1, 1),
            fuente = "Mock",
        )

        val transaccion = Transaccion(
            id = "tx-1",
            uidUsuario = "uid",
            cuentaId = "cta-1",
            bancoCodigo = "POPULAR",
            fecha = Instant.now(),
            fechaPosteo = null,
            descripcionCorta = "Compra",
            descripcionOriginal = "Compra",
            descripcionNormalizada = "compra",
            monto = BigDecimal("10.00"),
            tipo = TipoTransaccion.DEBITO,
            moneda = Moneda.USD,
            balanceDespues = BigDecimal("12.00"),
            referencia = null,
            serial = null,
            categoriaId = null,
            categoriaAutomatica = false,
            esDerivada = false,
            cargaId = "carga-1",
            notaUsuario = null,
            metadataBanco = emptyMap(),
            creadoEn = Instant.now(),
        )

        val movimiento = MovimientoTarjeta(
            id = "mv-1",
            uidUsuario = "uid",
            tarjetaId = "tar-1",
            bancoCodigo = "POPULAR",
            fechaTransaccion = Instant.now(),
            fechaPosteo = null,
            descripcionOriginal = "Compra",
            descripcionNormalizada = "compra",
            monto = BigDecimal("10.00"),
            montoUsd = null,
            tipoMovimiento = TipoMovimientoTarjeta.COMPRA,
            moneda = Moneda.USD,
            numeroAutorizacion = null,
            categoriaId = null,
            categoriaAutomatica = false,
            cargaId = "carga-1",
            metadataBanco = emptyMap(),
            creadoEn = Instant.now(),
        )

        val txNormalizada = transaccion.normalizarMoneda(tasa)
        val mvNormalizado = movimiento.normalizarMoneda(tasa)
        val totales = calcularTotalesFinancieros(listOf(txNormalizada), listOf(mvNormalizado))

        assertEquals(0, txNormalizada.monto.compareTo(BigDecimal("585.00")))
        assertEquals(0, txNormalizada.balanceDespues?.compareTo(BigDecimal("702.00")))
        assertEquals(0, mvNormalizado.monto.compareTo(BigDecimal("585.00")))
        assertEquals(0, totales.gastos.compareTo(BigDecimal("1170.00")))
    }
}
