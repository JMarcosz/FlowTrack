package com.example.flowtrack.core.crypto

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

/**
 * Generador de IDs determinísticos via SHA-256.
 * Garantiza idempotencia: el mismo input siempre produce el mismo ID,
 * lo que permite re-cargar archivos sin duplicar transacciones.
 */
object HashGenerator {

    fun hashTransaccion(
        uidUsuario: String,
        cuentaId: String,
        fecha: Instant,
        monto: BigDecimal,
        tipo: String,
        descripcionNormalizada: String,
    ): String {
        val input = "$uidUsuario|$cuentaId|${fecha.epochSecond}|${monto.toPlainString()}|$tipo|$descripcionNormalizada"
        return sha256(input).take(20)
    }

    fun hashMovimientoTarjeta(
        uidUsuario: String,
        tarjetaId: String,
        fecha: Instant,
        monto: BigDecimal,
        tipo: String,
        descripcionNormalizada: String,
    ): String {
        val input = "$uidUsuario|$tarjetaId|${fecha.epochSecond}|${monto.toPlainString()}|$tipo|$descripcionNormalizada"
        return sha256(input).take(20)
    }

    fun hashCuenta(uidUsuario: String, bancoCodigo: String, numeroCuenta: String): String {
        val input = "$uidUsuario|$bancoCodigo|$numeroCuenta"
        return sha256(input).take(20)
    }

    fun hashTarjeta(uidUsuario: String, bancoCodigo: String, ultimos4: String): String {
        val input = "$uidUsuario|$bancoCodigo|$ultimos4"
        return sha256(input).take(20)
    }

    fun hashEstadoTarjeta(tarjetaId: String, fechaCorte: Instant): String {
        val input = "$tarjetaId|${fechaCorte.epochSecond}"
        return sha256(input).take(20)
    }

    fun hashCarga(uidUsuario: String, nombreArchivo: String, tamanioBytes: Long, procesadoEn: Instant): String {
        val input = "$uidUsuario|$nombreArchivo|$tamanioBytes|${procesadoEn.epochSecond}"
        return sha256(input).take(20)
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
