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
 * Tests de detección de tipo de movimiento en PopularCsvParser.
 *
 * Formato real del Popular (UTF-8, separador coma):
 *   Fecha Posteo, Descripción Corta, Monto Transacción, Balance, No. Referencia, No. Serial, Descripción
 *
 * La dirección del movimiento se determina por "Descripción Corta":
 *   - Contiene "Crédito" → INGRESO
 *   - Contiene "Débito ATM" o Descripción larga contiene "RET DE CHK" → RETIRO_ATM
 *   - Contiene "Débito" → GASTO/TRANSFERENCIA/COMISION según Descripción larga
 *   - Vacío → COMISION (impuestos DGII, cargos)
 */
class PopularTipoTransaccionTest {

    private lateinit var parser: PopularCsvParser

    @Before
    fun setUp() { parser = PopularCsvParser() }

    private fun TipoMovimiento.esIngreso() = this == TipoMovimiento.INGRESO || this == TipoMovimiento.CASHBACK
    private fun TipoMovimiento.esGasto()  = !esIngreso()

    // ─── Clasificación por Descripción Corta ─────────────────────────────────

    @Test
    fun `fila con Credito Transferencia en descCorta se clasifica como INGRESO`() = runTest {
        val csv = cabecera() + fila("15/01/2024", "Crédito Transferencia", "5000.00", "25000.00", "REF001", "", "MB desde 841207657 TEST PERSONA")
        val exito = parsear(csv)
        assertEquals(1, exito.estado.movimientos.size)
        assertTrue("Debe ser INGRESO", exito.estado.movimientos[0].tipo.esIngreso())
        assertEquals(BigDecimal("5000.00"), exito.estado.movimientos[0].monto)
    }

    @Test
    fun `fila con Debito Cuenta en descCorta se clasifica como GASTO`() = runTest {
        val csv = cabecera() + fila("15/01/2024", "Débito Cuenta", "2500.00", "22500.00", "REF002", "", "SUPERMERCADO NACIONAL AGORA")
        val exito = parsear(csv)
        assertEquals(1, exito.estado.movimientos.size)
        assertTrue("Debe ser GASTO", exito.estado.movimientos[0].tipo.esGasto())
        assertEquals(BigDecimal("2500.00"), exito.estado.movimientos[0].monto)
    }

    @Test
    fun `fila con Debito ATM en descCorta se clasifica como RETIRO_ATM`() = runTest {
        val csv = cabecera() + fila("20/01/2024", "Débito ATM", "5000.00", "20000.00", "REF003", "", "BANCO POPULAR OFICINA PIANTINI RET DE CHK BPD1332")
        val exito = parsear(csv)
        assertEquals(1, exito.estado.movimientos.size)
        assertEquals(TipoMovimiento.RETIRO_ATM, exito.estado.movimientos[0].tipo)
    }

    @Test
    fun `fila con descCorta vacia y desc DGII se clasifica como COMISION`() = runTest {
        val csv = cabecera() + fila("17/01/2024", "", "5.93", "59.30", "", "", "PAGO IMPUESTO 0.15 DGII 2 TRANS POR 3950.00")
        val exito = parsear(csv)
        assertEquals(1, exito.estado.movimientos.size)
        assertEquals(TipoMovimiento.COMISION, exito.estado.movimientos[0].tipo)
    }

    @Test
    fun `fila Debito con LBTR en desc larga se clasifica como TRANSFERENCIA`() = runTest {
        val csv = cabecera() + fila("20/01/2024", "Débito Cuenta", "15000.00", "13000.00", "REF004", "", "LBTR VIA LBTR 9607562780 BANCO DE RESERVAS CAS2604017663")
        val exito = parsear(csv)
        assertEquals(TipoMovimiento.TRANSFERENCIA, exito.estado.movimientos[0].tipo)
    }

