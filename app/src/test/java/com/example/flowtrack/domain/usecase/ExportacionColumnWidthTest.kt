package com.example.flowtrack.domain.usecase

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportacionColumnWidthTest {

    @Test
    fun autosizeUsaAnchosDeterministasSinMetricasAwt() {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Prueba")
            sheet.createRow(0).createCell(0).setCellValue("Encabezado")
            sheet.createRow(1).createCell(0).setCellValue("Contenido de longitud controlada")

            ajustarColumnasSinAwt(sheet, 1)

            assertEquals(34 * 256, sheet.getColumnWidth(0))
        }
    }

    @Test
    fun autosizeRespetaLimitesMinimoYMaximo() {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Prueba")
            sheet.createRow(0).createCell(0).setCellValue("A")
            sheet.createRow(1).createCell(1).setCellValue("X".repeat(200))

            ajustarColumnasSinAwt(sheet, 2)

            assertEquals(10 * 256, sheet.getColumnWidth(0))
            assertEquals(60 * 256, sheet.getColumnWidth(1))
            assertTrue(sheet.getColumnWidth(0) < sheet.getColumnWidth(1))
        }
    }

}
