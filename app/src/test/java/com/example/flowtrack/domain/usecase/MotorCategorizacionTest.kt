package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Tests unitarios del MotorCategorizacion.
 *
 * Datos 100% sintéticos — no se usan fixtures reales.
 * Cada test verifica un aspecto aislado de la lógica de categorización.
 */
class MotorCategorizacionTest {

    // ─── Helpers de construcción ──────────────────────────────────────────────

    private fun transaccion(
        descripcionCorta: String,
        descripcionNormalizada: String,
        tipo: TipoTransaccion = TipoTransaccion.DEBITO,
        monto: BigDecimal = BigDecimal("1000.00"),
        categoriaId: String? = null,
    ): Transaccion = Transaccion(
        id = "tx-${descripcionCorta.hashCode()}",
        uidUsuario = "uid-test",
        cuentaId = "cuenta-test",
        bancoCodigo = "BANRESERVAS",
        fecha = Instant.parse("2025-03-15T10:00:00Z"),
        fechaPosteo = null,
        descripcionCorta = descripcionCorta,
        descripcionOriginal = descripcionCorta,
        descripcionNormalizada = descripcionNormalizada,
        monto = monto,
        tipo = tipo,
        moneda = Moneda.DOP,
        balanceDespues = null,
        referencia = null,
        serial = null,
        categoriaId = categoriaId,
        categoriaAutomatica = false,
        cargaId = "carga-test",
        creadoEn = Instant.now(),
    )

    private fun regla(
        patron: String,
        categoriaId: String,
        tipoMatch: TipoMatch = TipoMatch.CONTIENE,
        prioridad: Int = 10,
        uidUsuario: String? = "uid-test",
    ): ReglaCategoria = ReglaCategoria(
        id = "regla-${patron.hashCode()}",
        uidUsuario = uidUsuario,
        patron = patron,
        tipoMatch = tipoMatch,
        categoriaId = categoriaId,
        prioridad = prioridad,
        confianza = 1,
        activa = true,
        creadoPor = uidUsuario ?: "SISTEMA",
        creadoEn = Instant.parse("2025-01-01T00:00:00Z"),
    )

    // ─── 1. Sin reglas — inferencia por descripcionCorta ──────────────────────

    @Test
    fun `sin reglas - CONSUMO POS se infiere como compras`() {
        val tx = transaccion("CONSUMO POS", "CONSUMO POS TIENDA NACIONAL")
        val (catId, esAuto) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("compras", catId)
        assertTrue(esAuto)
    }

    @Test
    fun `sin reglas - RETIRO ATM se infiere como atm`() {
        val tx = transaccion("RETIRO ATM", "RETIRO ATM CENTRO COMERCIAL")
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("atm", catId)
    }

    @Test
    fun `sin reglas - NOMINA credito se infiere como salario`() {
        val tx = transaccion("NOMINA", "NOMINAS ACH EMPRESA XYZ", TipoTransaccion.CREDITO)
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("salario", catId)
    }

    @Test
    fun `sin reglas - TRANSFERENCIA SALIENTE se infiere como transferencia_enviada`() {
        val tx = transaccion("TRANSFERENCIA SALIENTE", "TRANS CREDITO HACIA CUENTA 1234")
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("transferencia_enviada", catId)
    }

    @Test
    fun `sin reglas - TRANSFERENCIA ENTRANTE credito se infiere como transferencia_recibida`() {
        val tx = transaccion("TRANSFERENCIA ENTRANTE", "CR TRANSFERENCIA RECIBIDA", TipoTransaccion.CREDITO)
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("transferencia_recibida", catId)
    }

    @Test
    fun `sin reglas - IMPUESTO DGII se infiere como impuestos`() {
        val tx = transaccion("IMPUESTO DGII", "COBRO IMP DGII 0 15 POR CIENTO")
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("impuestos", catId)
    }

    @Test
    fun `sin reglas - COMISION se infiere como intereses_comisiones`() {
        val tx = transaccion("COMISION ATM", "COMISION MENSUAL ATM")
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("intereses_comisiones", catId)
    }

    @Test
    fun `sin reglas - descripcion desconocida devuelve null`() {
        val tx = transaccion("OPERACION XYZ 9999", "OPERACION XYZ 9999 REFERENCIA 0001")
        val (catId, esAuto) = MotorCategorizacion.categorizar(tx, emptyList())
        assertNull(catId)
        assertFalse(esAuto)
    }