    @Test
    fun `fila Debito con MB a en desc larga se clasifica como TRANSFERENCIA`() = runTest {
        val csv = cabecera() + fila("21/01/2024", "Débito Cuenta", "2300.00", "5000.00", "REF005", "", "MB a 0824328827 CRISTIAN SANCHEZ")
        val exito = parsear(csv)
        assertEquals(TipoMovimiento.TRANSFERENCIA, exito.estado.movimientos[0].tipo)
    }

    @Test
    fun `fila con comisiones LBTR en desc se clasifica como COMISION`() = runTest {
        val csv = cabecera() + fila("20/01/2024", "Débito Cuenta", "100.00", "72600.00", "REF006", "", "EXI COMISIONES LBTR NUM E000073 RD 3000.00")
        val exito = parsear(csv)
        assertEquals(TipoMovimiento.COMISION, exito.estado.movimientos[0].tipo)
    }

    // ─── Mezcla de débitos y créditos ─────────────────────────────────────────

    @Test
    fun `CSV con mezcla de debitos y creditos los clasifica correctamente`() = runTest {
        val csv = cabecera() +
            fila("01/01/2024", "Débito ATM",          "3000.00", "47000.00", "REF010", "", "BANCO POPULAR RET DE CHK BPD1332") +
            fila("02/01/2024", "Crédito Transferencia","45000.00","92000.00", "REF011", "", "MB desde 841207657 EMPRESA XYZ") +
            fila("03/01/2024", "Débito Cuenta",        "1800.00", "90200.00", "REF012", "", "GASOLINERA TEXACO ARROYO HONDO") +
            fila("04/01/2024", "Crédito Transferencia","10000.00","100200.00","REF013", "", "MB desde 852463967 MIKE K THER") +
            fila("05/01/2024", "Débito Cuenta",        "4500.00", "95700.00", "REF014", "", "PAG EDESUR 9191116 58221105")

        val exito = parsear(csv)
        assertEquals("Deben ser 5 movimientos", 5, exito.estado.movimientos.size)
        assertEquals("Deben ser 3 gastos", 3, exito.estado.movimientos.count { it.tipo.esGasto() })
        assertEquals("Deben ser 2 ingresos", 2, exito.estado.movimientos.count { it.tipo.esIngreso() })
    }

    // ─── Manejo de montos ─────────────────────────────────────────────────────

    @Test
    fun `monto con comas de miles se parsea correctamente`() = runTest {
        val csv = cabecera() + "10/01/2024,\"Crédito\",\"1,500.00\",\"51,500.00\",,0,DEPOSITO CHEQUE Y EFECTIVO\n"
        val exito = parsear(csv)
        assertEquals(1, exito.estado.movimientos.size)
        assertTrue(exito.estado.movimientos[0].tipo.esIngreso())
        assertEquals(BigDecimal("1500.00"), exito.estado.movimientos[0].monto)
    }

    @Test
    fun `fila con monto vacio es descartada`() = runTest {
        val csv = cabecera() +
            fila("15/01/2024", "Débito Cuenta", "500.00", "9500.00", "REF030", "", "CONSUMO POS") +
            "16/01/2024,Débito Cuenta,,9500.00,REF031,,FILA SIN MONTO\n" +
            fila("17/01/2024", "Crédito Transferencia", "1000.00", "10500.00", "REF032", "", "MB desde 123456 PERSONA")

        val exito = parsear(csv)
        assertEquals("Solo deben parsearse 2 movimientos válidos", 2, exito.estado.movimientos.size)
        assertTrue("Debe haber advertencias por filas ignoradas", exito.report.warnings.isNotEmpty())
    }

    @Test
    fun `fila con monto cero es descartada`() = runTest {
        val csv = cabecera() + fila("15/01/2024", "Débito Cuenta", "0.00", "10000.00", "", "", "FILA CON CERO")
        val exito = parsear(csv)
        assertEquals("Fila con monto 0.00 debe ser descartada", 0, exito.estado.movimientos.size)
    }

    // ─── Rango de fechas ──────────────────────────────────────────────────────

