package com.example.flowtrack.domain.model

import java.math.BigDecimal
import java.time.Instant

enum class CategoriaMeta {
    FONDO_EMERGENCIA,
    VIAJES,
    VEHICULO,
    VIVIENDA,
    EDUCACION,
    TECNOLOGIA,
    INVERSION,
    OTRO,
}

enum class EstadoMeta {
    ACTIVA,
    COMPLETADA,
    PAUSADA,
    CANCELADA,
}

enum class TipoMovimientoMeta {
    DEPOSIT,
    WITHDRAW,
    TRANSFER_IN,
    TRANSFER_OUT,
    AUTO_SAVE,
    ROUNDUP,
}

enum class FrecuenciaAhorro {
    DIARIO,
    SEMANAL,
    QUINCENAL,
    MENSUAL,
}

enum class TipoReglaAhorro {
    MONTO_FIJO,
    PORCENTAJE_INGRESO,
    REDONDEO_COMPRAS,
}

enum class RolParticipanteMeta {
    ADMINISTRADOR,
    COLABORADOR,
}

data class Meta(
    val id: String,
    val uidUsuario: String,
    val nombre: String,
    val emoji: String,
    val montoObjetivo: BigDecimal,
    val montoActual: BigDecimal,
    val fechaLimite: Instant?,
    val activa: Boolean = true,
    val creadoEn: Instant,
    val descripcion: String? = null,
    val categoria: CategoriaMeta = CategoriaMeta.OTRO,
    val cuentaId: String? = null,
    val fechaObjetivo: Instant? = fechaLimite,
    val estado: EstadoMeta = when {
        !activa -> EstadoMeta.CANCELADA
        montoObjetivo > BigDecimal.ZERO && montoActual >= montoObjetivo -> EstadoMeta.COMPLETADA
        else -> EstadoMeta.ACTIVA
    },
    val actualizadaEn: Instant = creadoEn,
) {
    val porcentaje: Float get() =
        if (montoObjetivo > BigDecimal.ZERO)
            (montoActual.toFloat() / montoObjetivo.toFloat()).coerceIn(0f, 1f)
        else 0f

    val completada: Boolean get() = estado == EstadoMeta.COMPLETADA || montoActual >= montoObjetivo
}

data class MovimientoMeta(
    val id: String,
    val uidUsuario: String,
    val metaId: String,
    val cuentaId: String?,
    val tipo: TipoMovimientoMeta,
    val monto: BigDecimal,
    val balanceAntes: BigDecimal,
    val balanceDespues: BigDecimal,
    val metaDestinoId: String? = null,
    val requestId: String,
    val creadoEn: Instant,
)

data class ReglaAutoAhorro(
    val id: String,
    val uidUsuario: String,
    val metaId: String,
    val tipo: TipoReglaAhorro,
    val frecuencia: FrecuenciaAhorro,
    val monto: BigDecimal? = null,
    val porcentajeIngreso: BigDecimal? = null,
    val activa: Boolean = true,
    val creadaEn: Instant,
)

data class ParticipanteMeta(
    val uidUsuario: String,
    val metaId: String,
    val participanteUid: String,
    val nombre: String,
    val rol: RolParticipanteMeta,
    val creadoEn: Instant,
)

data class ResumenMetas(
    val saldoTotal: BigDecimal,
    val saldoDisponible: BigDecimal,
    val saldoEnMetas: BigDecimal,
    val porcentajeAhorrado: BigDecimal,
    val metasCompletadas: Int,
    val proximaMetaACompletar: Meta?,
    val progresoGlobal: BigDecimal,
)