    @Test
    fun `sin reglas - DEPOSITO credito se infiere como deposito`() {
        val tx = transaccion("DEPOSITO", "DEPOSITO EN EFECTIVO SUCURSAL", TipoTransaccion.CREDITO)
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("deposito", catId)
    }

    @Test
    fun `sin reglas - CASHBACK credito se infiere como cashback`() {
        val tx = transaccion("CASHBACK", "CASHBACK TARJETA VISA", TipoTransaccion.CREDITO)
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("cashback", catId)
    }

    // ─── 2. Matching de reglas ─────────────────────────────────────────────────

    @Test
    fun `regla CONTIENE - matchea descripcion parcial`() {
        val tx = transaccion("", "PAGO SUPERMERCADO NACIONAL 12345")
        val reglas = listOf(regla("SUPERMERCADO", "alimentacion"))
        val (catId, esAuto) = MotorCategorizacion.categorizar(tx, reglas)
        assertEquals("alimentacion", catId)
        assertTrue(esAuto)
    }

    @Test
    fun `regla EXACTO - solo matchea descripcion identica`() {
        val tx = transaccion("", "CONSUMO POS EXACTO")
        val reglas = listOf(regla("CONSUMO POS EXACTO", "compras", TipoMatch.EXACTO))
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        assertEquals("compras", catId)
    }

    @Test
    fun `regla EXACTO - no matchea si la descripcion es distinta`() {
        val tx = transaccion("CONSUMO POS", "CONSUMO POS TIENDA ABC 789")
        val reglas = listOf(regla("CONSUMO POS EXACTO", "compras", TipoMatch.EXACTO))
        // La regla exacta no debe coincidir; debe caer en inferencia por descripcionCorta
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        assertEquals("compras", catId)  // inferida por descripcionCorta, no por la regla
    }

    @Test
    fun `regla EMPIEZA_CON - matchea solo si el inicio coincide`() {
        val tx = transaccion("", "NETFLIX SUSCRIPCION MENSUAL")
        val reglas = listOf(regla("NETFLIX", "suscripciones", TipoMatch.EMPIEZA_CON))
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        assertEquals("suscripciones", catId)
    }

    @Test
    fun `regla EMPIEZA_CON - no matchea si el patron esta en el medio`() {
        val tx = transaccion("", "PAGO NETFLIX REFERENCIA 123")
        val reglas = listOf(regla("NETFLIX", "suscripciones", TipoMatch.EMPIEZA_CON))
        // No empieza con NETFLIX — debe caer en inferencia (sin match conocido → null)
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        assertNull(catId)
    }

    @Test
    fun `regla REGEX - matchea patron de expresion regular`() {
        val tx = transaccion("", "UBER 12345 VIAJE CIUDAD")
        val reglas = listOf(regla("UBER\\s\\d+", "transporte", TipoMatch.REGEX))
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        assertEquals("transporte", catId)
    }

    @Test
    fun `regla REGEX invalido - no lanza excepcion y devuelve null`() {
        val tx = transaccion("", "CONSUMO POS 123")
        val reglas = listOf(regla("[regex_invalido(", "compras", TipoMatch.REGEX))
        // El motor debe capturar la excepción y continuar
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        // Cae en inferencia por descripcionCorta → null (descripcionCorta vacía)
        assertNull(catId)
    }

    // ─── 3. Precedencia de reglas ─────────────────────────────────────────────

    @Test
    fun `regla usuario tiene precedencia sobre regla sistema`() {
        val tx = transaccion("", "CONSUMO POS FARMACIA CAROL")
        val reglaUsuario = regla("FARMACIA", "salud", uidUsuario = "uid-test")
        val reglaGlobal  = regla("FARMACIA", "compras", uidUsuario = null)
        val reglas = listOf(reglaGlobal, reglaUsuario)  // orden invertido a propósito
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        assertEquals("salud", catId)  // usuario gana
    }

    @Test
    fun `mayor prioridad gana entre reglas del mismo origen`() {
        val tx = transaccion("", "SUPERMERCADO NACIONAL")
        val reglaLow  = regla("SUPERMERCADO", "compras",    prioridad = 5)
        val reglaHigh = regla("SUPERMERCADO", "alimentacion", prioridad = 20)
        val reglas = listOf(reglaLow, reglaHigh)
        val (catId, _) = MotorCategorizacion.categorizar(tx, reglas)
        assertEquals("alimentacion", catId)  // prioridad 20 gana
    }

