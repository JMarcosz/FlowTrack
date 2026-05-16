package com.example.flowtrack.data.parsers.popular

import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests de detección GASTO vs INGRESO en PopularCsvParser.
 *
 * Ejecutar:
 *   .\gradlew.bat testDebugUnitTest --tests "com.example.flowtrack.data.parsers.popular.PopularTipoTransaccionTest"
 */
class PopularTipoTransaccionTest {

    private lateinit var parser: PopularCsvParser

    @Before
    fun setUp() {
        parser = PopularCsvParser()
    }

    // ─── Helpers de tipo ─────────────────────────────────────────────────────

    private fun TipoMovimiento.esIngreso() =
        this == TipoMovimiento.INGRESO || this == TipoMovimiento.CASHBACK

    private fun TipoMovimiento.esGasto() = !esIngreso()

    // ─── Tests de detección GASTO / INGRESO ──────────────────────────────────

    @Test
    fun `fila con credito positivo y debito vacio se clasifica como INGRESO`() = runTest {
        val csv = csvConCabecera() + filaCredito("15/01/2024", "TRANSFERENCIA RECIBIDA", "REF001", "", "5000.00", "25000.00")
        val resultado = parsear(csv)
        assertTrue("Debe retornar Success", resultado is ParseResult.Success)
        val exito = resultado as ParseResult.Success
        assertEquals("Debe haber exactamente 1 movimiento", 1, exito.estado.movimientos.size)
        assertTrue("El movimiento debe ser INGRESO", exito.estado.movimientos[0].tipo.esIngreso())
        assertEquals("El monto debe ser 5000.00", BigDecimal("5000.00"), exito.estado.movimientos[0].monto)
    }

    @Test
    fun `fila con debito positivo y credito vacio se clasifica como GASTO`() = runTest {
        val csv = csvConCabecera() + filaDebito("15/01/2024", "CONSUMO TARJETA SUPERMERCADO", "REF002", "2500.00", "", "22500.00")
        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals("Debe haber exactamente 1 movimiento", 1, exito.estado.movimientos.size)
        assertTrue("El movimiento debe ser GASTO", exito.estado.movimientos[0].tipo.esGasto())
        assertEquals("El monto debe ser 2500.00", BigDecimal("2500.00"), exito.estado.movimientos[0].monto)
    }

    @Test
    fun `CSV con mezcla de debitos y creditos los clasifica correctamente`() = runTest {
        val csv = csvConCabecera() +
            filaDebito("01/01/2024", "RETIRO ATM PLAZA CENTRAL", "REF010", "3000.00", "", "47000.00") +
            filaCredito("02/01/2024", "NOMINA EMPRESA XYZ SA", "REF011", "", "45000.00", "92000.00") +
            filaDebito("03/01/2024", "CONSUMO TARJETA GASOLINERA", "REF012", "1800.00", "", "90200.00") +
            filaCredito("04/01/2024", "TRANSFERENCIA RECIBIDA ACH", "REF013", "", "10000.00", "100200.00") +
            filaDebito("05/01/2024", "PAGO SERVICIO EDESUR", "REF014", "4500.00", "", "95700.00")

        val resultado = parsear(csv)
        assertTrue(resultado is ParseResult.Success)
        val exito = resultado as ParseResult.Success

        assertEquals("Debe haber 5 movimientos en total", 5, exito.estado.movimientos.size)

        val gastos = exito.estado.movimientos.filter { it.tipo.esGasto() }
        val ingresos = exito.estado.movimientos.filter { it.tipo.esIngreso() }

        assertEquals("Debe haber 3 gastos", 3, gastos.size)
        assertEquals("Debe haber 2 ingresos", 2, ingresos.size)
    }

    @Test
    fun `credito con formato de monto con comas se parsea correctamente`() = runTest {
        val csv = csvConCabecera() + "10/01/2024,DEPOSITO EN EFECTIVO,REF020,,\"1,500.00\",\"51,500.00\"\n"
        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals(1, exito.estado.movimientos.size)
        assertTrue(exito.estado.movimientos[0].tipo.esIngreso())
        assertEquals(BigDecimal("1500.00"), exito.estado.movimientos[0].monto)
    }

    @Test
    fun `debito con formato de monto con comas se parsea correctamente`() = runTest {
        val csv = csvConCabecera() + "10/01/2024,TRANSFERENCIA ENVIADA,REF021,\"12,750.50\",,\"38,749.50\"\n"
        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals(1, exito.estado.movimientos.size)
        assertTrue(exito.estado.movimientos[0].tipo.esGasto())
        assertEquals(BigDecimal("12750.50"), exito.estado.movimientos[0].monto)
    }

    @Test
    fun `fila con ambas columnas vacias es descartada sin romper el parseo`() = runTest {
        val csv = csvConCabecera() +
            filaDebito("15/01/2024", "CONSUMO POS", "REF030", "500.00", "", "9500.00") +
            "16/01/2024,FILA INVALIDA SIN MONTO,REF031,,,10000.00\n" +
            filaCredito("17/01/2024", "TRANSFERENCIA RECIBIDA", "REF032", "", "1000.00", "11000.00")

        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals("Solo deben parsearse 2 movimientos validos", 2, exito.estado.movimientos.size)
        assertTrue(
            "Debe haber al menos 1 advertencia por filas ignoradas",
            exito.report.warnings.isNotEmpty(),
        )
    }

