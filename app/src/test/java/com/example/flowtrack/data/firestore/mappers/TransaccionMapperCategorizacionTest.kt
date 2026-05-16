package com.example.flowtrack.data.firestore.mappers

import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.usecase.CategorizadorTransaccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Tests de la integración entre TransaccionNormalizada.toDomain() y CategorizadorTransaccion.
 *
 * Bug documentado: categoriaId siempre queda null porque ProcesarArchivoUseCase llama
 * toDomain() sin pasar categoriaId. El CategorizadorTransaccion existe pero nunca se
 * invoca en el flujo de importación.
 *
 * Estos tests verifican:
 *   1. Que toDomain() SIN categoriaId produce categoriaId=null y categoriaAutomatica=false
 *   2. Que toDomain() CON categoriaId produce categoriaId con valor y categoriaAutomatica=true
 *   3. Que el flujo completo (CategorizadorTransaccion → toDomain) funciona end-to-end
 *
 * Ejecutar:
 *   .\gradlew.bat testDebugUnitTest --tests "com.example.flowtrack.data.firestore.mappers.TransaccionMapperCategorizacionTest"
 */
class TransaccionMapperCategorizacionTest {

    private lateinit var categorizador: CategorizadorTransaccion

    private val CAT_ALIMENTACION = "cat_alimentacion"
    private val CAT_TRANSFERENCIA = "cat_transferencia"
    private val CAT_ATM = "cat_atm"

    @Before
    fun setUp() {
        categorizador = CategorizadorTransaccion(
            listOf(
                regla("r1", "SUPERMERCADO", TipoMatch.CONTIENE, CAT_ALIMENTACION, 10),
                regla("r2", "TRANSFERENCIA", TipoMatch.CONTIENE, CAT_TRANSFERENCIA, 8),
                regla("r3", "RETIRO ATM", TipoMatch.CONTIENE, CAT_ATM, 10),
            )
        )
    }

    // ─── Bug demostración: toDomain sin categoriaId ───────────────────────────

    @Test
    fun `toDomain sin categoriaId produce categoriaId null y categoriaAutomatica false`() {
        val txNorm = crearTxNormalizada(descripcion = "SUPERMERCADO NACIONAL", tipo = TipoTransaccion.DEBITO)
        // Llamada actual en ProcesarArchivoUseCase — sin pasar categoriaId
        val transaccion = txNorm.toDomain(
            uidUsuario = "uid_test",
            cuentaId = "cuenta_test",
            bancoCodigo = "POPULAR",
            cargaId = "carga_test",
            // categoriaId no se pasa → queda null (BUG DOCUMENTADO)
        )
        assertNull(
            "BUG: categoriaId debe ser null cuando no se pasa al mapper. " +
            "Esto demuestra que la categorización no está integrada en el flujo de importación.",
            transaccion.categoriaId
        )
        assertFalse(
            "BUG: categoriaAutomatica debe ser false cuando categoriaId es null",
            transaccion.categoriaAutomatica
        )
    }

    // ─── Corrección: toDomain con categoriaId ────────────────────────────────

    @Test
    fun `toDomain con categoriaId produce categoriaId con valor y categoriaAutomatica true`() {
        val txNorm = crearTxNormalizada(descripcion = "SUPERMERCADO NACIONAL", tipo = TipoTransaccion.DEBITO)
        val transaccion = txNorm.toDomain(
            uidUsuario = "uid_test",
            cuentaId = "cuenta_test",
            bancoCodigo = "POPULAR",
            cargaId = "carga_test",
            categoriaId = CAT_ALIMENTACION,
        )
        assertNotNull("categoriaId debe tener valor cuando se pasa al mapper", transaccion.categoriaId)
        assertEquals("categoriaId debe ser el valor pasado", CAT_ALIMENTACION, transaccion.categoriaId)
        assertTrue("categoriaAutomatica debe ser true cuando categoriaId tiene valor",
            transaccion.categoriaAutomatica)
    }

    // ─── Flujo completo: CategorizadorTransaccion → toDomain ─────────────────

