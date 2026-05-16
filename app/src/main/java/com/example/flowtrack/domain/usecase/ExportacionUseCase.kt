package com.example.flowtrack.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.presentation.components.categoriaRegistry
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
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
}
