package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import java.math.BigDecimal
import java.time.LocalDate

// ─── Entrada ─────────────────────────────────────────────────────────────────

/** Archivo entrante con metadata útil para detección rápida. */
data class ArchivoEntrada(
    val nombre: String,         // "Estado_de_Cuenta_BanReservas.pdf"
    val extension: String,      // "pdf" (lowercase, sin punto)
    val tamanioBytes: Long,
    val bytes: ByteArray,       // contenido en memoria — máx 10 MB
    val mimeType: String?,      // "application/pdf"
) {
    // ByteArray no implementa equals/hashCode por valor — override necesario
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArchivoEntrada) return false
        return nombre == other.nombre && extension == other.extension &&
            tamanioBytes == other.tamanioBytes && bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = nombre.hashCode()
        result = 31 * result + extension.hashCode()
        result = 31 * result + tamanioBytes.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }
}

// ─── Detección ───────────────────────────────────────────────────────────────

/** Resultado de la evaluación de detección de un parser. */
data class ConfianzaDeteccion(
    val confianza: Float,           // 0.0 a 1.0
    val razon: String,              // "Encontró IBAN DO57BRRD en página 1"
    val pistas: Map<String, String> = emptyMap(), // info auxiliar para UI
)

// ─── Contexto ────────────────────────────────────────────────────────────────

/** Contexto que el caller provee al parser antes de parsear. */
data class ContextoParseo(
    val uidUsuario: String,
    val cuentaIdSugerida: String? = null,   // si el usuario ya seleccionó cuenta destino
    val tarjetaIdSugerida: String? = null,
    val zonaHoraria: String = "America/Santo_Domingo",
)

// ─── Modelos intermedios (Capa B → Capa C) ───────────────────────────────────

data class CuentaDetectada(
    val numeroCuenta: String,               // últimos 4–10 dígitos
    val numeroCuentaCompleto: String?,      // IBAN si está disponible
    val titular: String,
    val tipoCuenta: TipoCuenta,
    val moneda: Moneda,
    val balanceAlCorte: BigDecimal?,
    val balanceAnterior: BigDecimal?,
)

data class TransaccionNormalizada(
    val fecha: LocalDate,
    val fechaPosteo: LocalDate?,
    val descripcionCorta: String,           // categoría nativa del banco
    val descripcionOriginal: String,        // raw del estado
    val monto: BigDecimal,                  // siempre positivo
    val tipo: TipoTransaccion,             // DEBITO | CREDITO
    val moneda: Moneda,
    val balanceDespues: BigDecimal?,
    val referencia: String?,
    val serial: String?,
    val esDerivada: Boolean = false,        // ej: impuesto DGII 0.15%
    val referenciaPadre: String? = null,    // ref de la transacción origen si esDerivada
    val metadataBanco: Map<String, String> = emptyMap(),
)

data class ResumenPeriodoDetectado(
    val periodoInicio: LocalDate,
    val periodoFin: LocalDate,
    val cantidadDebitos: Int,
    val cantidadCreditos: Int,
    val totalDebitos: BigDecimal,
    val totalCreditos: BigDecimal,
    val balanceFinal: BigDecimal,
)

data class TarjetaDetectada(
    val ultimos4: String,
    val titular: String,
    val tipoRed: String?,                   // "VISA", "MASTERCARD"
    val limiteCredito: BigDecimal,
    val moneda: Moneda,
    val diaCorte: Int?,
    val diaPago: Int?,
    val tasaInteresAnual: Double?,
)

data class EstadoTarjetaDetectado(
    val fechaCorte: LocalDate,
    val fechaLimitePago: LocalDate,
    val balanceAlCorte: BigDecimal,
    val balanceAnterior: BigDecimal?,
    val pagoMinimo: BigDecimal,
    val pagoTotal: BigDecimal,
    val montoVencido: BigDecimal,
    val balancePromedioDiario: BigDecimal?,
    val interesPorFinanciamiento: BigDecimal?,
    val cashbackGanado: BigDecimal?,
)

data class MovimientoTarjetaNormalizado(
    val fechaTransaccion: LocalDate,
    val fechaPosteo: LocalDate?,
    val descripcionOriginal: String,
    val monto: BigDecimal,                  // siempre positivo
    val tipoMovimiento: TipoMovimientoTarjeta,
    val moneda: Moneda,
    val numeroAutorizacion: String?,
    val metadataBanco: Map<String, String> = emptyMap(),
)