    @Test
    fun `flujo completo supermercado se categoriza como alimentacion`() {
        val txNorm = crearTxNormalizada(descripcion = "SUPERMERCADO NACIONAL SANTIAGO", tipo = TipoTransaccion.DEBITO)
        val categoriaId = categorizador.clasificar(txNorm.descripcionOriginal)
        val transaccion = txNorm.toDomain(
            uidUsuario = "uid_test",
            cuentaId = "cuenta_test",
            bancoCodigo = "BANRESERVAS",
            cargaId = "carga_test",
            categoriaId = categoriaId,
        )
        assertEquals("El supermercado debe clasificarse como alimentacion",
            CAT_ALIMENTACION, transaccion.categoriaId)
        assertTrue("categoriaAutomatica debe ser true", transaccion.categoriaAutomatica)
    }

    @Test
    fun `flujo completo retiro ATM se categoriza como atm`() {
        val txNorm = crearTxNormalizada(descripcion = "RETIRO ATM CALLE EL CONDE", tipo = TipoTransaccion.DEBITO)
        val categoriaId = categorizador.clasificar(txNorm.descripcionOriginal)
        val transaccion = txNorm.toDomain(
            uidUsuario = "uid_test",
            cuentaId = "cuenta_test",
            bancoCodigo = "POPULAR",
            cargaId = "carga_test",
            categoriaId = categoriaId,
        )
        assertEquals("El retiro ATM debe clasificarse como atm", CAT_ATM, transaccion.categoriaId)
    }

    @Test
    fun `flujo completo descripcion sin keyword queda sin categoria`() {
        val txNorm = crearTxNormalizada(descripcion = "CARGO MENSUAL XYZ", tipo = TipoTransaccion.DEBITO)
        val categoriaId = categorizador.clasificar(txNorm.descripcionOriginal)
        val transaccion = txNorm.toDomain(
            uidUsuario = "uid_test",
            cuentaId = "cuenta_test",
            bancoCodigo = "CIBAO",
            cargaId = "carga_test",
            categoriaId = categoriaId,
        )
        assertNull("Descripcion sin keyword conocida debe quedar sin categoria",
            transaccion.categoriaId)
        assertFalse("categoriaAutomatica debe ser false cuando no hay categoria",
            transaccion.categoriaAutomatica)
    }

    @Test
    fun `flujo completo lista de transacciones - conteo de categorizadas vs no categorizadas`() {
        val txsNormalizadas = listOf(
            crearTxNormalizada("SUPERMERCADO NACIONAL", TipoTransaccion.DEBITO, BigDecimal("500.00")),
            crearTxNormalizada("RETIRO ATM CENTRO", TipoTransaccion.DEBITO, BigDecimal("2000.00")),
            crearTxNormalizada("TRANSFERENCIA ENTRANTE BPRD", TipoTransaccion.CREDITO, BigDecimal("45000.00")),
            crearTxNormalizada("PAGO FACTURA REFERENCIA 99", TipoTransaccion.DEBITO, BigDecimal("800.00")),
            crearTxNormalizada("CONSUMO POS FARMACIA XYZ", TipoTransaccion.DEBITO, BigDecimal("350.00")),
        )

        val transacciones = txsNormalizadas.map { txNorm ->
            val categoriaId = categorizador.clasificar(txNorm.descripcionOriginal)
            txNorm.toDomain(
                uidUsuario = "uid_test",
                cuentaId = "cuenta_test",
                bancoCodigo = "POPULAR",
                cargaId = "carga_test",
                categoriaId = categoriaId,
            )
        }

        val categorizadas = transacciones.filter { it.categoriaId != null }
        val noCategorizadas = transacciones.filter { it.categoriaId == null }

        // SUPERMERCADO, RETIRO ATM, TRANSFERENCIA → 3 deben categorizarse
        assertEquals("Deben categorizarse 3 de 5 transacciones", 3, categorizadas.size)
        // PAGO FACTURA, CONSUMO POS FARMACIA → 2 sin categoria conocida
        assertEquals("Deben quedar 2 sin categoria", 2, noCategorizadas.size)

        // Todos los categorizados tienen categoriaAutomatica = true
        categorizadas.forEach { tx ->
            assertTrue("categoriaAutomatica debe ser true para transacciones categorizadas",
                tx.categoriaAutomatica)
        }
        // Todos los no categorizados tienen categoriaAutomatica = false
        noCategorizadas.forEach { tx ->
            assertFalse("categoriaAutomatica debe ser false para transacciones sin categoria",
                tx.categoriaAutomatica)
        }
    }