    @Test
    fun `regla sistema aplica cuando no hay reglas de usuario`() {
        val tx = transaccion("", "FARMACIA CAROL SUCURSAL 3")
        val reglaGlobal = regla("FARMACIA", "salud", uidUsuario = null)
        val (catId, _) = MotorCategorizacion.categorizar(tx, listOf(reglaGlobal))
        assertEquals("salud", catId)
    }

    // ─── 4. Reglas inactivas ──────────────────────────────────────────────────

    @Test
    fun `regla inactiva no se aplica`() {
        val tx = transaccion("CONSUMO POS", "CONSUMO POS TIENDA ABC")
        val reglaInactiva = regla("TIENDA ABC", "compras").copy(activa = false)
        val (catId, _) = MotorCategorizacion.categorizar(tx, listOf(reglaInactiva))
        // La regla inactiva no aplica; inferencia por descripcionCorta → compras de todas formas
        assertEquals("compras", catId)
    }

    // ─── 5. TipoTransaccion CREDITO vs DEBITO ────────────────────────────────

    @Test
    fun `TRANSFERENCIA ENTRANTE debito no se infiere como transferencia_recibida`() {
        // Mismo texto pero tipo DEBITO — no debe ser "transferencia_recibida"
        val tx = transaccion("TRANSFERENCIA ENTRANTE", "CR TRANSFERENCIA RECIBIDA", TipoTransaccion.DEBITO)
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        // La inferencia para transferencia_recibida requiere CREDITO
        assertNull(catId)
    }

    @Test
    fun `tipo CREDITO y DEBITO producen categorias distintas para mismo texto`() {
        // descripcionCorta debe coincidir con lo que generaría el parser (BanReservas la normaliza a "TRANSFERENCIA PROPIA")
        val txCredito = transaccion("TRANSFERENCIA PROPIA", "CR TRANSFERENCIA PROPIA AHORRO", TipoTransaccion.CREDITO)
        val txDebito  = transaccion("TRANSFERENCIA SALIENTE", "TRANS CREDITO SALIENTE", TipoTransaccion.DEBITO)
        val (catCredito, _) = MotorCategorizacion.categorizar(txCredito, emptyList())
        val (catDebito, _)  = MotorCategorizacion.categorizar(txDebito, emptyList())
        assertEquals("transferencia_recibida", catCredito)
        assertEquals("transferencia_enviada", catDebito)
    }

    // ─── 6. Lote de transacciones ─────────────────────────────────────────────

    @Test
    fun `categorizarLote asigna categorias correctas a lista mixta`() {
        val txs = listOf(
            transaccion("CONSUMO POS",       "CONSUMO POS SUPERMERCADO"),
            transaccion("NOMINA",            "NOMINAS ACH EMPRESA",  TipoTransaccion.CREDITO),
            transaccion("RETIRO ATM",        "RETIRO ATM BANRESERVAS"),
            transaccion("OPERACION RANDOM",  "OPERACION RANDOM 0001"),
        )
        val resultado = MotorCategorizacion.categorizarLote(txs, emptyList())

        assertEquals("compras",  resultado[0].categoriaId)
        assertEquals("salario",  resultado[1].categoriaId)
        assertEquals("atm",      resultado[2].categoriaId)
        assertNull(resultado[3].categoriaId)  // sin match
    }

    @Test
    fun `categorizarLote no sobreescribe categorias ya asignadas`() {
        val txYaCategorizada = transaccion("CONSUMO POS", "CONSUMO POS TIENDA", categoriaId = "suscripciones")
        val resultado = MotorCategorizacion.categorizarLote(listOf(txYaCategorizada), emptyList())
        // La transacción ya tenía categoría — no se debe tocar
        assertEquals("suscripciones", resultado[0].categoriaId)
    }

    @Test
    fun `categorizarLote con regla asigna categoria correcta`() {
        val txs = listOf(
            transaccion("", "NETFLIX SUSCRIPCION"),
            transaccion("", "CONSUMO POS RANDOM"),
        )
        val reglas = listOf(regla("NETFLIX", "suscripciones"))
        val resultado = MotorCategorizacion.categorizarLote(txs, reglas)
        assertEquals("suscripciones", resultado[0].categoriaId)
        assertTrue(resultado[0].categoriaAutomatica)
        // Segundo sin regla pero sin descripcionCorta conocida → null
        assertNull(resultado[1].categoriaId)
    }

    // ─── 7. Normalización en matching ────────────────────────────────────────

