package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Tests unitarios de CategorizadorTransaccion.
 *
 * Cubre:
 *   - Clasificación positiva por cada TipoMatch
 *   - Clasificación negativa (no match → null)
 *   - Case insensitive (mayúsculas/minúsculas/acentos)
 *   - Descripción vacía o en blanco → null, sin excepción
 *   - Prioridad: regla de mayor prioridad gana cuando varias hacen match
 *   - Reglas inactivas son ignoradas
 *   - Categorías DGII y de gasto típicas del dominio RD
 *   - Variaciones por banco: BanReservas vs Popular vs Cibao
 */
class CategorizadorTransaccionTest {

    private lateinit var categorizador: CategorizadorTransaccion

    // IDs de categoría usados en estos tests
    private val CAT_ALIMENTACION = "cat_alimentacion"
    private val CAT_GASOLINA = "cat_gasolina"
    private val CAT_TRANSFERENCIA = "cat_transferencia"
    private val CAT_NOMINA = "cat_nomina"
    private val CAT_IMPUESTO = "cat_impuesto_dgii"
    private val CAT_ATM = "cat_retiro_atm"
    private val CAT_COMISION = "cat_comision_bancaria"
    private val CAT_SERVICIOS = "cat_servicios"
    private val CAT_RESTAURANTE = "cat_restaurante"

    @Before
    fun setUp() {
        // Reglas base que simulan el catálogo del sistema
        val reglas = listOf(
            regla("r01", "SUPERMERCADO", TipoMatch.CONTIENE, CAT_ALIMENTACION, prioridad = 10),
            regla("r02", "COLMADO", TipoMatch.CONTIENE, CAT_ALIMENTACION, prioridad = 9),
            regla("r03", "GASOLINERA", TipoMatch.CONTIENE, CAT_GASOLINA, prioridad = 10),
            regla("r04", "GASOLINA", TipoMatch.CONTIENE, CAT_GASOLINA, prioridad = 9),
            regla("r05", "TRANSFERENCIA", TipoMatch.CONTIENE, CAT_TRANSFERENCIA, prioridad = 8),
            regla("r06", "NOMINA", TipoMatch.CONTIENE, CAT_NOMINA, prioridad = 10),
            regla("r07", "DGII", TipoMatch.CONTIENE, CAT_IMPUESTO, prioridad = 15),
            regla("r08", "COBRO IMP", TipoMatch.EMPIEZA_CON, CAT_IMPUESTO, prioridad = 14),
            regla("r09", "RETIRO ATM", TipoMatch.CONTIENE, CAT_ATM, prioridad = 10),
            regla("r10", "COMISION", TipoMatch.CONTIENE, CAT_COMISION, prioridad = 7),
            regla("r11", "PAGO SERVICIO", TipoMatch.EXACTO, CAT_SERVICIOS, prioridad = 10),
            regla("r12", "RESTAURANTE", TipoMatch.CONTIENE, CAT_RESTAURANTE, prioridad = 10),
            regla("r13", "ACH", TipoMatch.CONTIENE, CAT_TRANSFERENCIA, prioridad = 6),
        )
        categorizador = CategorizadorTransaccion(reglas)
    }

    // ─── Casos positivos básicos ──────────────────────────────────────────────

    @Test
    fun `dado descripcion con SUPERMERCADO entonces categoria es alimentacion`() {
        val resultado = categorizador.clasificar("SUPERMERCADO NACIONAL")
        assertEquals(CAT_ALIMENTACION, resultado)
    }

    @Test
    fun `dado descripcion con GASOLINERA entonces categoria es gasolina`() {
        val resultado = categorizador.clasificar("GASOLINERA TEXACO LA CIENEGA")
        assertEquals(CAT_GASOLINA, resultado)
    }

    @Test
    fun `dado descripcion con TRANSFERENCIA entonces categoria es transferencia`() {
        val resultado = categorizador.clasificar("TRANSFERENCIA SALIENTE BPRD")
        assertEquals(CAT_TRANSFERENCIA, resultado)
    }

    @Test
    fun `dado descripcion con NOMINA entonces categoria es nomina`() {
        val resultado = categorizador.clasificar("NOMINAS ACH EMPRESA XYZ")
        assertEquals(CAT_NOMINA, resultado)
    }

    @Test
    fun `dado descripcion con DGII entonces categoria es impuesto`() {
        val resultado = categorizador.clasificar("COBRO DGII 0.15 POR CIENTO")
        assertEquals(CAT_IMPUESTO, resultado)
    }

    @Test
    fun `dado descripcion con RETIRO ATM entonces categoria es atm`() {
        val resultado = categorizador.clasificar("RETIRO ATM CALLE DEL SOL")
        assertEquals(CAT_ATM, resultado)
    }

    @Test
    fun `dado descripcion PAGO SERVICIO exacto entonces categoria es servicios`() {
        val resultado = categorizador.clasificar("PAGO SERVICIO")
        assertEquals(CAT_SERVICIOS, resultado)
    }

    // ─── Casos negativos ─────────────────────────────────────────────────────

