package com.example.flowtrack.presentation.components

import androidx.compose.ui.graphics.Color
import com.example.flowtrack.ui.theme.*

data class CategoriaUI(
    val id: String,
    val nombre: String,
    val color: Color,
)

// Colores alineados al design system §1.4
val categoriaRegistry: Map<String, CategoriaUI> = listOf(
    CategoriaUI("alimentacion",           "Alimentación",             CatAlimentacion),
    CategoriaUI("transporte",             "Transporte",               CatTransporte),
    CategoriaUI("salud",                  "Salud",                    CatSalud),
    CategoriaUI("entretenimiento",        "Entretenimiento",          CatPagos),
    CategoriaUI("suscripciones",          "Suscripciones",            CatSuscripciones),
    CategoriaUI("servicios",              "Servicios",                CatServicios),
    CategoriaUI("compras",                "Compras",                  CatCompras),
    CategoriaUI("atm",                    "Retiro ATM",               CatAtm),
    CategoriaUI("transferencia_enviada",  "Transferencia enviada",    CatOtros),
    CategoriaUI("impuestos",              "Impuestos",                CatImpuestos),
    CategoriaUI("intereses_comisiones",   "Intereses y comisiones",   CatServicios),
    CategoriaUI("salario",                "Salario",                  CatIngresos),
    CategoriaUI("transferencia_recibida", "Transferencia recibida",   CatIngresos),
    CategoriaUI("deposito",               "Depósito",                 CatIngresos),
    CategoriaUI("cashback",               "Cashback",                 CatCashback),
    CategoriaUI("pago_tarjeta",           "Pago a tarjeta",           CatPagos),
    CategoriaUI("sin_categorizar",        "Sin categorizar",          CatSinCategorizar),
).associateBy { it.id }

fun categoriaPorId(id: String): CategoriaUI =
    categoriaRegistry[id] ?: categoriaRegistry["sin_categorizar"]!!