    @Test
    fun `credito se categoriza correctamente igual que debito`() {
        // La categorización no debe depender del tipo DEBITO/CREDITO — solo de la descripción
        val txCredito = crearTxNormalizada("TRANSFERENCIA RECIBIDA ACH", TipoTransaccion.CREDITO)
        val categoriaId = categorizador.clasificar(txCredito.descripcionOriginal)
        val transaccion = txCredito.toDomain(
            uidUsuario = "uid_test",
            cuentaId = "cuenta_test",
            bancoCodigo = "POPULAR",
            cargaId = "carga_test",
            categoriaId = categoriaId,
        )
        assertEquals("La transferencia recibida (CREDITO) debe categorizarse como transferencia",
            CAT_TRANSFERENCIA, transaccion.categoriaId)
        assertEquals("El tipo debe seguir siendo CREDITO después de categorizar",
            TipoTransaccion.CREDITO, transaccion.tipo)
    }

    // ─── Integridad de campos en toDomain ────────────────────────────────────

    @Test
    fun `toDomain preserva todos los campos de la transaccion normalizada`() {
        val monto = BigDecimal("1234.56")
        val descripcion = "SUPERMERCADO CORONA MEGACENTRO"
        val txNorm = crearTxNormalizada(descripcion, TipoTransaccion.DEBITO, monto)

        val categoriaId = categorizador.clasificar(descripcion)
        val transaccion = txNorm.toDomain(
            uidUsuario = "uid_abc",
            cuentaId = "cuenta_xyz",
            bancoCodigo = "POPULAR",
            cargaId = "carga_123",
            categoriaId = categoriaId,
        )

        assertEquals("uid debe preservarse", "uid_abc", transaccion.uidUsuario)
        assertEquals("cuentaId debe preservarse", "cuenta_xyz", transaccion.cuentaId)
        assertEquals("bancoCodigo debe preservarse", "POPULAR", transaccion.bancoCodigo)
        assertEquals("monto debe preservarse", monto, transaccion.monto)
        assertEquals("tipo debe preservarse", TipoTransaccion.DEBITO, transaccion.tipo)
        assertEquals("moneda debe preservarse", Moneda.DOP, transaccion.moneda)
        assertEquals("descripcionOriginal debe preservarse", descripcion, transaccion.descripcionOriginal)
    }

    @Test
    fun `monto nunca usa Double - es BigDecimal puro`() {
        val txNorm = crearTxNormalizada("SUPERMERCADO TEST", TipoTransaccion.DEBITO, BigDecimal("99.99"))
        val transaccion = txNorm.toDomain("uid", "cuenta", "POPULAR", "carga")
        // Verificar que el monto es BigDecimal con escala correcta — no Double
        assertTrue("monto debe ser BigDecimal", transaccion.monto is BigDecimal)
        assertEquals("monto debe ser exactamente 99.99", BigDecimal("99.99"), transaccion.monto)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun crearTxNormalizada(
        descripcion: String,
        tipo: TipoTransaccion,
        monto: BigDecimal = BigDecimal("1000.00"),
    ) = TransaccionNormalizada(
        fecha = LocalDate.of(2024, 1, 15),
        fechaPosteo = null,
        descripcionCorta = descripcion.take(40),
        descripcionOriginal = descripcion,
        monto = monto,
        tipo = tipo,
        moneda = Moneda.DOP,
        balanceDespues = null,
        referencia = null,
        serial = null,
    )

    private fun regla(
        id: String,
        patron: String,
        tipoMatch: TipoMatch,
        categoriaId: String,
        prioridad: Int,
    ) = ReglaCategoria(
        id = id,
        uidUsuario = null,
        patron = patron,
        tipoMatch = tipoMatch,
        categoriaId = categoriaId,
        prioridad = prioridad,
        confianza = 0,
        activa = true,
        creadoPor = "SISTEMA",
        creadoEn = Instant.parse("2024-01-01T00:00:00Z"),
    )
}
