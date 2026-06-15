package com.example.flowtrack.domain.usecase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.CategoriaCatalogo
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class FormatoExportacion { XLSX, CSV, PDF }

enum class SeccionExportacionXlsx {
    RESUMEN_GENERAL,
    TRANSACCIONES,
    RESUMEN_POR_CATEGORIA,
    RESUMEN_POR_BANCO,
    RESUMEN_POR_CUENTA,
    TARJETAS_Y_CORTES,
    MOVIMIENTOS_TARJETA,
}

data class FiltroExportacion(
    val formato: FormatoExportacion,
    val inicio: Instant,
    val fin: Instant,
    val cuentaIds: Set<String> = emptySet(),
    val tarjetaIds: Set<String> = emptySet(),
    val seccionesXlsx: Set<SeccionExportacionXlsx> = SeccionExportacionXlsx.entries.toSet(),
)

class ExportacionUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
    private val cuentaRepository: CuentaRepository,
    private val tarjetaRepository: TarjetaRepository,
) {
    suspend fun exportar(
        context: Context,
        uid: String,
        filtro: FiltroExportacion,
    ): AppResult<Uri> = when (filtro.formato) {
        FormatoExportacion.CSV -> exportarCsv(context, uid, filtro)
        FormatoExportacion.PDF -> exportarPdf(context, uid, filtro)
        FormatoExportacion.XLSX -> exportarXlsx(context, uid, filtro)
    }

    suspend fun exportarCsv(
        context: Context,
        uid: String,
        filtro: FiltroExportacion,
    ): AppResult<Uri> {
        val datos = cargarDatos(uid, filtro)
        if (datos.transacciones.isEmpty() && datos.movimientos.isEmpty()) {
            return AppResult.Error(ErrorApp.Desconocido("No hay registros para exportar en el rango seleccionado."))
        }

        return try {
            val fileName = nombreArchivo("FlowTrack_Export", filtro, "csv")
            val file = File(context.cacheDir, fileName)
            FileWriter(file).use { writer ->
                writer.appendLine("Tipo,ID,Fecha,Fuente,Cuenta/Tarjeta,Banco,Moneda,Monto,Descripcion,Categoria,Referencia,PadreId")
                val zona = ZoneId.of("America/Santo_Domingo")
                datos.transacciones.forEach { tx ->
                    writer.appendLine(
                        listOf(
                            if (tx.esDerivada) "Retencion DGII" else "Transaccion",
                            tx.id,
                            formatDate(tx.fecha.atZone(zona).toLocalDate()),
                            cuentaEtiqueta(tx.cuentaId, datos.cuentasPorId),
                            cuentaEtiqueta(tx.cuentaId, datos.cuentasPorId),
                            tx.bancoCodigo,
                            tx.moneda.name,
                            formatMoney(tx.monto),
                            csvEscapar(tx.descripcionOriginal),
                            categoriaNombre(tx.categoriaId),
                            tx.referencia.orEmpty(),
                            tx.transaccionPadreId.orEmpty(),
                        ).joinToString(",")
                    )
                }
                datos.movimientos.forEach { mov ->
                    writer.appendLine(
                        listOf(
                            "Movimiento tarjeta",
                            mov.id,
                            formatDate(mov.fechaTransaccion.atZone(zona).toLocalDate()),
                            tarjetaEtiqueta(mov.tarjetaId, datos.tarjetasPorId),
                            tarjetaEtiqueta(mov.tarjetaId, datos.tarjetasPorId),
                            mov.bancoCodigo,
                            mov.moneda.name,
                            formatMoney(mov.monto),
                            csvEscapar(mov.descripcionOriginal),
                            categoriaNombre(mov.categoriaId),
                            mov.numeroAutorizacion.orEmpty(),
                            mov.cargaId,
                        ).joinToString(",")
                    )
                }
            }

            AppResult.Success(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.Desconocido("Error generando CSV: ${e.message}", e))
        }
    }

    suspend fun exportarPdf(
        context: Context,
        uid: String,
        filtro: FiltroExportacion,
    ): AppResult<Uri> {
        val datos = cargarDatos(uid, filtro)
        if (datos.transacciones.isEmpty() && datos.movimientos.isEmpty()) {
            return AppResult.Error(ErrorApp.Desconocido("No hay registros para exportar en el rango seleccionado."))
        }

        return try {
            val zona = ZoneId.of("America/Santo_Domingo")
            val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val inicioStr = filtro.inicio.atZone(zona).toLocalDate().format(fmt)
            val finStr = filtro.fin.atZone(zona).toLocalDate().format(fmt)

            val ingresos = datos.transacciones.filter { it.tipo == TipoTransaccion.CREDITO && !it.esDerivada }
                .fold(java.math.BigDecimal.ZERO.setScale(2)) { acc, t -> acc + t.monto }
            val gastos = datos.transacciones.filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
                .fold(java.math.BigDecimal.ZERO.setScale(2)) { acc, t -> acc + t.monto }
            val balance = ingresos - gastos

            val doc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val paintTitle = Paint().apply { textSize = 20f; isFakeBoldText = true; color = Color.parseColor("#0F172A") }
            val paintHeader = Paint().apply { textSize = 13f; isFakeBoldText = true; color = Color.parseColor("#0F172A") }
            val paintBody = Paint().apply { textSize = 11f; color = Color.parseColor("#334155") }
            val paintMuted = Paint().apply { textSize = 10f; color = Color.parseColor("#94A3B8") }
            val paintIncome = Paint().apply { textSize = 11f; color = Color.parseColor("#16A34A"); isFakeBoldText = true }
            val paintExpense = Paint().apply { textSize = 11f; color = Color.parseColor("#DC2626"); isFakeBoldText = true }
            val paintDivider = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }

            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = doc.startPage(pageInfo)
            var canvas: Canvas = page.canvas
            var y = 48f

            fun newPage() {
                doc.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }

            fun checkY(needed: Float) {
                if (y + needed > pageHeight - 40f) newPage()
            }

            fun divider() {
                canvas.drawLine(40f, y, (pageWidth - 40f).toFloat(), y, paintDivider)
                y += 8f
            }

            canvas.drawText("FlowTrack", 40f, y, paintTitle)
            y += 28f
            canvas.drawText("Reporte de exportacion: $inicioStr - $finStr", 40f, y, paintMuted)
            y += 6f
            divider()
            y += 8f

            canvas.drawText("Resumen del periodo", 40f, y, paintHeader)
            y += 20f
            canvas.drawText("Ingresos:", 40f, y, paintBody)
            canvas.drawText(formatMoney(ingresos), 200f, y, paintIncome)
            y += 16f
            canvas.drawText("Gastos:", 40f, y, paintBody)
            canvas.drawText(formatMoney(gastos), 200f, y, paintExpense)
            y += 16f
            canvas.drawText("Balance:", 40f, y, paintBody)
            canvas.drawText(formatMoney(balance.abs()), 200f, y, if (balance >= java.math.BigDecimal.ZERO) paintIncome else paintExpense)
            y += 16f
            canvas.drawText("Transacciones:", 40f, y, paintBody)
            canvas.drawText("${datos.transacciones.count { !it.esDerivada }}", 200f, y, paintBody)
            y += 16f
            canvas.drawText("Retenciones DGII:", 40f, y, paintBody)
            canvas.drawText("${datos.transacciones.count { it.esDerivada }}", 200f, y, paintBody)
            y += 16f
            canvas.drawText("Movimientos tarjeta:", 40f, y, paintBody)
            canvas.drawText("${datos.movimientos.size}", 200f, y, paintBody)
            y += 20f
            divider()
            y += 8f

            canvas.drawText("Detalle de transacciones", 40f, y, paintHeader)
            y += 20f
            val grouped = datos.transacciones
                .filter { !it.esDerivada }
                .groupBy { it.fecha.atZone(zona).toLocalDate() }
                .toSortedMap(compareByDescending { it })
            for ((fecha, txs) in grouped) {
                checkY(30f)
                canvas.drawText(fecha.format(fmt), 40f, y, paintMuted)
                y += 16f
                for (tx in txs) {
                    checkY(16f)
                    val desc = tx.descripcionCorta.ifBlank { tx.descripcionOriginal }.take(45)
                    val montoStr = formatMoney(tx.monto)
                    val txPaint = if (tx.tipo == TipoTransaccion.CREDITO) paintIncome else paintExpense
                    canvas.drawText(desc, 48f, y, paintBody)
                    canvas.drawText(categoriaNombre(tx.categoriaId), 280f, y, paintMuted)
                    canvas.drawText(montoStr, pageWidth - 40f - paintBody.measureText(montoStr), y, txPaint)
                    y += 15f
                    val derivadas = datos.derivadasPorPadre[tx.id].orEmpty()
                    derivadas.forEach { d ->
                        checkY(16f)
                        canvas.drawText("DGII: ${d.descripcionCorta.ifBlank { "Retencion" }.take(38)}", 60f, y, paintMuted)
                        canvas.drawText(formatMoney(d.monto), pageWidth - 40f - paintBody.measureText(formatMoney(d.monto)), y, paintExpense)
                        y += 14f
                    }
                }
                y += 4f
            }

            if (datos.movimientos.isNotEmpty()) {
                y += 8f
                divider()
                y += 8f
                canvas.drawText("Movimientos de tarjeta", 40f, y, paintHeader)
                y += 20f
                val movGrouped = datos.movimientos.groupBy { it.fechaTransaccion.atZone(zona).toLocalDate() }
                    .toSortedMap(compareByDescending { it })
                for ((fecha, movs) in movGrouped) {
                    checkY(30f)
                    canvas.drawText(fecha.format(fmt), 40f, y, paintMuted)
                    y += 16f
                    for (mov in movs) {
                        checkY(16f)
                        val desc = mov.descripcionOriginal.ifBlank { "Movimiento tarjeta" }.take(45)
                        val montoStr = formatMoney(mov.monto)
                        canvas.drawText(desc, 48f, y, paintBody)
                        canvas.drawText(categoriaNombre(mov.categoriaId), 280f, y, paintMuted)
                        canvas.drawText(montoStr, pageWidth - 40f - paintBody.measureText(montoStr), y, paintExpense)
                        y += 15f
                    }
                    y += 4f
                }
            }

            doc.finishPage(page)

            val fileName = nombreArchivo("FlowTrack", filtro, "pdf")
            val file = File(context.cacheDir, fileName)
            withContext(Dispatchers.IO) {
                doc.writeTo(FileOutputStream(file))
            }
            doc.close()

            AppResult.Success(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.Desconocido("Error generando PDF: ${e.message}", e))
        }
    }

    suspend fun exportarXlsx(
        context: Context,
        uid: String,
        filtro: FiltroExportacion,
    ): AppResult<Uri> {
        val datos = cargarDatos(uid, filtro)
        if (datos.transacciones.isEmpty() && datos.movimientos.isEmpty()) {
            return AppResult.Error(ErrorApp.Desconocido("No hay registros para exportar en el rango seleccionado."))
        }

        return try {
            val workbook = XSSFWorkbook()
            val styleHeader = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.BLUE_GREY.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setFont(workbook.createFont().apply {
                    bold = true
                    color = IndexedColors.WHITE.index
                })
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                borderBottom = BorderStyle.THIN
            }

            if (SeccionExportacionXlsx.RESUMEN_GENERAL in filtro.seccionesXlsx) {
                val sheet = workbook.createSheet("Resumen general")
                var row = 0
                fun put(label: String, value: String) {
                    val r = sheet.createRow(row++)
                    r.createCell(0).setCellValue(label)
                    r.createCell(1).setCellValue(value)
                }
                put("Rango", "${formatFecha(filtro.inicio)} - ${formatFecha(filtro.fin)}")
                put("Transacciones", "${datos.transacciones.count { !it.esDerivada }}")
                put("Retenciones DGII", "${datos.transacciones.count { it.esDerivada }}")
                put("Movimientos de tarjeta", "${datos.movimientos.size}")
                put("Ingresos", formatMoney(datos.transacciones.filter { it.tipo == TipoTransaccion.CREDITO && !it.esDerivada }.fold(java.math.BigDecimal.ZERO.setScale(2)) { acc, t -> acc + t.monto }))
                put("Gastos", formatMoney(datos.transacciones.filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }.fold(java.math.BigDecimal.ZERO.setScale(2)) { acc, t -> acc + t.monto }))
                autosize(sheet, 2)
            }

            if (SeccionExportacionXlsx.TRANSACCIONES in filtro.seccionesXlsx) {
                val sheet = workbook.createSheet("Transacciones")
                writeHeader(sheet, listOf("ID", "Fecha", "Cuenta", "Banco", "Monto", "Moneda", "Descripcion", "Categoria", "Referencia", "PadreId"), styleHeader)
                var row = 1
                datos.transacciones.forEach { tx ->
                    val r = sheet.createRow(row++)
                    r.createCell(0).setCellValue(tx.id)
                    r.createCell(1).setCellValue(formatFecha(tx.fecha))
                    r.createCell(2).setCellValue(cuentaEtiqueta(tx.cuentaId, datos.cuentasPorId))
                    r.createCell(3).setCellValue(tx.bancoCodigo)
                    r.createCell(4).setCellValue(formatMoney(tx.monto))
                    r.createCell(5).setCellValue(tx.moneda.name)
                    r.createCell(6).setCellValue(tx.descripcionOriginal)
                    r.createCell(7).setCellValue(categoriaNombre(tx.categoriaId))
                    r.createCell(8).setCellValue(tx.referencia.orEmpty())
                    r.createCell(9).setCellValue(tx.transaccionPadreId.orEmpty())
                }
                autosize(sheet, 10)
            }

            if (SeccionExportacionXlsx.RESUMEN_POR_CATEGORIA in filtro.seccionesXlsx) {
                val sheet = workbook.createSheet("Resumen por categoria")
                writeHeader(sheet, listOf("Categoria", "Monto"), styleHeader)
                val porCategoria = datos.transacciones.filter { !it.esDerivada && it.tipo == TipoTransaccion.DEBITO }
                    .groupBy { it.categoriaId ?: "sin_categorizar" }
                    .mapValues { (_, txs) -> txs.fold(java.math.BigDecimal.ZERO.setScale(2)) { acc, tx -> acc + tx.monto } }
                var row = 1
                porCategoria.entries.sortedByDescending { it.value }.forEach { (catId, monto) ->
                    val r = sheet.createRow(row++)
                    r.createCell(0).setCellValue(categoriaNombre(catId))
                    r.createCell(1).setCellValue(formatMoney(monto))
                }
                autosize(sheet, 2)
            }

            if (SeccionExportacionXlsx.RESUMEN_POR_BANCO in filtro.seccionesXlsx) {
                val sheet = workbook.createSheet("Resumen por banco")
                writeHeader(sheet, listOf("Banco", "Ingresos", "Gastos"), styleHeader)
                val bancos = mutableMapOf<String, Pair<java.math.BigDecimal, java.math.BigDecimal>>()
                datos.transacciones.forEach { tx ->
                    val current = bancos[tx.bancoCodigo] ?: (java.math.BigDecimal.ZERO.setScale(2) to java.math.BigDecimal.ZERO.setScale(2))
                    bancos[tx.bancoCodigo] = if (tx.tipo == TipoTransaccion.CREDITO && !tx.esDerivada) {
                        (current.first + tx.monto) to current.second
                    } else if (tx.tipo == TipoTransaccion.DEBITO && !tx.esDerivada) {
                        current.first to (current.second + tx.monto)
                    } else {
                        current
                    }
                }
                datos.movimientos.forEach { mov ->
                    val current = bancos[mov.bancoCodigo] ?: (java.math.BigDecimal.ZERO.setScale(2) to java.math.BigDecimal.ZERO.setScale(2))
                    bancos[mov.bancoCodigo] = current.first to (current.second + mov.monto)
                }
                var row = 1
                bancos.entries.sortedByDescending { it.value.first + it.value.second }.forEach { (banco, pair) ->
                    val r = sheet.createRow(row++)
                    r.createCell(0).setCellValue(banco)
                    r.createCell(1).setCellValue(formatMoney(pair.first))
                    r.createCell(2).setCellValue(formatMoney(pair.second))
                }
                autosize(sheet, 3)
            }

            if (SeccionExportacionXlsx.RESUMEN_POR_CUENTA in filtro.seccionesXlsx) {
                val sheet = workbook.createSheet("Resumen por cuenta")
                writeHeader(sheet, listOf("Cuenta", "Banco", "Ingresos", "Gastos"), styleHeader)
                var row = 1
                datos.cuentas.forEach { cuenta ->
                    val txs = datos.transacciones.filter { it.cuentaId == cuenta.id && !it.esDerivada }
                    val ingresos = txs.filter { it.tipo == TipoTransaccion.CREDITO }.fold(java.math.BigDecimal.ZERO.setScale(2)) { acc, tx -> acc + tx.monto }
                    val gastos = txs.filter { it.tipo == TipoTransaccion.DEBITO }.fold(java.math.BigDecimal.ZERO.setScale(2)) { acc, tx -> acc + tx.monto }
                    val r = sheet.createRow(row++)
                    r.createCell(0).setCellValue(cuenta.alias.ifBlank { cuenta.numeroCuenta })
                    r.createCell(1).setCellValue(cuenta.bancoCodigo)
                    r.createCell(2).setCellValue(formatMoney(ingresos))
                    r.createCell(3).setCellValue(formatMoney(gastos))
                }
                autosize(sheet, 4)
            }

            if (SeccionExportacionXlsx.TARJETAS_Y_CORTES in filtro.seccionesXlsx) {
                val sheet = workbook.createSheet("Tarjetas y cortes")
                writeHeader(sheet, listOf("Tarjeta", "Banco", "Limite", "Ultimo corte", "Balance al corte", "Pago total"), styleHeader)
                var row = 1
                datos.tarjetas.forEach { tarjeta ->
                    val estado = datos.estadosPorTarjeta[tarjeta.id]?.firstOrNull()
                    val r = sheet.createRow(row++)
                    r.createCell(0).setCellValue(tarjeta.alias.ifBlank { "${tarjeta.bancoCodigo} ****${tarjeta.ultimos4}" })
                    r.createCell(1).setCellValue(tarjeta.bancoCodigo)
                    r.createCell(2).setCellValue(formatMoney(tarjeta.limiteCredito))
                    r.createCell(3).setCellValue(estado?.fechaCorte?.let { formatFecha(it) }.orEmpty())
                    r.createCell(4).setCellValue(estado?.balanceAlCorte?.let { formatMoney(it) }.orEmpty())
                    r.createCell(5).setCellValue(estado?.pagoTotal?.let { formatMoney(it) }.orEmpty())
                }
                autosize(sheet, 6)
            }

            if (SeccionExportacionXlsx.MOVIMIENTOS_TARJETA in filtro.seccionesXlsx) {
                val sheet = workbook.createSheet("Movimientos tarjeta")
                writeHeader(sheet, listOf("ID", "Fecha", "Tarjeta", "Banco", "Monto", "Moneda", "Descripcion", "Categoria"), styleHeader)
                var row = 1
                datos.movimientos.forEach { mov ->
                    val r = sheet.createRow(row++)
                    r.createCell(0).setCellValue(mov.id)
                    r.createCell(1).setCellValue(formatFecha(mov.fechaTransaccion))
                    r.createCell(2).setCellValue(tarjetaEtiqueta(mov.tarjetaId, datos.tarjetasPorId))
                    r.createCell(3).setCellValue(mov.bancoCodigo)
                    r.createCell(4).setCellValue(formatMoney(mov.monto))
                    r.createCell(5).setCellValue(mov.moneda.name)
                    r.createCell(6).setCellValue(mov.descripcionOriginal)
                    r.createCell(7).setCellValue(categoriaNombre(mov.categoriaId))
                }
                autosize(sheet, 8)
            }

            val fileName = nombreArchivo("FlowTrack_Export", filtro, "xlsx")
            val file = File(context.cacheDir, fileName)
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { workbook.write(it) }
            }
            workbook.close()

            AppResult.Success(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.Desconocido("Error generando XLSX: ${e.message}", e))
        }
    }

    private suspend fun cargarDatos(uid: String, filtro: FiltroExportacion): DatosExportacion {
        val txsResult = transaccionRepository.obtenerTransacciones(uid, filtro.inicio, filtro.fin, limite = 0)
        val movsResult = movimientoTarjetaRepository.obtenerMovimientos(uid, filtro.inicio, filtro.fin)
        val cuentasResult = cuentaRepository.obtenerCuentas(uid)
        val tarjetasResult = tarjetaRepository.obtenerTarjetas(uid)

        val transacciones = (txsResult as? AppResult.Success)?.data.orEmpty()
        val movimientos = (movsResult as? AppResult.Success)?.data.orEmpty()
        val cuentas = (cuentasResult as? AppResult.Success)?.data.orEmpty()
        val tarjetas = (tarjetasResult as? AppResult.Success)?.data.orEmpty()

        val cuentasFiltradas = if (filtro.cuentaIds.isEmpty()) cuentas else cuentas.filter { it.id in filtro.cuentaIds }
        val tarjetasFiltradas = if (filtro.tarjetaIds.isEmpty()) tarjetas else tarjetas.filter { it.id in filtro.tarjetaIds }
        val txsFiltradas = transacciones.filter { filtro.cuentaIds.isEmpty() || it.cuentaId in filtro.cuentaIds }
        val movsFiltrados = movimientos.filter { filtro.tarjetaIds.isEmpty() || it.tarjetaId in filtro.tarjetaIds }
        val derivadasPorPadre = txsFiltradas.filter { it.esDerivada }.groupBy { it.transaccionPadreId.orEmpty() }
        val estadosMap = tarjetasFiltradas.associate { tarjeta ->
            tarjeta.id to (when (val res = tarjetaRepository.obtenerEstadosTarjeta(uid, tarjeta.id)) {
                is AppResult.Success -> res.data
                is AppResult.Error -> emptyList()
            })
        }

        return DatosExportacion(
            transacciones = txsFiltradas,
            movimientos = movsFiltrados,
            cuentas = cuentasFiltradas,
            tarjetas = tarjetasFiltradas,
            cuentasPorId = cuentasFiltradas.associateBy { it.id },
            tarjetasPorId = tarjetasFiltradas.associateBy { it.id },
            estadosPorTarjeta = estadosMap,
            derivadasPorPadre = derivadasPorPadre,
        )
    }

    private fun nombreArchivo(prefijo: String, filtro: FiltroExportacion, extension: String): String {
        val zona = ZoneId.of("America/Santo_Domingo")
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        return "${prefijo}_${filtro.inicio.atZone(zona).toLocalDate().format(fmt)}_${filtro.fin.atZone(zona).toLocalDate().format(fmt)}_${System.currentTimeMillis()}.$extension"
    }

    private fun categoriaNombre(categoriaId: String?): String =
        CategoriaCatalogo.nombreDe(categoriaId)

    private fun cuentaEtiqueta(cuentaId: String, cuentasPorId: Map<String, Cuenta>): String =
        cuentasPorId[cuentaId]?.alias?.ifBlank { cuentasPorId[cuentaId]?.numeroCuenta ?: cuentaId } ?: cuentaId

    private fun tarjetaEtiqueta(tarjetaId: String, tarjetasPorId: Map<String, com.example.flowtrack.domain.model.Tarjeta>): String =
        tarjetasPorId[tarjetaId]?.alias?.ifBlank { tarjetasPorId[tarjetaId]?.let { "${it.bancoCodigo} ****${it.ultimos4}" } ?: tarjetaId } ?: tarjetaId

    private fun csvEscapar(texto: String): String = "\"${texto.replace("\"", "\"\"")}\""

    private fun formatFecha(instant: Instant): String =
        instant.atZone(ZoneId.of("America/Santo_Domingo")).toLocalDate().toString()

    private fun writeHeader(sheet: org.apache.poi.ss.usermodel.Sheet, headers: List<String>, style: org.apache.poi.ss.usermodel.CellStyle) {
        val row = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = row.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = style
        }
    }

    private fun autosize(sheet: org.apache.poi.ss.usermodel.Sheet, columns: Int) {
        ajustarColumnasSinAwt(sheet, columns)
    }

    private data class DatosExportacion(
        val transacciones: List<Transaccion>,
        val movimientos: List<MovimientoTarjeta>,
        val cuentas: List<Cuenta>,
        val tarjetas: List<com.example.flowtrack.domain.model.Tarjeta>,
        val cuentasPorId: Map<String, Cuenta>,
        val tarjetasPorId: Map<String, com.example.flowtrack.domain.model.Tarjeta>,
        val estadosPorTarjeta: Map<String, List<EstadoTarjetaSnap>>,
        val derivadasPorPadre: Map<String, List<Transaccion>>,
    )
}

internal fun ajustarColumnasSinAwt(
    sheet: org.apache.poi.ss.usermodel.Sheet,
    columns: Int,
) {
    repeat(columns) { column ->
        val maxCharacters = sheet.asSequence()
            .mapNotNull { row -> row.getCell(column)?.toString() }
            .maxOfOrNull(String::length)
            ?: 0
        val width = ((maxCharacters + 2) * 256).coerceIn(10 * 256, 60 * 256)
        sheet.setColumnWidth(column, width)
    }
}
