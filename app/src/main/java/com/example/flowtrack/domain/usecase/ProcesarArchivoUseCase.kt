package com.example.flowtrack.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.flowtrack.core.crypto.HashGenerator
import com.example.flowtrack.core.notifications.NotificationHelper
import com.example.flowtrack.core.notifications.NotificationRoute
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.core.workers.ClusteringWorker
import com.example.flowtrack.data.firestore.mappers.toDomainCuenta
import com.example.flowtrack.data.firestore.mappers.toDomainEstadoTarjeta
import com.example.flowtrack.data.firestore.mappers.toDomainMovimientoTarjeta
import com.example.flowtrack.data.firestore.mappers.toDomainTarjeta
import com.example.flowtrack.data.firestore.mappers.toDomainTransaccion
import com.example.flowtrack.data.firestore.repositories.ImportacionRepository
import com.example.flowtrack.data.firestore.repositories.NotificacionConfigRepository
import com.example.flowtrack.data.firestore.repositories.ReglaCategoriaRepository
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.BankParserFactory
import com.example.flowtrack.data.parsers.core.ImportRequest
import com.example.flowtrack.data.parsers.core.ParseResult
import com.example.flowtrack.data.parsers.core.ParserNoDisponibleException
import com.example.flowtrack.data.parsers.core.detectarFormatoArchivo
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val LIMITE_BYTES = 10 * 1024 * 1024L
private const val LIMITE_CLUSTERING_IMPORTACION = 50
private val ZONA_RD = ZoneId.of("America/Santo_Domingo")

/**
 * Orquestador del flujo de importación.
 *
     * El banco y tipo de producto son elegidos por el usuario. El formato se detecta
     * desde la firma del archivo y [BankParserFactory] resuelve el parser por clave exacta.
 *
 * Pasos:
 *   1. Leer el archivo (Uri → ArchivoEntrada, validar tamaño)
 *   2. Obtener el parser por (bancoCodigo, productoTipo, formato)
 *   3. Parsear
 *   4. Mapear al modelo de dominio + categorización automática
 *   5. Persistir en Firestore
 */
class ProcesarArchivoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parserFactory: BankParserFactory,
    private val importacionRepository: ImportacionRepository,
    private val reglaCategoriaRepository: ReglaCategoriaRepository,
    private val notificacionConfigRepository: NotificacionConfigRepository,
) {

    suspend fun ejecutar(
        uri: Uri,
        uid: String,
        bancoCodigo: String,
        productoTipo: ProductoTipo,
        formato: FileFormat,
        fechaCorteManual: LocalDate? = null,
        fechaLimitePagoManual: LocalDate? = null,
        claveDocumento: String? = null,
    ): ResultadoImportacion {
        val archivo = leerArchivo(uri) ?: return ResultadoImportacion.Error(
            AppResult.Error(ErrorApp.ParseError("No se pudo leer el archivo seleccionado."))
        )
        return ejecutar(
            archivo = archivo,
            uid = uid,
            bancoCodigo = bancoCodigo,
            productoTipo = productoTipo,
            formato = formato,
            fechaCorteManual = fechaCorteManual,
            fechaLimitePagoManual = fechaLimitePagoManual,
            claveDocumento = claveDocumento,
        )
    }

    suspend fun ejecutar(
        archivo: ArchivoEntrada,
        uid: String,
        bancoCodigo: String,
        productoTipo: ProductoTipo,
        formato: FileFormat,
        fechaCorteManual: LocalDate? = null,
        fechaLimitePagoManual: LocalDate? = null,
        claveDocumento: String? = null,
    ): ResultadoImportacion {
        if (archivo.tamanioBytes > LIMITE_BYTES) {
            return ResultadoImportacion.Error(
                AppResult.Error(ErrorApp.ArchivoMuyGrande(archivo.tamanioBytes, LIMITE_BYTES))
            )
        }

        val formatoDetectado = detectarFormatoArchivo(archivo)
            ?: return ResultadoImportacion.Error(
                AppResult.Error(
                    ErrorApp.FormatoIncompatible(
                        "No se pudo identificar el formato real del archivo seleccionado."
                    )
                )
            )

        val parser = parserFactory.obtenerParser(bancoCodigo, productoTipo, formatoDetectado)
            .getOrElse { ex ->
                val error = if (ex is ParserNoDisponibleException) {
                    ErrorApp.FormatoIncompatible(
                        "$bancoCodigo no admite archivos ${formatoDetectado.name}."
                    )
                } else {
                    ErrorApp.Desconocido(ex.message ?: "Parser no disponible.")
                }
                return ResultadoImportacion.Error(AppResult.Error(error))
            }

        val request = ImportRequest(
            uidUsuario = uid,
            bancoCodigo = bancoCodigo,
            productoTipo = productoTipo,
            formato = formatoDetectado,
            archivo = archivo,
            claveDocumento = claveDocumento,
        )
        return when (val resultado = parser.parse(request)) {
            is ParseResult.Success -> mapearYPersistir(
                resultado,
                uid,
                archivo,
                parser.version,
                fechaCorteManual,
                fechaLimitePagoManual,
            )
            is ParseResult.Error -> ResultadoImportacion.Error(
                AppResult.Error(ErrorApp.ParseError(resultado.message, resultado.cause))
            )
            ParseResult.ClaveRequerida -> ResultadoImportacion.ClaveRequerida
            ParseResult.ClaveIncorrecta -> ResultadoImportacion.ClaveIncorrecta
        }
    }

    private suspend fun mapearYPersistir(
        resultado: ParseResult.Success,
        uid: String,
        archivo: ArchivoEntrada,
        parserVersion: Int,
        fechaCorteManual: LocalDate? = null,
        fechaLimitePagoManual: LocalDate? = null,
    ): ResultadoImportacion {
        var estado = resultado.estado
        if (estado.productoTipo == ProductoTipo.TARJETA &&
            (fechaCorteManual != null || fechaLimitePagoManual != null)
        ) {
            estado = estado.copy(
                fechaCorte = fechaCorteManual ?: estado.fechaCorte,
                fechaLimitePago = fechaLimitePagoManual ?: estado.fechaLimitePago,
            )
        }
        val report = resultado.report
        val cargaId = HashGenerator.hashCarga(uid, archivo.nombre, archivo.tamanioBytes, Instant.now())

        val (periodoInicio, periodoFin) = calcularPeriodo(estado.fechaInicio, estado.fechaFin)

        val reglas = reglaCategoriaRepository.obtenerReglasPersonales(uid).getOrNull() ?: emptyList()

        return when (estado.productoTipo) {
            ProductoTipo.CUENTA -> {
                val cuenta = estado.toDomainCuenta(uid)

                val transacciones = estado.movimientos.map { mov ->
                    mov.toDomainTransaccion(uid, cuenta.id, estado.bancoCodigo, cargaId)
                }

                val txCategorizadas = MotorCategorizacion.categorizarLote(transacciones, reglas)

                val carga = construirCarga(
                    id = cargaId,
                    uid = uid,
                    archivo = archivo,
                    bancoCodigo = estado.bancoCodigo,
                    parserVersion = parserVersion,
                    tipoDocumento = TipoDocumento.CUENTA_CORRIENTE,
                    cuentaId = cuenta.id,
                    tarjetaId = null,
                    periodoInicio = periodoInicio,
                    periodoFin = periodoFin,
                    insertadas = txCategorizadas.size,
                    duplicadas = report.totalIgnorado,
                    advertencias = report.warnings,
                )

                when (val r = importacionRepository.persistirCarga(uid, cuenta, txCategorizadas, carga)) {
                    is AppResult.Success -> {
                        if (txCategorizadas.size > LIMITE_CLUSTERING_IMPORTACION) {
                            ClusteringWorker.enqueueAfterImport(context)
                        }
                        notificarImportacionYAlertas(uid, carga, txCategorizadas, emptyList())
                        ResultadoImportacion.Exito(carga, txCategorizadas.size)
                    }
                    is AppResult.Error   -> {
                        notificarErrorImportacion(uid, archivo.nombre, r.error.toMensajeUsuario())
                        ResultadoImportacion.Error(r)
                    }
                }
            }

            ProductoTipo.TARJETA -> {
                val tarjeta = estado.toDomainTarjeta(uid)

                val movimientos = MotorCategorizacion.categorizarLoteMovimientos(
                    movimientos = estado.movimientos.map { mov ->
                        mov.toDomainMovimientoTarjeta(uid, tarjeta.id, estado.bancoCodigo, cargaId)
                    },
                    reglas = reglas,
                )

                val estadoTarjeta = estado.toDomainEstadoTarjeta(uid, tarjeta.id, cargaId, periodoInicio, periodoFin)

                val carga = construirCarga(
                    id = cargaId,
                    uid = uid,
                    archivo = archivo,
                    bancoCodigo = estado.bancoCodigo,
                    parserVersion = parserVersion,
                    tipoDocumento = TipoDocumento.TARJETA_CREDITO,
                    cuentaId = null,
                    tarjetaId = tarjeta.id,
                    periodoInicio = periodoInicio,
                    periodoFin = periodoFin,
                    insertadas = movimientos.size,
                    duplicadas = report.totalIgnorado,
                    advertencias = report.warnings,
                )

                when (val r = importacionRepository.persistirCargaTarjeta(uid, tarjeta, estadoTarjeta, movimientos, carga)) {
                    is AppResult.Success -> {
                        if (movimientos.size > LIMITE_CLUSTERING_IMPORTACION) {
                            ClusteringWorker.enqueueAfterImport(context)
                        }
                        notificarImportacionYAlertas(uid, carga, emptyList(), movimientos)
                        ResultadoImportacion.Exito(carga, movimientos.size)
                    }
                    is AppResult.Error   -> {
                        notificarErrorImportacion(uid, archivo.nombre, r.error.toMensajeUsuario())
                        ResultadoImportacion.Error(r)
                    }
                }
            }
        }
    }

    private fun calcularPeriodo(inicio: LocalDate?, fin: LocalDate?): Pair<Instant, Instant> {
        val periodoFin = fin?.atStartOfDay(ZONA_RD)?.toInstant() ?: Instant.now()
        val periodoInicio = inicio?.atStartOfDay(ZONA_RD)?.toInstant()
            ?: periodoFin.minusSeconds(30L * 24 * 3600)
        return periodoInicio to periodoFin
    }

    private fun construirCarga(
        id: String,
        uid: String,
        archivo: ArchivoEntrada,
        bancoCodigo: String,
        parserVersion: Int,
        tipoDocumento: TipoDocumento,
        cuentaId: String?,
        tarjetaId: String?,
        periodoInicio: Instant,
        periodoFin: Instant,
        insertadas: Int,
        duplicadas: Int,
        advertencias: List<String>,
    ) = Carga(
        id = id,
        uidUsuario = uid,
        nombreArchivo = archivo.nombre,
        tamanioBytes = archivo.tamanioBytes,
        mimeType = archivo.mimeType,
        bancoCodigo = bancoCodigo,
        parserVersion = parserVersion,
        tipoDocumento = tipoDocumento,
        cuentaId = cuentaId,
        tarjetaId = tarjetaId,
        periodoInicio = periodoInicio,
        periodoFin = periodoFin,
        transaccionesInsertadas = insertadas,
        transaccionesDuplicadas = duplicadas,
        advertencias = advertencias,
        estado = EstadoCarga.EXITOSO,
        procesadoEn = Instant.now(),
    )

    private suspend fun notificarImportacionYAlertas(
        uid: String,
        carga: Carga,
        transacciones: List<com.example.flowtrack.domain.model.Transaccion>,
        movimientos: List<com.example.flowtrack.domain.model.MovimientoTarjeta>,
    ) {
        val config = notificacionConfigRepository.observar(uid).first()
        if (!config.activa) return

        NotificationHelper.notificar(
            context = context,
            canalId = NotificationHelper.CANAL_PUSH,
            notifId = carga.id.hashCode(),
            titulo = "Importación completada",
            texto = "Se procesó ${carga.nombreArchivo} con éxito.",
        )

        if (!config.alertasGastosAltos) return

        val gastosCuenta = transacciones.filter { tx ->
            tx.tipo == com.example.flowtrack.domain.model.TipoTransaccion.DEBITO && !tx.esDerivada &&
                tx.monto >= config.umbralGastoAlto
        }
        val gastosTarjeta = movimientos.filter { mov ->
            mov.tipoMovimiento in setOf(
                TipoMovimientoTarjeta.COMPRA,
                TipoMovimientoTarjeta.AVANCE_EFECTIVO,
                TipoMovimientoTarjeta.INTERES,
                TipoMovimientoTarjeta.COMISION,
            ) && mov.monto >= config.umbralGastoAlto
        }

        val totales = gastosCuenta.size + gastosTarjeta.size
        if (totales <= 0) return

        val texto = when {
            totales == 1 && gastosCuenta.isNotEmpty() ->
                "Un gasto superó tu umbral de ${config.umbralGastoAlto}."
            totales == 1 ->
                "Un movimiento de tarjeta superó tu umbral de ${config.umbralGastoAlto}."
            else ->
                "$totales movimientos superaron tu umbral de ${config.umbralGastoAlto}."
        }

        NotificationHelper.notificar(
            context = context,
            canalId = NotificationHelper.CANAL_ALERTAS,
            notifId = (carga.id.hashCode() * 31) + 7,
            titulo = "Gasto alto detectado",
            texto = texto,
            contentIntent = android.app.PendingIntent.getActivity(
                context,
                (carga.id.hashCode() * 31) + 7,
                android.content.Intent(context, com.example.flowtrack.MainActivity::class.java).apply {
                    putExtra(NotificationRoute.EXTRA_ROUTE, NotificationRoute.ROUTE_TRANSACCIONES)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private suspend fun notificarErrorImportacion(uid: String, nombreArchivo: String, mensaje: String) {
        val config = notificacionConfigRepository.observar(uid).first()
        if (!config.activa) return

        val notifId = (uid + nombreArchivo).hashCode()
        NotificationHelper.notificar(
            context = context,
            canalId = NotificationHelper.CANAL_ALERTAS,
            notifId = notifId,
            titulo = "Importación fallida",
            texto = mensaje,
            contentIntent = android.app.PendingIntent.getActivity(
                context,
                notifId,
                android.content.Intent(context, com.example.flowtrack.MainActivity::class.java).apply {
                    putExtra(NotificationRoute.EXTRA_ROUTE, NotificationRoute.ROUTE_HISTORIAL)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun leerArchivo(uri: Uri): ArchivoEntrada? {
        return try {
            val contentResolver = context.contentResolver
            var nombre = "archivo"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) nombre = cursor.getString(nameIndex)
            }
            if (nombre == "archivo") {
                nombre = uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
            }
            val extension = nombre.substringAfterLast('.', "").lowercase()
            val mimeType = contentResolver.getType(uri)
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            ArchivoEntrada(nombre, extension, bytes.size.toLong(), bytes, mimeType)
        } catch (_: Exception) { null }
    }
}

// ─── Tipos de resultado ───────────────────────────────────────────────────────

sealed class ResultadoImportacion {
    data class Exito(val carga: Carga, val transaccionesInsertadas: Int) : ResultadoImportacion()
    data class Error(val error: AppResult.Error) : ResultadoImportacion()
    data object ClaveRequerida : ResultadoImportacion()
    data object ClaveIncorrecta : ResultadoImportacion()
}
