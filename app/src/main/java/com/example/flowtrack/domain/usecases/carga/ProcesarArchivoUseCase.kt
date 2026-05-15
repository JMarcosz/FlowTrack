package com.example.flowtrack.domain.usecases.carga

import android.content.Context
import android.net.Uri
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDomainConBanco
import com.example.flowtrack.data.firestore.repositories.ImportacionRepository
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.data.parsers.core.ContextoParseo
import com.example.flowtrack.data.parsers.core.ParserRegistry
import com.example.flowtrack.data.parsers.core.ResultadoDeteccion
import com.example.flowtrack.data.parsers.core.ResultadoParseo
import com.example.flowtrack.core.crypto.HashGenerator
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.domain.model.TipoDocumento
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/** Límite de tamaño de archivo aceptable: 10 MB. */
private const val LIMITE_BYTES = 10 * 1024 * 1024L

/**
 * Orquestador del flujo de importación de un estado de cuenta.
 * Pasos:
 *   1. Leer el archivo (Uri → ByteArray, validar tamaño)
 *   2. Detectar banco con ParserRegistry
 *   3. Parsear con el parser elegido
 *   4. Mapear al modelo de dominio + asignar hashes
 *   5. Persistir en Firestore via ImportacionRepository
 *
 * Emite un ResultadoImportacion sealed para que la UI maneje cada estado.
 */
class ProcesarArchivoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parserRegistry: ParserRegistry,
    private val importacionRepository: ImportacionRepository,
) {

    suspend fun ejecutar(
        uri: Uri,
        uid: String,
        bancoCodigo: String? = null,  // null = dejar que el registry detecte
        contexto: ContextoParseo = ContextoParseo(uidUsuario = uid),
    ): ResultadoImportacion {

        // 1. Leer archivo
        val archivo = leerArchivo(uri) ?: return ResultadoImportacion.Error(
            AppResult.Error(ErrorApp.ParseError("No se pudo leer el archivo seleccionado."))
        )

        // 2. Validar tamaño
        if (archivo.tamanioBytes > LIMITE_BYTES) {
            return ResultadoImportacion.Error(
                AppResult.Error(
                    ErrorApp.ArchivoMuyGrande(
                        tamanioBytes = archivo.tamanioBytes,
                        limiteBytes = LIMITE_BYTES,
                    )
                )
            )
        }

        // 3. Detectar banco (o usar el que el usuario confirmó)
        if (bancoCodigo == null) {
            when (val deteccion = parserRegistry.detectar(archivo)) {
                is ResultadoDeteccion.AltaConfianza -> {
                    // Procesar automáticamente
                    return procesarConParser(
                        archivo = archivo,
                        bancoCodigo = deteccion.parser.codigoBanco,
                        confianza = deteccion.confianza.confianza,
                        detectadoAuto = true,
                        contexto = contexto,
                        uid = uid,
                        parserVersion = deteccion.parser.version,
                        tipoDocumento = deteccion.parser.tipoDocumento,
                        parser = deteccion.parser,
                    )
                }
                is ResultadoDeteccion.MediaConfianza -> {
                    return ResultadoImportacion.RequiereSeleccion(
                        archivo = archivo,
                        candidatos = deteccion.candidatos.map { (parser, conf) ->
                            CandidatoBanco(
                                codigoBanco = parser.codigoBanco,
                                confianza = conf.confianza,
                                razon = conf.razon,
                            )
                        },
                        mostrarCatalogo = false,
                    )
                }
                is ResultadoDeteccion.BajaConfianza, is ResultadoDeteccion.NoDetectado -> {
                    return ResultadoImportacion.RequiereSeleccion(
                        archivo = archivo,
                        candidatos = when (deteccion) {
                            is ResultadoDeteccion.BajaConfianza -> deteccion.candidatos.map { (p, c) ->
                                CandidatoBanco(p.codigoBanco, c.confianza, c.razon)
                            }
                            else -> emptyList()
                        },
                        mostrarCatalogo = true,
                    )
                }
            }
        }

        // bancoCodigo explícito: buscar el parser correspondiente
        val parser = parserRegistry.bancosRegistrados().let {
            parserRegistry // indirecto — accedemos por registry
        }
        // Nota: parsear directamente con el banco confirmado
        // Se resuelve en la segunda llamada cuando el usuario confirma
        return ResultadoImportacion.Error(
            AppResult.Error(ErrorApp.Desconocido("Parser para '$bancoCodigo' no encontrado."))
        )
    }

    /** Segunda invocación: el usuario ya confirmó el banco. */
    suspend fun ejecutarConBancoConfirmado(
        uri: Uri,
        uid: String,
        bancoCodigo: String,
    ): ResultadoImportacion {
        val archivo = leerArchivo(uri) ?: return ResultadoImportacion.Error(
            AppResult.Error(ErrorApp.ParseError("No se pudo releer el archivo."))
        )

        val deteccion = parserRegistry.detectar(archivo)

        // Forzar el parser del banco confirmado
        val (parser, confianza) = when (deteccion) {
            is ResultadoDeteccion.AltaConfianza ->
                if (deteccion.parser.codigoBanco == bancoCodigo)
                    deteccion.parser to deteccion.confianza.confianza
                else findParserByCode(bancoCodigo, deteccion) to 0.3f

            else -> findParserByCode(bancoCodigo, deteccion) to 0.3f
        }

        return procesarConParser(
            archivo = archivo,
            bancoCodigo = bancoCodigo,
            confianza = confianza,
            detectadoAuto = false,
            contexto = ContextoParseo(uidUsuario = uid),
            uid = uid,
            parserVersion = parser?.version ?: 1,
            tipoDocumento = parser?.tipoDocumento ?: TipoDocumento.CUENTA_CORRIENTE,
            parser = parser,
        )
    }

    private fun findParserByCode(
        bancoCodigo: String,
        deteccion: ResultadoDeteccion,
    ) = when (deteccion) {
        is ResultadoDeteccion.BajaConfianza ->
            deteccion.candidatos.firstOrNull { it.first.codigoBanco == bancoCodigo }?.first
        is ResultadoDeteccion.MediaConfianza ->
            deteccion.candidatos.firstOrNull { it.first.codigoBanco == bancoCodigo }?.first
        else -> null
    }

    private suspend fun procesarConParser(
        archivo: ArchivoEntrada,
        bancoCodigo: String,
        confianza: Float,
        detectadoAuto: Boolean,
        contexto: ContextoParseo,
        uid: String,
        parserVersion: Int,
        tipoDocumento: TipoDocumento,
        parser: com.example.flowtrack.data.parsers.core.BankParser?,
    ): ResultadoImportacion {
        if (parser == null) {
            return ResultadoImportacion.Error(
                AppResult.Error(ErrorApp.Desconocido("No hay parser disponible para $bancoCodigo."))
            )
        }

        val resultado = parser.parsear(archivo, contexto)

        return when (resultado) {
            is ResultadoParseo.ExitoCuenta -> {
                val cuenta = resultado.cuenta.toDomainConBanco(uid, bancoCodigo)
                val cargaId = HashGenerator.hashCarga(uid, archivo.nombre, archivo.tamanioBytes, Instant.now())

                // Mapear transacciones a dominio
                val transacciones = resultado.transacciones.map { txNorm ->
                    txNorm.toDomain(
                        uidUsuario = uid,
                        cuentaId = cuenta.id,
                        bancoCodigo = bancoCodigo,
                        cargaId = cargaId,
                    )
                }

                // Vincular derivadas DGII (segunda pasada)
                val refMap = transacciones.associateBy { it.referencia ?: "" }
                val txConVinculos = transacciones.map { tx ->
                    if (tx.esDerivada && tx.referencia != null) {
                        val padre = refMap[tx.referencia]
                        if (padre != null) tx.copy(transaccionPadreId = padre.id) else tx
                    } else tx
                }
                // Actualizar lista de derivadas en las padres
                val txFinales = txConVinculos.map { tx ->
                    val derivadasIds = txConVinculos
                        .filter { it.transaccionPadreId == tx.id }
                        .map { it.id }
                    if (derivadasIds.isNotEmpty()) tx.copy(derivadasIds = derivadasIds) else tx
                }

                val carga = Carga(
                    id = cargaId,
                    uidUsuario = uid,
                    nombreArchivo = archivo.nombre,
                    tamanioBytes = archivo.tamanioBytes,
                    mimeType = archivo.mimeType,
                    bancoCodigo = bancoCodigo,
                    bancoDetectadoAutomaticamente = detectadoAuto,
                    confianzaDeteccion = confianza,
                    parserVersion = parserVersion,
                    tipoDocumento = tipoDocumento,
                    cuentaId = cuenta.id,
                    tarjetaId = null,
                    periodoInicio = resultado.resumenPeriodo?.periodoInicio?.let {
                        it.atStartOfDay(java.time.ZoneId.of("America/Santo_Domingo")).toInstant()
                    },
                    periodoFin = resultado.resumenPeriodo?.periodoFin?.let {
                        it.atStartOfDay(java.time.ZoneId.of("America/Santo_Domingo")).toInstant()
                    },
                    transaccionesInsertadas = txFinales.size,
                    transaccionesDuplicadas = 0,
                    advertencias = resultado.advertencias,
                    estado = EstadoCarga.EXITOSO,
                    procesadoEn = Instant.now(),
                )

                val persistResult = importacionRepository.persistirCarga(uid, cuenta, txFinales, carga)

                when (persistResult) {
                    is AppResult.Success -> ResultadoImportacion.Exito(carga, txFinales.size)
                    is AppResult.Error -> ResultadoImportacion.Error(persistResult)
                }
            }

            is ResultadoParseo.ExitoTarjeta -> {
                // Sprint 3: implementar persistencia de tarjeta
                ResultadoImportacion.Error(
                    AppResult.Error(ErrorApp.Desconocido("Parseo de tarjeta pendiente de implementación completa."))
                )
            }

            is ResultadoParseo.RequiereConfirmacion -> {
                ResultadoImportacion.RequiereSeleccion(
                    archivo = archivo,
                    candidatos = resultado.opcionesBanco.map { CandidatoBanco(it, 0.3f, "Formato genérico") },
                    mostrarCatalogo = true,
                )
            }

            is ResultadoParseo.Error -> {
                ResultadoImportacion.Error(
                    AppResult.Error(ErrorApp.ParseError(resultado.mensaje, resultado.excepcion))
                )
            }
        }
    }

    private fun leerArchivo(uri: Uri): ArchivoEntrada? {
        return try {
            val contentResolver = context.contentResolver
            val nombre = uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
            val extension = nombre.substringAfterLast('.', "").lowercase()
            val mimeType = contentResolver.getType(uri)

            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val tamanio = bytes.size.toLong()

            ArchivoEntrada(
                nombre = nombre,
                extension = extension,
                tamanioBytes = tamanio,
                bytes = bytes,
                mimeType = mimeType,
            )
        } catch (e: Exception) {
            null
        }
    }
}

// ─── Tipos de resultado ───────────────────────────────────────────────────────

sealed class ResultadoImportacion {
    data class Exito(val carga: Carga, val transaccionesInsertadas: Int) : ResultadoImportacion()
    data class RequiereSeleccion(
        val archivo: ArchivoEntrada,
        val candidatos: List<CandidatoBanco>,
        val mostrarCatalogo: Boolean,
    ) : ResultadoImportacion()
    data class Error(val error: AppResult.Error) : ResultadoImportacion()
}

data class CandidatoBanco(
    val codigoBanco: String,
    val confianza: Float,
    val razon: String,
)