// Nota: el antiguo `sealed class ResultadoParseo` (ExitoCuenta/ExitoTarjeta/RequiereConfirmacion/Error)
// fue eliminado. Pertenecía al sistema de parsing previo y no tenía referencias vivas; el flujo actual
// usa `ParseResult` (ver abajo) con `EstadoCuentaNormalizado`. La auditoría que reportaba
// "ExitoTarjeta devuelve Error" se refería a ese sistema ya retirado.

// ─── Modelos del contrato BankStatementParser ────────────────────────────────

/** Identifica unívocamente un parser: banco + tipo de producto + formato de archivo. */
data class ParserKey(
    val bancoCodigo: String,
    val productoTipo: ProductoTipo,
    val formato: FileFormat,
)

/** Petición de importación que el caller entrega al parser seleccionado. */
data class ImportRequest(
    val uidUsuario: String,
    val bancoCodigo: String,
    val productoTipo: ProductoTipo,
    val formato: FileFormat,
    val archivo: ArchivoEntrada,
)

/**
 * Tipo de movimiento financiero normalizado.
 * Más granular que DEBITO/CREDITO para permitir mejor categorización automática.
 */
enum class TipoMovimiento {
    INGRESO,        // depósito, nómina, transferencia recibida
    GASTO,          // consumo POS, pago de servicios, débito general
    PAGO_TARJETA,   // pago de saldo de tarjeta de crédito
    INTERES,        // interés por financiamiento
    COMISION,       // comisión bancaria
    IMPUESTO,       // DGII, impuestos
    CASHBACK,       // cashback o rebate
    TRANSFERENCIA,  // transferencia saliente o ambigua
    RETIRO_ATM,     // retiro en cajero automático
    AJUSTE,         // ajuste contable
    DEVOLUCION,     // reverso, devolución o crédito en cuenta/tarjeta
}

/** Movimiento individual ya normalizado por el parser. */
data class MovimientoNormalizado(
    val fechaTransaccion: LocalDate,
    val fechaPosteo: LocalDate?,
    val descripcionOriginal: String,
    val descripcionNormalizada: String,
    val descripcionCorta: String,
    val monto: BigDecimal,
    val montoUsd: BigDecimal? = null,       // monto paralelo en USD (Cibao bimoneda); null si no aplica
    val tipo: TipoMovimiento,
    val moneda: Moneda,
    val balancePosterior: BigDecimal?,
    val referencia: String?,
    val categoriaId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/** Estado de cuenta completo ya normalizado por el parser. */
data class EstadoCuentaNormalizado(
    val bancoCodigo: String,
    val productoTipo: ProductoTipo,
    val productoId: String?,
    val titular: String?,
    val moneda: Moneda,
    val fechaInicio: LocalDate?,
    val fechaFin: LocalDate?,
    val balanceInicial: BigDecimal?,
    val balanceFinal: BigDecimal?,
    val movimientos: List<MovimientoNormalizado>,
    // Tarjeta
    val fechaCorte: LocalDate? = null,
    val fechaLimitePago: LocalDate? = null,
    val pagoMinimo: BigDecimal? = null,
    val pagoTotal: BigDecimal? = null,
    val montoVencido: BigDecimal? = null,
    val limiteCredito: BigDecimal? = null,
    val tipoRed: String? = null,
    val tasaInteresAnual: Double? = null,
    val diaCorte: Int? = null,
    val diaPago: Int? = null,
    val balancePromedioDiario: BigDecimal? = null,
    val interesPorFinanciamiento: BigDecimal? = null,
    val cashbackGanado: BigDecimal? = null,
    // Cuenta
    val numeroCuentaCompleto: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/** Reporte de estadísticas del proceso de parseo. */
data class ParseReport(
    val parserId: String,
    val totalDetectado: Int,
    val totalImportado: Int,
    val totalIgnorado: Int,
    val warnings: List<String>,
    val errors: List<String>,
)

/** Resultado del parseo con la interfaz BankStatementParser. */
sealed interface ParseResult {
    data class Success(
        val estado: EstadoCuentaNormalizado,
        val report: ParseReport,
    ) : ParseResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : ParseResult
}