    @Test
    fun `fila con credito 0 punto 00 explicito es descartada`() = runTest {
        val csv = csvConCabecera() +
            "15/01/2024,DESCRIPCION CERO,REF040,0.00,0.00,10000.00\n"

        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals("Fila con 0.00 en ambas columnas debe ser descartada", 0, exito.estado.movimientos.size)
    }

    // ─── Tests del resumen de período ─────────────────────────────────────────

    @Test
    fun `resumen del periodo cuenta gastos e ingresos correctamente`() = runTest {
        val csv = csvConCabecera() +
            filaDebito("01/01/2024", "CONSUMO POS", "REF050", "100.00", "", "900.00") +
            filaDebito("02/01/2024", "RETIRO ATM", "REF051", "200.00", "", "700.00") +
            filaCredito("03/01/2024", "NOMINA", "REF052", "", "50000.00", "50700.00")

        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        val movs = exito.estado.movimientos

        val gastos = movs.filter { it.tipo.esGasto() }
        val ingresos = movs.filter { it.tipo.esIngreso() }

        assertEquals("Debe haber 2 gastos", 2, gastos.size)
        assertEquals("Debe haber 1 ingreso", 1, ingresos.size)
        assertEquals(
            "Total gastos debe ser 300.00",
            BigDecimal("300.00"),
            gastos.fold(BigDecimal.ZERO) { acc, m -> acc + m.monto },
        )
        assertEquals(
            "Total ingresos debe ser 50000.00",
            BigDecimal("50000.00"),
            ingresos.fold(BigDecimal.ZERO) { acc, m -> acc + m.monto },
        )
    }

    @Test
    fun `periodo inicio antes que periodo fin`() = runTest {
        val csv = csvConCabecera() +
            filaDebito("01/01/2024", "PAGO A", "REF060", "100.00", "", "900.00") +
            filaCredito("31/01/2024", "DEPOSITO", "REF061", "", "1000.00", "1800.00")

        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        val inicio = exito.estado.fechaInicio
        val fin = exito.estado.fechaFin
        if (inicio != null && fin != null) {
            assertTrue("fechaInicio debe ser antes que fechaFin", !inicio.isAfter(fin))
        }
    }

    // ─── Tests de normalización de descripción ────────────────────────────────

    @Test
    fun `descripcion CONSUMO TARJETA se normaliza a CONSUMO POS`() = runTest {
        val csv = csvConCabecera() + filaDebito("15/01/2024", "CONSUMO TARJETA PLAZA LAMA", "REF090", "800.00", "", "9200.00")
        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals("CONSUMO POS", exito.estado.movimientos[0].descripcionCorta)
    }

    @Test
    fun `descripcion RETIRO ATM se normaliza a RETIRO ATM`() = runTest {
        val csv = csvConCabecera() + filaDebito("15/01/2024", "RETIRO ATM CENTRO COMERCIAL", "REF091", "2000.00", "", "8000.00")
        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals("RETIRO ATM", exito.estado.movimientos[0].descripcionCorta)
    }

    @Test
    fun `descripcion TRANSFERENCIA ENVIADA se normaliza a TRANSFERENCIA SALIENTE`() = runTest {
        val csv = csvConCabecera() + filaDebito("15/01/2024", "TRANSFERENCIA ENVIADA AL 1234", "REF092", "5000.00", "", "5000.00")
        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals("TRANSFERENCIA SALIENTE", exito.estado.movimientos[0].descripcionCorta)
    }

    @Test
    fun `descripcion TRANSFERENCIA RECIBIDA se normaliza a TRANSFERENCIA ENTRANTE`() = runTest {
        val csv = csvConCabecera() + filaCredito("15/01/2024", "TRANSFERENCIA RECIBIDA DE 5678", "REF093", "", "8000.00", "18000.00")
        val resultado = parsear(csv)
        val exito = resultado as ParseResult.Success
        assertEquals("TRANSFERENCIA ENTRANTE", exito.estado.movimientos[0].descripcionCorta)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun csvConCabecera(): String =
        "BANCO POPULAR DOMINICANO\n" +
        "Fecha,Descripcion,Referencia,Debito,Credito,Balance\n"

    private fun filaDebito(fecha: String, desc: String, ref: String, debito: String, credito: String, balance: String) =
        "$fecha,$desc,$ref,$debito,$credito,$balance\n"

    private fun filaCredito(fecha: String, desc: String, ref: String, debito: String, credito: String, balance: String) =
        "$fecha,$desc,$ref,$debito,$credito,$balance\n"

    private suspend fun parsear(csv: String): ParseResult =
        parser.parse(makeRequest(crearArchivo(csv)))

    private fun makeRequest(archivo: ArchivoEntrada) = ImportRequest(
        uidUsuario = "uid_test",
        bancoCodigo = "POPULAR",
        productoTipo = ProductoTipo.CUENTA,
        formato = FileFormat.CSV,
        archivo = archivo,
    )

    private fun crearArchivo(csv: String): ArchivoEntrada {
        val bytes = csv.toByteArray(Charsets.UTF_8)
        return ArchivoEntrada(
            nombre = "popular_test.csv",
            extension = "csv",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "text/csv",
        )
    }
}
