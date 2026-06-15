package com.example.flowtrack.domain.model

object CategoriaCatalogo {
    const val ALIMENTACION = "alimentacion"
    const val TRANSPORTE = "transporte"
    const val SALUD = "salud"
    const val ENTRETENIMIENTO = "entretenimiento"
    const val SUSCRIPCIONES = "suscripciones"
    const val SERVICIOS = "servicios"
    const val COMPRAS = "compras"
    const val ATM = "atm"
    const val TRANSFERENCIA_ENVIADA = "transferencia_enviada"
    const val IMPUESTOS = "impuestos"
    const val INTERESES_COMISIONES = "intereses_comisiones"
    const val SALARIO = "salario"
    const val TRANSFERENCIA_RECIBIDA = "transferencia_recibida"
    const val DEPOSITO = "deposito"
    const val CASHBACK = "cashback"
    const val PAGO_TARJETA = "pago_tarjeta"
    const val SIN_CATEGORIZAR = "sin_categorizar"

    data class Definicion(
        val id: String,
        val nombre: String,
    )

    val categorias = listOf(
        Definicion(ALIMENTACION, "Alimentación"),
        Definicion(TRANSPORTE, "Transporte"),
        Definicion(SALUD, "Salud"),
        Definicion(ENTRETENIMIENTO, "Entretenimiento"),
        Definicion(SUSCRIPCIONES, "Suscripciones"),
        Definicion(SERVICIOS, "Servicios"),
        Definicion(COMPRAS, "Compras"),
        Definicion(ATM, "Retiro ATM"),
        Definicion(TRANSFERENCIA_ENVIADA, "Transferencia enviada"),
        Definicion(IMPUESTOS, "Impuestos"),
        Definicion(INTERESES_COMISIONES, "Intereses y comisiones"),
        Definicion(SALARIO, "Salario"),
        Definicion(TRANSFERENCIA_RECIBIDA, "Transferencia recibida"),
        Definicion(DEPOSITO, "Depósito"),
        Definicion(CASHBACK, "Cashback"),
        Definicion(PAGO_TARJETA, "Pago a tarjeta"),
        Definicion(SIN_CATEGORIZAR, "Sin categorizar"),
    )

    private val categoriasPorId = categorias.associateBy { it.id }
    private val aliasNormalizados = mapOf(
        "compra" to COMPRAS,
        "compras" to COMPRAS,
        "sin categorizar" to SIN_CATEGORIZAR,
        "sin_categorizar" to SIN_CATEGORIZAR,
    )

    fun normalizarId(categoriaId: String?): String? {
        val cruda = categoriaId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return aliasNormalizados[cruda.lowercase()] ?: cruda
    }

    fun nombreDe(categoriaId: String?): String =
        normalizarId(categoriaId)?.let { categoriasPorId[it]?.nombre } ?: categoriasPorId[SIN_CATEGORIZAR]!!.nombre
}
