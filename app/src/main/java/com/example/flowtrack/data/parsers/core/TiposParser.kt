package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.domain.model.Moneda
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

// ─── ResultadoParseo ─────────────────────────────────────────────────────────

/** Resultado del parseo completo. Sealed para forzar manejo exhaustivo en UI. */
sealed class ResultadoParseo {

    data class ExitoCuenta(
        val cuenta: CuentaDetectada,
        val transacciones: List<TransaccionNormalizada>,
        val resumenPeriodo: ResumenPeriodoDetectado?,
        val advertencias: List<String> = emptyList(),
    ) : ResultadoParseo()

    data class ExitoTarjeta(
        val tarjeta: TarjetaDetectada,
        val estadoTarjeta: EstadoTarjetaDetectado,
        val movimientos: List<MovimientoTarjetaNormalizado>,
        val advertencias: List<String> = emptyList(),
    ) : ResultadoParseo()

    /** El parser reconoce el formato pero necesita confirmación del banco por el usuario. */
    data class RequiereConfirmacion(
        val mensaje: String,
        val opcionesBanco: List<String>,
        val datosParciales: Any,
    ) : ResultadoParseo()

    data class Error(
        val mensaje: String,
        val excepcion: Throwable? = null,
        val recuperable: Boolean = false,
    ) : ResultadoParseo()
}
