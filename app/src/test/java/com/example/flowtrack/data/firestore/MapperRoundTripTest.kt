package com.example.flowtrack.data.firestore

import com.example.flowtrack.data.firestore.dto.MovimientoTarjetaDto
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.data.firestore.mappers.toTimestamp
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.EstadoTransaccion
import com.example.flowtrack.domain.model.OrigenTransaccion
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.model.esContabilizable
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Tests de round-trip para los mappers de Firestore.
 * Verifican que la conversión dominio → DTO → dominio no pierde datos.
 * Tests JVM puros: no requieren emulador ni Firebase real.
 */
class MapperRoundTripTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun instanteReferencia(): Instant =
        Instant.ofEpochSecond(1_700_000_000L, 0)

    private fun transaccionBase(): Transaccion = Transaccion(
        id = "tx-abc-123",
        uidUsuario = "uid-test",
        cuentaId = "cuenta-001",
        bancoCodigo = "BANRESERVAS",
        fecha = instanteReferencia(),
        fechaPosteo = instanteReferencia().plusSeconds(86_400),
        descripcionCorta = "SUPERMERCADO",
        descripcionOriginal = "SUPERMERCADO LA SIRENA",
        descripcionNormalizada = "supermercado la sirena",
        monto = BigDecimal("1234.56"),
        tipo = TipoTransaccion.DEBITO,
        moneda = Moneda.DOP,
        balanceDespues = BigDecimal("9876.44"),
        referencia = "REF-001",
        serial = "SER-002",
        categoriaId = "cat-alimentos",
        categoriaAutomatica = true,
        esDerivada = false,
        transaccionPadreId = null,
        derivadasIds = emptyList(),
        origen = OrigenTransaccion.IMPORTACION_ARCHIVO,
        sourceEventId = "event-001",
        sourceMessageId = "message-001",
        sourceTransactionId = "source-tx-001",
        actualizadoEn = instanteReferencia().plusSeconds(60),
        estado = EstadoTransaccion.APROBADA,
        afectaBalance = true,
        posibleDuplicado = false,
        motivoRechazo = null,
        cargaId = "carga-001",
        notaUsuario = "nota de prueba",
        metadataBanco = mapOf("campo1" to "valor1"),
        creadoEn = instanteReferencia(),
    )

    private fun movimientoBase(): MovimientoTarjeta = MovimientoTarjeta(
        id = "mov-xyz-456",
        uidUsuario = "uid-test",
        tarjetaId = "tarjeta-001",
        bancoCodigo = "QIK",
        fechaTransaccion = instanteReferencia(),
        fechaPosteo = instanteReferencia().plusSeconds(3_600),
        descripcionOriginal = "FARMACIA CAROL",
        descripcionNormalizada = "farmacia carol",
        monto = BigDecimal("850.00"),
        tipoMovimiento = TipoMovimientoTarjeta.COMPRA,
        moneda = Moneda.DOP,
        numeroAutorizacion = "AUTH-777",
        categoriaId = "cat-salud",
        categoriaAutomatica = false,
        cargaId = "carga-002",
        metadataBanco = mapOf("origen" to "NFC"),
        creadoEn = instanteReferencia(),
    )

    // ─── Transaccion round-trip ────────────────────────────────────────────────

    @Test
    fun `transaccion toDto conserva todos los campos escalares`() {
        val original = transaccionBase()
        val dto = original.toDto()

        assertEquals(original.id, dto.id)
        assertEquals(original.uidUsuario, dto.uidUsuario)
        assertEquals(original.cuentaId, dto.cuentaId)
        assertEquals(original.bancoCodigo, dto.bancoCodigo)
        assertEquals(original.descripcionCorta, dto.descripcionCorta)
        assertEquals(original.descripcionOriginal, dto.descripcionOriginal)
        assertEquals(original.descripcionNormalizada, dto.descripcionNormalizada)
        assertEquals(original.referencia, dto.referencia)
        assertEquals(original.serial, dto.serial)
        assertEquals(original.categoriaId, dto.categoriaId)
        assertEquals(original.categoriaAutomatica, dto.categoriaAutomatica)
        assertEquals(original.esDerivada, dto.esDerivada)
        assertEquals(original.transaccionPadreId, dto.transaccionPadreId)
        assertEquals(original.derivadasIds, dto.derivadasIds)
        assertEquals(original.origen.name, dto.origen)
        assertEquals(original.sourceEventId, dto.sourceEventId)
        assertEquals(original.sourceMessageId, dto.sourceMessageId)
        assertEquals(original.sourceTransactionId, dto.sourceTransactionId)
        assertEquals(original.actualizadoEn.epochSecond, dto.actualizadoEn?.seconds)
        assertEquals(original.estado.name, dto.estado)
        assertEquals(original.afectaBalance, dto.afectaBalance)
        assertEquals(original.posibleDuplicado, dto.posibleDuplicado)
        assertEquals(original.motivoRechazo, dto.motivoRechazo)
        assertEquals(original.cargaId, dto.cargaId)
        assertEquals(original.notaUsuario, dto.notaUsuario)
        assertEquals(original.metadataBanco, dto.metadataBanco)
    }

    @Test
    fun `transaccion toDto convierte monto BigDecimal a String canonico correctamente`() {
        val original = transaccionBase()
        val dto = original.toDto()
        assertEquals("1234.56", dto.monto)
        assertEquals("9876.44", dto.balanceDespues)
    }

    @Test
    fun `transaccion toDto serializa TipoTransaccion como String`() {
        val dto = transaccionBase().toDto()
        assertEquals("DEBITO", dto.tipo)
    }

    @Test
    fun `transaccion toDto serializa Moneda como String`() {
        val dto = transaccionBase().toDto()
        assertEquals("DOP", dto.moneda)
    }

    @Test
    fun `transaccion toDto convierte fecha Instant a Timestamp`() {
        val original = transaccionBase()
        val dto = original.toDto()
        assertNotNull(dto.fecha)
        assertEquals(original.fecha.epochSecond, dto.fecha!!.seconds)
        assertEquals(original.fecha.nano, dto.fecha!!.nanoseconds)
    }

    @Test
    fun `transaccion toDomain recupera monto como BigDecimal con escala 2`() {
        val dto = transaccionBase().toDto()
        val dominio = dto.toDomain()
        assertEquals(BigDecimal("1234.56"), dominio.monto)
        assertEquals(2, dominio.monto.scale())
    }

    @Test
    fun `transaccion toDomain recupera balanceDespues nullable correctamente`() {
        val dto = transaccionBase().toDto()
        val dominio = dto.toDomain()
        assertNotNull(dominio.balanceDespues)
        assertEquals(BigDecimal("9876.44"), dominio.balanceDespues)
        assertEquals(2, dominio.balanceDespues!!.scale())
    }

    @Test
    fun `transaccion toDomain con balanceDespues null no falla`() {
        val original = transaccionBase().copy(balanceDespues = null)
        val dto = original.toDto()
        assertNull(dto.balanceDespues)
        val dominio = dto.toDomain()
        assertNull(dominio.balanceDespues)
    }

    @Test
    fun `transaccion toDomain deserializa TipoTransaccion desde String`() {
        val dto = transaccionBase().toDto()
        val dominio = dto.toDomain()
        assertEquals(TipoTransaccion.DEBITO, dominio.tipo)
    }

    @Test
    fun `transaccion toDomain deserializa Moneda desde String`() {
        val dto = transaccionBase().toDto()
        val dominio = dto.toDomain()
        assertEquals(Moneda.DOP, dominio.moneda)
    }

    @Test
    fun `transaccion toDomain recupera fecha desde Timestamp`() {
        val original = transaccionBase()
        val dto = original.toDto()
        val dominio = dto.toDomain()
        assertEquals(original.fecha.epochSecond, dominio.fecha.epochSecond)
    }

    @Test
    fun `transaccion round-trip mantiene monto intacto`() {
        val original = transaccionBase()
        val dominio = original.toDto().toDomain()
        assertEquals(original.monto, dominio.monto)
    }

    @Test
    fun `transaccion round-trip mantiene todos los campos no monetarios`() {
        val original = transaccionBase()
        val dominio = original.toDto().toDomain()

        assertEquals(original.id, dominio.id)
        assertEquals(original.uidUsuario, dominio.uidUsuario)
        assertEquals(original.cuentaId, dominio.cuentaId)
        assertEquals(original.bancoCodigo, dominio.bancoCodigo)
        assertEquals(original.descripcionCorta, dominio.descripcionCorta)
        assertEquals(original.descripcionOriginal, dominio.descripcionOriginal)
        assertEquals(original.descripcionNormalizada, dominio.descripcionNormalizada)
        assertEquals(original.tipo, dominio.tipo)
        assertEquals(original.moneda, dominio.moneda)
        assertEquals(original.referencia, dominio.referencia)
        assertEquals(original.serial, dominio.serial)
        assertEquals(original.categoriaId, dominio.categoriaId)
        assertEquals(original.categoriaAutomatica, dominio.categoriaAutomatica)
        assertEquals(original.esDerivada, dominio.esDerivada)
        assertEquals(original.origen, dominio.origen)
        assertEquals(original.sourceEventId, dominio.sourceEventId)
        assertEquals(original.sourceMessageId, dominio.sourceMessageId)
        assertEquals(original.sourceTransactionId, dominio.sourceTransactionId)
        assertEquals(original.actualizadoEn.epochSecond, dominio.actualizadoEn.epochSecond)
        assertEquals(original.estado, dominio.estado)
        assertEquals(original.afectaBalance, dominio.afectaBalance)
        assertEquals(original.posibleDuplicado, dominio.posibleDuplicado)
        assertEquals(original.motivoRechazo, dominio.motivoRechazo)
        assertEquals(original.cargaId, dominio.cargaId)
        assertEquals(original.notaUsuario, dominio.notaUsuario)
        assertEquals(original.metadataBanco, dominio.metadataBanco)
    }

    @Test
    fun `transaccion aprobada y activa cuenta como contabilizable`() {
        val tx = transaccionBase()
        assertEquals(true, tx.esContabilizable)
    }

    @Test
    fun `transaccion rechazada deja de ser contabilizable`() {
        val tx = transaccionBase().copy(estado = EstadoTransaccion.RECHAZADA, afectaBalance = false)
        assertEquals(false, tx.esContabilizable)
    }

    @Test
    fun `transaccion con tipo CREDITO serializa y deserializa correctamente`() {
        val tx = transaccionBase().copy(tipo = TipoTransaccion.CREDITO)
        val dominio = tx.toDto().toDomain()
        assertEquals(TipoTransaccion.CREDITO, dominio.tipo)
    }

    @Test
    fun `transaccion con moneda USD serializa y deserializa correctamente`() {
        val tx = transaccionBase().copy(moneda = Moneda.USD)
        val dominio = tx.toDto().toDomain()
        assertEquals(Moneda.USD, dominio.moneda)
    }

    @Test
    fun `transaccion con monto cero round-trip correcto`() {
        val tx = transaccionBase().copy(monto = BigDecimal.ZERO.setScale(2))
        val dominio = tx.toDto().toDomain()
        assertEquals(BigDecimal("0.00"), dominio.monto)
    }

    @Test
    fun `transaccion con monto grande round-trip no pierde precision`() {
        // La serialización canónica en String conserva la precisión exacta.
        val montoGrande = BigDecimal("999999.99")
        val tx = transaccionBase().copy(monto = montoGrande)
        val dominio = tx.toDto().toDomain()
        assertEquals(montoGrande, dominio.monto)
    }

    @Test
    fun `transaccion con fechaPosteo null round-trip correcto`() {
        val tx = transaccionBase().copy(fechaPosteo = null)
        val dto = tx.toDto()
        assertNull(dto.fechaPosteo)
        val dominio = dto.toDomain()
        assertNull(dominio.fechaPosteo)
    }

    @Test
    fun `transaccion con derivadasIds no vacia round-trip correcto`() {
        val tx = transaccionBase().copy(derivadasIds = listOf("id1", "id2"))
        val dominio = tx.toDto().toDomain()
        assertEquals(listOf("id1", "id2"), dominio.derivadasIds)
    }

    // ─── MovimientoTarjeta round-trip ─────────────────────────────────────────

    @Test
    fun `movimientoTarjeta toDto conserva todos los campos escalares`() {
        val original = movimientoBase()
        val dto = original.toDto()

        assertEquals(original.id, dto.id)
        assertEquals(original.uidUsuario, dto.uidUsuario)
        assertEquals(original.tarjetaId, dto.tarjetaId)
        assertEquals(original.bancoCodigo, dto.bancoCodigo)
        assertEquals(original.descripcionOriginal, dto.descripcionOriginal)
        assertEquals(original.descripcionNormalizada, dto.descripcionNormalizada)
        assertEquals(original.numeroAutorizacion, dto.numeroAutorizacion)
        assertEquals(original.categoriaId, dto.categoriaId)
        assertEquals(original.categoriaAutomatica, dto.categoriaAutomatica)
        assertEquals(original.cargaId, dto.cargaId)
        assertEquals(original.metadataBanco, dto.metadataBanco)
    }

    @Test
    fun `movimientoTarjeta toDto convierte monto BigDecimal a String canonico`() {
        val dto = movimientoBase().toDto()
        assertEquals("850.00", dto.monto)
    }

    @Test
    fun `movimientoTarjeta toDto serializa TipoMovimientoTarjeta como String`() {
        val dto = movimientoBase().toDto()
        assertEquals("COMPRA", dto.tipoMovimiento)
    }

    @Test
    fun `movimientoTarjeta toDto serializa Moneda como String`() {
        val dto = movimientoBase().toDto()
        assertEquals("DOP", dto.moneda)
    }

    @Test
    fun `movimientoTarjeta toDomain recupera monto como BigDecimal escala 2`() {
        val dto = movimientoBase().toDto()
        val dominio = dto.toDomain()
        assertEquals(BigDecimal("850.00"), dominio.monto)
        assertEquals(2, dominio.monto.scale())
    }

    @Test
    fun `movimientoTarjeta toDomain deserializa TipoMovimientoTarjeta desde String`() {
        val dto = movimientoBase().toDto()
        val dominio = dto.toDomain()
        assertEquals(TipoMovimientoTarjeta.COMPRA, dominio.tipoMovimiento)
    }

    @Test
    fun `movimientoTarjeta toDomain deserializa Moneda desde String`() {
        val dto = movimientoBase().toDto()
        val dominio = dto.toDomain()
        assertEquals(Moneda.DOP, dominio.moneda)
    }

    @Test
    fun `movimientoTarjeta round-trip mantiene monto intacto`() {
        val original = movimientoBase()
        val dominio = original.toDto().toDomain()
        assertEquals(original.monto, dominio.monto)
    }

    @Test
    fun `movimientoTarjeta round-trip mantiene todos los campos no monetarios`() {
        val original = movimientoBase()
        val dominio = original.toDto().toDomain()

        assertEquals(original.id, dominio.id)
        assertEquals(original.uidUsuario, dominio.uidUsuario)
        assertEquals(original.tarjetaId, dominio.tarjetaId)
        assertEquals(original.bancoCodigo, dominio.bancoCodigo)
        assertEquals(original.descripcionOriginal, dominio.descripcionOriginal)
        assertEquals(original.descripcionNormalizada, dominio.descripcionNormalizada)
        assertEquals(original.tipoMovimiento, dominio.tipoMovimiento)
        assertEquals(original.moneda, dominio.moneda)
        assertEquals(original.numeroAutorizacion, dominio.numeroAutorizacion)
        assertEquals(original.categoriaId, dominio.categoriaId)
        assertEquals(original.categoriaAutomatica, dominio.categoriaAutomatica)
        assertEquals(original.cargaId, dominio.cargaId)
        assertEquals(original.metadataBanco, dominio.metadataBanco)
    }

    @Test
    fun `movimientoTarjeta con fechaPosteo null round-trip correcto`() {
        val mov = movimientoBase().copy(fechaPosteo = null)
        val dto = mov.toDto()
        assertNull(dto.fechaPosteo)
        val dominio = dto.toDomain()
        assertNull(dominio.fechaPosteo)
    }

    @Test
    fun `movimientoTarjeta con numeroAutorizacion null round-trip correcto`() {
        val mov = movimientoBase().copy(numeroAutorizacion = null)
        val dto = mov.toDto()
        assertNull(dto.numeroAutorizacion)
        val dominio = dto.toDomain()
        assertNull(dominio.numeroAutorizacion)
    }

    @Test
    fun `movimientoTarjeta con categoriaId null round-trip correcto`() {
        val mov = movimientoBase().copy(categoriaId = null)
        val dto = mov.toDto()
        assertNull(dto.categoriaId)
        val dominio = dto.toDomain()
        assertNull(dominio.categoriaId)
    }

    @Test
    fun `movimientoTarjeta con montoUsd no nulo round-trip correcto`() {
        val mov = movimientoBase().copy(montoUsd = BigDecimal("123.45"))
        val dto = mov.toDto()
        assertEquals("123.45", dto.montoUsd)
        val dominio = dto.toDomain()
        assertEquals(BigDecimal("123.45"), dominio.montoUsd)
        assertEquals(2, dominio.montoUsd!!.scale())
    }

    @Test
    fun `movimientoTarjeta con montoUsd null round-trip correcto`() {
        val mov = movimientoBase().copy(montoUsd = null)
        val dto = mov.toDto()
        assertNull(dto.montoUsd)
        val dominio = dto.toDomain()
        assertNull(dominio.montoUsd)
    }

    // ─── Enums TipoTransaccion ─────────────────────────────────────────────────

    @Test
    fun `enum TipoTransaccion DEBITO serializa como nombre exacto`() {
        val dto = transaccionBase().copy(tipo = TipoTransaccion.DEBITO).toDto()
        assertEquals(TipoTransaccion.DEBITO.name, dto.tipo)
    }

    @Test
    fun `enum TipoTransaccion CREDITO serializa como nombre exacto`() {
        val dto = transaccionBase().copy(tipo = TipoTransaccion.CREDITO).toDto()
        assertEquals(TipoTransaccion.CREDITO.name, dto.tipo)
    }

    @Test
    fun `enum TipoTransaccion DEBITO round-trip correcto`() {
        val tx = transaccionBase().copy(tipo = TipoTransaccion.DEBITO)
        assertEquals(TipoTransaccion.DEBITO, tx.toDto().toDomain().tipo)
    }

    @Test
    fun `enum TipoTransaccion CREDITO round-trip correcto`() {
        val tx = transaccionBase().copy(tipo = TipoTransaccion.CREDITO)
        assertEquals(TipoTransaccion.CREDITO, tx.toDto().toDomain().tipo)
    }

    // ─── Enums TipoMovimientoTarjeta ───────────────────────────────────────────

    @Test
    fun `todos los valores de TipoMovimientoTarjeta serializan y deserializan correctamente`() {
        TipoMovimientoTarjeta.entries.forEach { tipo ->
            val mov = movimientoBase().copy(tipoMovimiento = tipo)
            val dto = mov.toDto()
            assertEquals(tipo.name, dto.tipoMovimiento)
            val dominio = dto.toDomain()
            assertEquals(tipo, dominio.tipoMovimiento)
        }
    }

    // ─── TransaccionDto construido directamente (simula lectura de Firestore) ──

    @Test
    fun `TransaccionDto con fecha null usa Instant now como fallback`() {
        val dto = TransaccionDto(
            id = "tx-fallback",
            uidUsuario = "uid-test",
            cuentaId = "cuenta-001",
            bancoCodigo = "BANRESERVAS",
            fecha = null,  // simula campo ausente en Firestore
            monto = "100.00",
            tipo = "DEBITO",
            moneda = "DOP",
            cargaId = "carga-001",
            descripcionCorta = "PRUEBA",
        )
        // No debe lanzar excepción — usa fallback Instant.now()
        val dominio = dto.toDomain()
        assertNotNull(dominio.fecha)
    }

    @Test
    fun `TransaccionDto con creadoEn null usa Instant now como fallback`() {
        val dto = TransaccionDto(
            id = "tx-fallback2",
            uidUsuario = "uid-test",
            cuentaId = "cuenta-001",
            bancoCodigo = "BANRESERVAS",
            fecha = Timestamp(instanteReferencia().epochSecond, instanteReferencia().nano),
            monto = "200.00",
            tipo = "CREDITO",
            moneda = "DOP",
            cargaId = "carga-001",
            descripcionCorta = "PRUEBA",
            creadoEn = null, // simula campo ausente
        )
        val dominio = dto.toDomain()
        assertNotNull(dominio.creadoEn)
    }

    @Test
    fun `MovimientoTarjetaDto con fechaTransaccion null usa Instant now como fallback`() {
        val dto = MovimientoTarjetaDto(
            id = "mov-fallback",
            uidUsuario = "uid-test",
            tarjetaId = "tarjeta-001",
            bancoCodigo = "QIK",
            fechaTransaccion = null,
            monto = "300.00",
            tipoMovimiento = "PAGO",
            moneda = "DOP",
            cargaId = "carga-003",
        )
        val dominio = dto.toDomain()
        assertNotNull(dominio.fechaTransaccion)
        assertEquals(TipoMovimientoTarjeta.PAGO, dominio.tipoMovimiento)
    }

    // ─── Timestamp → Instant conversión bidireccional ──────────────────────────

    @Test
    fun `Instant a Timestamp conserva segundos y nanosegundos`() {
        val instante = Instant.ofEpochSecond(1_700_000_000L, 123_456_789)
        val ts = instante.toTimestamp()
        assertEquals(1_700_000_000L, ts.seconds)
        assertEquals(123_456_789, ts.nanoseconds)
    }

    @Test
    fun `Timestamp a Instant conserva el epoch second`() {
        val ts = Timestamp(1_700_000_000L, 0)
        val instante = ts.toDate().toInstant()
        assertEquals(1_700_000_000L, instante.epochSecond)
    }
}