    @Test
    fun `dado descripcion sin keywords entonces retorna null`() {
        val resultado = categorizador.clasificar("PAGO CON TARJETA 12345")
        assertNull("Descripcion sin keyword conocida debe retornar null", resultado)
    }

    @Test
    fun `dado descripcion que empieza igual pero no es exacto para EXACTO entonces no hace match`() {
        // "PAGO SERVICIO EDESUR" no es exactamente "PAGO SERVICIO"
        val resultado = categorizador.clasificar("PAGO SERVICIO EDESUR")
        // La regla EXACTO no aplica, pero no hay otra regla para este patron
        assertNull("PAGO SERVICIO EDESUR no debe matchear EXACTO 'PAGO SERVICIO'", resultado)
    }

    // ─── Case insensitive y acentos ──────────────────────────────────────────

    @Test
    fun `clasificar es case insensitive - minusculas`() {
        assertEquals(CAT_ALIMENTACION, categorizador.clasificar("supermercado nacional"))
    }

    @Test
    fun `clasificar es case insensitive - mixto`() {
        assertEquals(CAT_GASOLINA, categorizador.clasificar("Gasolinera Torres"))
    }

    @Test
    fun `clasificar normaliza acentos - descripcion con acento`() {
        // "Nómina" con tilde debe matchear la regla "NOMINA"
        assertEquals(CAT_NOMINA, categorizador.clasificar("Nómina mensual empresa"))
    }

    @Test
    fun `clasificar normaliza acentos - descripcion con e acento`() {
        // "Transferéncia" (forma incorrecta pero posible en texto OCR) debe matchear
        assertEquals(CAT_TRANSFERENCIA, categorizador.clasificar("Transféréncia bancaria"))
    }

    // ─── Descripción vacía/nula ───────────────────────────────────────────────

    @Test
    fun `descripcion vacia retorna null sin excepcion`() {
        val resultado = categorizador.clasificar("")
        assertNull(resultado)
    }

    @Test
    fun `descripcion con solo espacios retorna null sin excepcion`() {
        val resultado = categorizador.clasificar("   ")
        assertNull(resultado)
    }

    // ─── Prioridad ────────────────────────────────────────────────────────────

    @Test
    fun `dado dos reglas que hacen match la de mayor prioridad gana`() {
        // "COBRO IMP" hace match en r08 (prioridad=14, EMPIEZA_CON) Y "DGII" en r07 (prioridad=15)
        // "COBRO IMP DGII 0.15" — r07 tiene prioridad 15, r08 tiene 14, gana r07 (DGII)
        val resultado = categorizador.clasificar("COBRO IMP DGII 0.15")
        assertEquals("La regla DGII (prioridad 15) debe ganar sobre COBRO IMP (prioridad 14)",
            CAT_IMPUESTO, resultado)
    }

    @Test
    fun `dado descripcion que matchea NOMINA y TRANSFERENCIA gana NOMINA por prioridad`() {
        // Regla NOMINA prioridad=10, TRANSFERENCIA prioridad=8
        // "NOMINAS ACH TRANSFERENCIA" contiene ambas keywords
        val resultado = categorizador.clasificar("NOMINAS ACH TRANSFERENCIA EMPRESA")
        assertEquals("NOMINA (prioridad 10) debe ganar sobre TRANSFERENCIA (prioridad 8)",
            CAT_NOMINA, resultado)
    }

    // ─── Reglas inactivas ────────────────────────────────────────────────────

    @Test
    fun `regla inactiva es ignorada`() {
        val reglaInactiva = regla("rInactiva", "GASOLINERA", TipoMatch.CONTIENE, "cat_falsa",
            prioridad = 99, activa = false)
        val categorizadorConInactiva = CategorizadorTransaccion(
            listOf(reglaInactiva) + listOf(regla("r03", "GASOLINERA", TipoMatch.CONTIENE, CAT_GASOLINA, 10))
        )
        val resultado = categorizadorConInactiva.clasificar("GASOLINERA TEXTO")
        assertEquals("La regla inactiva no debe aplicarse aunque tenga prioridad mayor",
            CAT_GASOLINA, resultado)
    }

    @Test
    fun `lista de reglas vacia siempre retorna null`() {
        val categorizadorVacio = CategorizadorTransaccion(emptyList())
        assertNull(categorizadorVacio.clasificar("SUPERMERCADO NACIONAL"))
    }

    // ─── TipoMatch.EMPIEZA_CON ────────────────────────────────────────────────

    @Test
    fun `EMPIEZA_CON hace match cuando descripcion empieza con patron`() {
        // "COBRO IMP..." empieza con "COBRO IMP"
        assertEquals(CAT_IMPUESTO, categorizador.clasificar("COBRO IMP 0.15 REFERENCIA 99"))
    }

