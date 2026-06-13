package com.example.flowtrack.domain.usecases.carga

import com.example.flowtrack.data.firestore.mappers.toDomainEstadoTarjeta
import com.example.flowtrack.data.firestore.mappers.toDomainMovimientoTarjeta
import com.example.flowtrack.data.firestore.mappers.toDomainTarjeta
import com.example.flowtrack.data.parsers.cibao.CibaoXlsParser
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.FixtureLoader
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.FileFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Test de integración del mapeo de persistencia de tarjeta.
 *
 * Reproduce la transformación que hace `ProcesarArchivoUseCase.mapearYPersistir` en la rama
 * TARJETA (parser → EstadoCuentaNormalizado → Tarjeta + EstadoTarjetaSnap + List<MovimientoTarjeta>)
 * y verifica:
 *   - que los objetos de dominio que se pasan a `persistirCargaTarjeta` son correctos,
 *   - que los IDs son determinísticos (re-importar el mismo archivo no duplica).
 *
 * No toca Firestore (eso lo audita el agente firebase-persistence-tester). Usa el fixture
 * real de Cibao (XLS, POI corre en JVM). Si el fixture no está, el test se omite.
 */
class TarjetaPersistenceMappingTest {

    private val uid = "test_uid"

    private suspend fun parsearCibao(): ParseResult.Success? {
        val bytes = FixtureLoader.cargar("cibao_v1.xls", "cibao") ?: return null
        val archivo = ArchivoEntrada("cibao.xls", "xls", bytes.size.toLong(), bytes, "application/vnd.ms-excel")
        val req = ImportRequest(uid, "CIBAO", ProductoTipo.TARJETA, FileFormat.XLS, archivo)
        return CibaoXlsParser().parse(req) as? ParseResult.Success
    }

    @Test
    fun `mapeo de carga de tarjeta produce dominio correcto`() = runTest {
        val exito = parsearCibao() ?: run {
            println("⚠️ Fixture Cibao no disponible. Test omitido.")
            return@runTest
        }
        val estado = exito.estado
        val cargaId = "carga_test_001"
        val periodoInicio = Instant.parse("2026-03-01T00:00:00Z")
        val periodoFin = Instant.parse("2026-03-31T00:00:00Z")

        val tarjeta = estado.toDomainTarjeta(uid)
        val movimientos = estado.movimientos.map {
            it.toDomainMovimientoTarjeta(uid, tarjeta.id, estado.bancoCodigo, cargaId)
        }
        val snap = estado.toDomainEstadoTarjeta(uid, tarjeta.id, cargaId, periodoInicio, periodoFin)

        // ── Tarjeta ──
        assertEquals("CIBAO", tarjeta.bancoCodigo)
        assertEquals(uid, tarjeta.uidUsuario)
        assertEquals(Moneda.DOP, tarjeta.moneda)
        assertEquals(EstadoTarjeta.ACTIVO, tarjeta.estado)
        assertTrue("ultimos4 = 4 dígitos", tarjeta.ultimos4.length == 4 && tarjeta.ultimos4.all { it.isDigit() })
        assertTrue("Límite > 0", tarjeta.limiteCredito > BigDecimal.ZERO)

        // ── EstadoTarjetaSnap ──
        assertEquals(tarjeta.id, snap.tarjetaId)
        assertEquals(cargaId, snap.cargaId)
        assertTrue("Balance al corte > 0", snap.balanceAlCorte > BigDecimal.ZERO)
        assertTrue("fechaLimitePago >= fechaCorte", !snap.fechaLimitePago.isBefore(snap.fechaCorte))

        // ── Movimientos ──
        assertEquals("Todos los movimientos parseados se mapean", estado.movimientos.size, movimientos.size)
        assertTrue("Todo movimiento pertenece a la tarjeta", movimientos.all { it.tarjetaId == tarjeta.id })
        assertTrue("Todo movimiento pertenece a la carga", movimientos.all { it.cargaId == cargaId })
        // Bimoneda preservada en el dominio
        assertTrue("Al menos 1 movimiento con montoUsd",
            movimientos.any { (it.montoUsd ?: BigDecimal.ZERO) > BigDecimal.ZERO })
        assertTrue("Tipos válidos", movimientos.all { it.tipoMovimiento in TipoMovimientoTarjeta.entries })

        // ── Idempotencia: re-mapear el mismo estado produce IDs idénticos ──
        val tarjeta2 = estado.toDomainTarjeta(uid)
        val movimientos2 = estado.movimientos.map {
            it.toDomainMovimientoTarjeta(uid, tarjeta2.id, estado.bancoCodigo, cargaId)
        }
        val snap2 = estado.toDomainEstadoTarjeta(uid, tarjeta2.id, cargaId, periodoInicio, periodoFin)
        assertEquals("ID de tarjeta determinístico", tarjeta.id, tarjeta2.id)
        assertEquals("ID de estado determinístico", snap.id, snap2.id)
        assertEquals("IDs de movimientos determinísticos",
            movimientos.map { it.id }, movimientos2.map { it.id })

        // Sin colisiones de ID entre movimientos distintos
        assertEquals("IDs de movimientos únicos",
            movimientos.size, movimientos.map { it.id }.toSet().size)

        println("✅ Mapeo tarjeta: ${movimientos.size} movimientos, IDs únicos y determinísticos")
    }
}
