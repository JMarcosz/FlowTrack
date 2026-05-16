package com.example.flowtrack.data.parsers.cibao

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Test

class CibaoXlsDiagnosticTest {

    @Test
    fun `diagnostico estructura cibao xls`() {
        val f = java.io.File("../docs/03-fixtures/cibao.xls")
        if (!f.exists()) { println("⚠️ Fixture no disponible"); return }

        val wb = runCatching { HSSFWorkbook(f.inputStream()) }.getOrNull()
            ?: run { println("❌ No se pudo abrir"); return }

        val hoja = wb.getSheetAt(0)
        println("Hoja: '${wb.getSheetName(0)}', total filas: ${hoja.lastRowNum}")

        // Imprimir etiquetas de las primeras 20 filas (no datos sensibles)
        for (i in 0..minOf(20, hoja.lastRowNum)) {
            val row = hoja.getRow(i) ?: continue
            val celdas = (0 until row.lastCellNum).mapIndexed { idx, _ ->
                val cell = row.getCell(idx)
                "[$idx]='${cell?.toString()?.take(25) ?: ""}'"
            }
            if (celdas.isNotEmpty()) println("  Fila $i: ${celdas.joinToString(", ")}")
        }
        wb.close()
    }

    @Test
    fun `diagnostico popular csv lineas con debito credito`() {
        val f = java.io.File("../docs/03-fixtures/popular.csv")
        if (!f.exists()) { println("⚠️ Fixture no disponible"); return }

        val lineas = f.readLines(Charsets.UTF_8)
        println("Total líneas CSV: ${lineas.size}")
        lineas.forEachIndexed { idx, linea ->
            val u = linea.uppercase()
            if (u.contains("DEBITO") || u.contains("DÉBITO") || u.contains("CREDITO") || u.contains("CRÉDITO")) {
                println("  Línea $idx: '${linea.take(80)}'")
            }
        }
    }
}