    @Test
    fun `EMPIEZA_CON no hace match cuando patron esta en el medio`() {
        // "IMPUESTO COBRO IMP" — "COBRO IMP" no está al inicio
        // La regla r08 EMPIEZA_CON no aplica, pero r07 DGII tampoco está
        // Solo aplica si la descripcion contiene DGII a través de otra regla
        val categorizadorSoloEmpiezaCon = CategorizadorTransaccion(
            listOf(regla("rEC", "COBRO IMP", TipoMatch.EMPIEZA_CON, CAT_IMPUESTO, prioridad = 10))
        )
        val resultado = categorizadorSoloEmpiezaCon.clasificar("IMPUESTO COBRO IMP BANCO")
        assertNull("EMPIEZA_CON no debe matchear cuando el patron esta en el medio", resultado)
    }

    // ─── TipoMatch.REGEX ─────────────────────────────────────────────────────

    @Test
    fun `REGEX hace match con expresion regular valida`() {
        val reglaRegex = regla("rRE", """\bSUCURSAL\s+\d+\b""", TipoMatch.REGEX, CAT_TRANSFERENCIA, prioridad = 5)
        val cat = CategorizadorTransaccion(listOf(reglaRegex))
        assertEquals(CAT_TRANSFERENCIA, cat.clasificar("SUCURSAL 302 RETIRO"))
    }

    @Test
    fun `REGEX invalido no lanza excepcion - retorna null`() {
        val reglaRegexInvalida = regla("rREinv", """[[[INVALIDO""", TipoMatch.REGEX, CAT_ATM, prioridad = 5)
        val cat = CategorizadorTransaccion(listOf(reglaRegexInvalida))
        // No debe lanzar excepción — debe retornar null silenciosamente
        assertNull(cat.clasificar("RETIRO ATM TEXTO"))
    }

    // ─── Variaciones por banco ────────────────────────────────────────────────

    @Test
    fun `BanReservas - descripcion con prefijo de sucursal hace match`() {
        // BanReservas: "SUC SANTIAGO - SUPERMERCADO NACIONAL"
        val resultado = categorizador.clasificar("SUC SANTIAGO - SUPERMERCADO NACIONAL")
        assertEquals("BanReservas con prefijo de sucursal debe matchear SUPERMERCADO",
            CAT_ALIMENTACION, resultado)
    }

    @Test
    fun `Popular - descripcion con pipe separador hace match`() {
        // Banco Popular: "CONSUMO POS|SUPERMERCADO CORONA"
        val resultado = categorizador.clasificar("CONSUMO POS|SUPERMERCADO CORONA")
        assertEquals("Popular con separador pipe debe matchear SUPERMERCADO",
            CAT_ALIMENTACION, resultado)
    }

    @Test
    fun `Qik - descripcion de pago P2P no matchea categoria comercio`() {
        // Qik P2P: "PAGO A JUAN PEREZ QIK" — no debe clasificarse como supermercado
        val resultado = categorizador.clasificar("PAGO A JUAN PEREZ QIK")
        assertNull("Transferencia P2P de Qik no debe clasificarse como supermercado", resultado)
    }

    @Test
    fun `Cibao - movimiento de gasolinera hace match aunque descripcion sea corta`() {
        // Asociación Cibao: descripciones cortas tipo "GASOLINERA 304"
        val resultado = categorizador.clasificar("GASOLINERA 304")
        assertEquals("Cibao descripcion corta de gasolinera debe matchear",
            CAT_GASOLINA, resultado)
    }

    @Test
    fun `COLMADO hace match como categoria alimentacion`() {
        val resultado = categorizador.clasificar("COLMADO DON RAFAEL")
        assertEquals(CAT_ALIMENTACION, resultado)
    }

    @Test
    fun `ACH hace match como transferencia aunque tenga menor prioridad`() {
        val resultado = categorizador.clasificar("DEPOSITO ACH EMPRESA SA")
        assertEquals(CAT_TRANSFERENCIA, resultado)
    }

    // ─── clasificarConRegla ───────────────────────────────────────────────────

    @Test
    fun `clasificarConRegla retorna categoria y regla que hizo match`() {
        val (categoriaId, reglaMatch) = categorizador.clasificarConRegla("SUPERMERCADO LA SIRENA")
        assertEquals(CAT_ALIMENTACION, categoriaId)
        assertNotNull("La regla que hizo match no debe ser null", reglaMatch)
        assertEquals("r01", reglaMatch!!.id)
    }

    @Test
    fun `clasificarConRegla con descripcion sin match retorna null null`() {
        val (categoriaId, reglaMatch) = categorizador.clasificarConRegla("TEXTO SIN KEYWORD")
        assertNull(categoriaId)
        assertNull(reglaMatch)
    }

    // ─── Helper factory ───────────────────────────────────────────────────────

    private fun regla(
        id: String,
        patron: String,
        tipoMatch: TipoMatch,
        categoriaId: String,
        prioridad: Int = 10,
        activa: Boolean = true,
    ) = ReglaCategoria(
        id = id,
        uidUsuario = null,          // regla del SISTEMA
        patron = patron,
        tipoMatch = tipoMatch,
        categoriaId = categoriaId,
        prioridad = prioridad,
        confianza = 0,
        activa = activa,
        creadoPor = "SISTEMA",
        creadoEn = Instant.parse("2024-01-01T00:00:00Z"),
    )
}