    @Test
    fun `matching es insensible a mayusculas y acentos`() {
        val tx = transaccion("", "FARMACIA CAROL SAN ISIDRO")
        // Patrón con acento — debe matchear igual
        val reglaConAcento = regla("farmacía", "salud")
        val (catId, _) = MotorCategorizacion.categorizar(tx, listOf(reglaConAcento))
        assertEquals("salud", catId)
    }

    @Test
    fun `matching CONTIENE con patron largo solo matchea si esta completo`() {
        val tx = transaccion("", "PAGO NETO EDENORTE")
        val regla = regla("CLARO RD", "servicios")
        val (catId, _) = MotorCategorizacion.categorizar(tx, listOf(regla))
        // "CLARO RD" no está en "PAGO NETO EDENORTE"
        assertNull(catId)
    }

    // ─── 8. Tests de matchea() ────────────────────────────────────────────────

    @Test
    fun `matchea CONTIENE - verdadero cuando patron esta en descripcion`() {
        val regla = regla("UBER", "transporte", TipoMatch.CONTIENE)
        assertTrue(MotorCategorizacion.matchea("VIAJE UBER BOGOTA", regla))
    }

    @Test
    fun `matchea CONTIENE - falso cuando patron no esta en descripcion`() {
        val regla = regla("UBER", "transporte", TipoMatch.CONTIENE)
        assertFalse(MotorCategorizacion.matchea("VIAJE TAXI 1234", regla))
    }

    @Test
    fun `matchea EXACTO - verdadero solo con texto identico`() {
        val regla = regla("PAGO CLARO", "servicios", TipoMatch.EXACTO)
        assertTrue(MotorCategorizacion.matchea("PAGO CLARO", regla))
        assertFalse(MotorCategorizacion.matchea("PAGO CLARO MENSUAL", regla))
    }

    @Test
    fun `matchea EMPIEZA_CON - verdadero si descripcion empieza con patron`() {
        val regla = regla("SALARIO", "salario", TipoMatch.EMPIEZA_CON)
        assertTrue(MotorCategorizacion.matchea("SALARIO EMPRESA NACIONAL SA", regla))
        assertFalse(MotorCategorizacion.matchea("PAGO SALARIO EMPRESA", regla))
    }

    @Test
    fun `matchea REGEX - verdadero para patron valido`() {
        val regla = regla("""\bUBER\b""", "transporte", TipoMatch.REGEX)
        assertTrue(MotorCategorizacion.matchea("PAGO UBER BOGOTA", regla))
    }

    // ─── 9. BigDecimal en montos ──────────────────────────────────────────────

    @Test
    fun `montos con BigDecimal no pierden precision en categorizar`() {
        val montoExacto = BigDecimal("12345.67")
        val tx = transaccion("CONSUMO POS", "CONSUMO POS SUPERMERCADO", monto = montoExacto)
        val (catId, _) = MotorCategorizacion.categorizar(tx, emptyList())
        assertEquals("compras", catId)
        // El monto no debe haber sido modificado (BigDecimal inmutable)
        assertEquals(montoExacto, tx.monto)
    }

    // ─── 10. inferirPorDescripcionCorta público ───────────────────────────────

    @Test
    fun `inferir PAGO SERVICIO devuelve servicios`() {
        val cat = MotorCategorizacion.inferirPorDescripcionCorta("PAGO SERVICIO", TipoTransaccion.DEBITO)
        assertEquals("servicios", cat)
    }

    @Test
    fun `inferir TRANSFERENCIA LBTR devuelve transferencia_enviada`() {
        val cat = MotorCategorizacion.inferirPorDescripcionCorta("TRANSFERENCIA LBTR", TipoTransaccion.DEBITO)
        assertEquals("transferencia_enviada", cat)
    }

    @Test
    fun `inferir TRANSFERENCIA ACH devuelve transferencia_enviada`() {
        val cat = MotorCategorizacion.inferirPorDescripcionCorta("TRANSFERENCIA ACH", TipoTransaccion.DEBITO)
        assertEquals("transferencia_enviada", cat)
    }

    @Test
    fun `inferir texto sin coincidencia devuelve null`() {
        val cat = MotorCategorizacion.inferirPorDescripcionCorta("TEXTO DESCONOCIDO XYZ", TipoTransaccion.DEBITO)
        assertNull(cat)
    }

    @Test
    fun `inferir RETIRO SUCURSAL devuelve atm`() {
        val cat = MotorCategorizacion.inferirPorDescripcionCorta("RETIRO SUCURSAL", TipoTransaccion.DEBITO)
        assertEquals("atm", cat)
    }
}
