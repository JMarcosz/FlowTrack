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
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.presentation.components.categoriaRegistry
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ExportacionUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository
) {
    suspend fun exportarCsv(
        context: Context,
        uid: String,
        inicio: Instant,
        fin: Instant
    ): AppResult<Uri> {
        val result = transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 10000)
        if (result is AppResult.Error) return AppResult.Error(result.error)
        
        val transacciones = (result as AppResult.Success).data
        if (transacciones.isEmpty()) {
            return AppResult.Error(ErrorApp.Desconocido("No hay transacciones en el rango seleccionado."))
        }

        return try {
            val fileName = "FlowTrack_Export_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            val writer = FileWriter(file)

            // Header
            writer.append("ID,Fecha,Banco,Monto,Moneda,Tipo,Categoría,Descripción,Referencia\n")

            val zona = ZoneId.of("America/Santo_Domingo")

            for (tx in transacciones) {
                val fechaStr = formatDate(tx.fecha.atZone(zona).toLocalDate())
                val tipo = if (tx.tipo == TipoTransaccion.CREDITO) "Ingreso" else "Gasto"
                val catNombre = tx.categoriaId?.let { categoriaRegistry[it]?.nombre } ?: "Sin Categorizar"
                
                // Escapar comas en la descripción y referencia
                val desc = "\"${tx.descripcionOriginal.replace("\"", "\"\"")}\""
                val ref = "\"${(tx.referencia ?: "").replace("\"", "\"\"")}\""

                writer.append("${tx.id},$fechaStr,${tx.bancoCodigo},${tx.monto},${tx.moneda},$tipo,$catNombre,$desc,$ref\n")
            }

            writer.flush()
            writer.close()

            // Utiliza FileProvider para que se pueda compartir de forma segura
            // (Asume que está configurado en el AndroidManifest.xml de la app)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            AppResult.Success(uri)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.Desconocido("Error generando CSV: ${e.message}", e))
        }
    }

    suspend fun exportarPdf(
        context: Context,
        uid: String,
        inicio: Instant,
        fin: Instant,
    ): AppResult<Uri> {
        val result = transaccionRepository.obtenerTransacciones(uid, inicio, fin, limite = 10000)
        if (result is AppResult.Error) return AppResult.Error(result.error)
        val transacciones = (result as AppResult.Success).data
        if (transacciones.isEmpty()) {
            return AppResult.Error(ErrorApp.Desconocido("No hay transacciones en el rango seleccionado."))
        }

        return try {
            val zona = ZoneId.of("America/Santo_Domingo")
            val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val inicioStr = inicio.atZone(zona).toLocalDate().format(fmt)
            val finStr = fin.atZone(zona).toLocalDate().format(fmt)

            val totalIngresos = transacciones.filter { it.tipo == TipoTransaccion.CREDITO }
                .fold(java.math.BigDecimal.ZERO) { acc, t -> acc + t.monto }
            val totalGastos = transacciones.filter { it.tipo == TipoTransaccion.DEBITO }
                .fold(java.math.BigDecimal.ZERO) { acc, t -> acc + t.monto }
            val balance = totalIngresos - totalGastos

            val doc = PdfDocument()
            val pageWidth = 595   // A4 points
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

            fun checkY(needed: Float) { if (y + needed > pageHeight - 40f) newPage() }

            fun drawDivider() {
                canvas.drawLine(40f, y, (pageWidth - 40f).toFloat(), y, paintDivider)
                y += 8f
            }

            // ── Encabezado ────────────────────────────────────────
            canvas.drawText("FlowTrack", 40f, y, paintTitle)
            y += 28f
            canvas.drawText("Reporte de transacciones: $inicioStr – $finStr", 40f, y, paintMuted)
            y += 6f
            drawDivider()
            y += 8f

            // ── Resumen ───────────────────────────────────────────
            canvas.drawText("Resumen del período", 40f, y, paintHeader)
            y += 20f
            canvas.drawText("Ingresos:", 40f, y, paintBody)
            canvas.drawText(formatMoney(totalIngresos), 200f, y, paintIncome)
            y += 16f
            canvas.drawText("Gastos:", 40f, y, paintBody)
            canvas.drawText(formatMoney(totalGastos), 200f, y, paintExpense)
            y += 16f
            canvas.drawText("Balance:", 40f, y, paintBody)
            val balancePaint = if (balance >= java.math.BigDecimal.ZERO) paintIncome else paintExpense
            canvas.drawText(formatMoney(balance.abs()), 200f, y, balancePaint)
            y += 16f
            canvas.drawText("Transacciones:", 40f, y, paintBody)
            canvas.drawText("${transacciones.size}", 200f, y, paintBody)
            y += 20f
            drawDivider()
            y += 8f

            // ── Listado agrupado por fecha ────────────────────────
            canvas.drawText("Detalle de transacciones", 40f, y, paintHeader)
            y += 20f

            val grouped = transacciones
                .filter { !it.esDerivada }
                .groupBy { it.fecha.atZone(zona).toLocalDate() }
                .toSortedMap(compareByDescending { it })

            for ((fecha, txs) in grouped) {
                checkY(30f)
                canvas.drawText(fecha.format(fmt), 40f, y, paintMuted)
                y += 16f

                for (tx in txs) {
                    checkY(16f)
                    val catNombre = tx.categoriaId?.let { categoriaRegistry[it]?.nombre } ?: "Sin categorizar"
                    val desc = tx.descripcionCorta.ifBlank { tx.descripcionOriginal }.take(45)
                    val montoStr = formatMoney(tx.monto)
                    val txPaint = if (tx.tipo == TipoTransaccion.CREDITO) paintIncome else paintExpense
                    canvas.drawText(desc, 48f, y, paintBody)
                    canvas.drawText(catNombre, 310f, y, paintMuted)
                    canvas.drawText(montoStr, (pageWidth - 40f - paintBody.measureText(montoStr)), y, txPaint)
                    y += 15f
                }
                y += 4f
            }

            doc.finishPage(page)

            val fileName = "FlowTrack_${inicioStr.replace("/", "-")}_${finStr.replace("/", "-")}.pdf"
            val file = File(context.cacheDir, fileName)
            doc.writeTo(FileOutputStream(file))
            doc.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            AppResult.Success(uri)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.Desconocido("Error generando PDF: ${e.message}", e))
        }
    }
}
