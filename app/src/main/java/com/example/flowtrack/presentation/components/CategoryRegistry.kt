package com.example.flowtrack.presentation.components

import androidx.compose.ui.graphics.Color
import com.example.flowtrack.ui.theme.*

data class CategoriaUI(
    val id: String,
    val nombre: String,
    val color: Color,
)

val categoriaRegistry: Map<String, CategoriaUI> = listOf(
    CategoriaUI("alimentacion",           "Alimentación",             CatAlimentacion),
    CategoriaUI("transporte",             "Transporte",               CatTransporte),
    CategoriaUI("salud",                  "Salud",                    CatSalud),
    CategoriaUI("entretenimiento",        "Entretenimiento",          CatEntretenimiento),
    CategoriaUI("suscripciones",          "Suscripciones",            CatSuscripciones),
    CategoriaUI("servicios",              "Servicios",                CatServicios),
    CategoriaUI("compras",                "Compras",                  CatCompras),
    CategoriaUI("atm",                    "Retiro ATM",               CatAtm),
    CategoriaUI("transferencia_enviada",  "Transferencia enviada",    CatTransferenciaEnviada),
    CategoriaUI("impuestos",              "Impuestos",                CatImpuestos),
    CategoriaUI("intereses_comisiones",   "Intereses y comisiones",   CatIntereses),
    CategoriaUI("salario",                "Salario",                  CatSalario),
    CategoriaUI("transferencia_recibida", "Transferencia recibida",   CatTransferenciaRecibida),
    CategoriaUI("deposito",               "Depósito",                 CatDeposito),
    CategoriaUI("cashback",               "Cashback",                 CatCashback),
    CategoriaUI("pago_tarjeta",           "Pago a tarjeta",           CatPagoTarjeta),
    CategoriaUI("sin_categorizar",        "Sin categorizar",          CatSinCategorizar),
).associateBy { it.id }

fun categoriaPorId(id: String): CategoriaUI =
    categoriaRegistry[id] ?: categoriaRegistry["sin_categorizar"]!!