    @Test
    fun `periodo inicio antes que periodo fin`() = runTest {
        val csv = cabecera() +
            fila("01/01/2024", "Débito Cuenta",        "100.00",  "900.00",  "REF060", "", "PAGO A") +
            fila("31/01/2024", "Crédito Transferencia","1000.00", "1800.00", "REF061", "", "MB desde 123456 PERSONA")

        val exito = parsear(csv)
        val inicio = exito.estado.fechaInicio
        val fin = exito.estado.fechaFin
        if (inicio != null && fin != null) {
            assertTrue("fechaInicio debe ser antes que fechaFin", !inicio.isAfter(fin))
        }
    }

    // ─── Normalización de descripciones ──────────────────────────────────────

    @Test
    fun `credito con MB desde produce descripcion TRANSFERENCIA RECIBIDA`() = runTest {
        val csv = cabecera() + fila("15/01/2024", "Crédito Transferencia", "8000.00", "18000.00", "REF093", "", "MB desde 841207657 TEST PERSONA")
        val exito = parsear(csv)
        assertEquals("TRANSFERENCIA RECIBIDA", exito.estado.movimientos[0].descripcionCorta)
    }

    @Test
    fun `debito con MB a produce descripcion TRANSFERENCIA ENVIADA`() = runTest {
        val csv = cabecera() + fila("15/01/2024", "Débito Cuenta", "5000.00", "5000.00", "REF092", "", "MB a 0852680222 JUNIOR MARTE F")
        val exito = parsear(csv)
        assertEquals("TRANSFERENCIA ENVIADA", exito.estado.movimientos[0].descripcionCorta)
    }

    @Test
    fun `debito ATM produce descripcion RETIRO ATM`() = runTest {
        val csv = cabecera() + fila("15/01/2024", "Débito ATM", "2000.00", "8000.00", "REF091", "", "BANCO POPULAR OFICINA PIANTINI RET DE CHK BPD1332")
        val exito = parsear(csv)
        assertEquals("RETIRO ATM", exito.estado.movimientos[0].descripcionCorta)
    }

    @Test
    fun `DGII tax produce descripcion IMPUESTO DGII`() = runTest {
        val csv = cabecera() + fila("17/01/2024", "", "5.93", "59.30", "", "", "PAGO IMPUESTO 0.15 DGII 2 TRANS POR 3950.00")
        val exito = parsear(csv)
        assertEquals("IMPUESTO DGII", exito.estado.movimientos[0].descripcionCorta)
    }

    @Test
    fun `deposito cheque produce descripcion DEPOSITO`() = runTest {
        val csv = cabecera() + fila("01/05/2024", "Crédito", "45972.79", "52649.06", "", "", "DEPOSITO CHEQUE Y EFECTIVO")
        val exito = parsear(csv)
        assertEquals("DEPOSITO", exito.estado.movimientos[0].descripcionCorta)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun cabecera(): String =
        "Banco Popular Dominicano\n" +
        "Cuenta: 000000000837542000\n" +
        "\n" +
        "Fecha Posteo,Descripción Corta,Monto Transacción,Balance ,No. Referencia,No. Serial,Descripción\n"

    /** Fila con las 7 columnas del formato real del Popular. */
    private fun fila(
        fecha: String,
        descCorta: String,
        monto: String,
        balance: String,
        ref: String,
        serial: String,
        desc: String,
    ): String = "$fecha,$descCorta,$monto,$balance,$ref,$serial,$desc\n"

    private suspend fun parsear(csv: String): ParseResult.Success {
        val bytes = csv.toByteArray(Charsets.UTF_8)
        val archivo = ArchivoEntrada(
            nombre = "popular_test.csv",
            extension = "csv",
            tamanioBytes = bytes.size.toLong(),
            bytes = bytes,
            mimeType = "text/csv",
        )
        val resultado = parser.parse(
            ImportRequest(
                uidUsuario = "uid_test",
                bancoCodigo = "POPULAR",
                productoTipo = ProductoTipo.CUENTA,
                formato = FileFormat.CSV,
                archivo = archivo,
            )
        )
        assertTrue("Parseo debe retornar Success, fue: $resultado", resultado is ParseResult.Success)
        return resultado as ParseResult.Success
    }
}
