package com.example.flowtrack.presentation.components

import androidx.compose.ui.graphics.Color
import com.example.flowtrack.domain.model.CategoriaCatalogo
import com.example.flowtrack.ui.theme.*

data class CategoriaUI(
    val id: String,
    val nombre: String,
    val color: Color,
)

// Colores alineados al design system §1.4.
val categoriaRegistry: Map<String, CategoriaUI> = CategoriaCatalogo.categorias
    .map { definicion ->
        CategoriaUI(
            id = definicion.id,
            nombre = definicion.nombre,
            color = colorParaCategoria(definicion.id),
        )
    }
    .associateBy { it.id }

fun categoriaPorId(id: String?): CategoriaUI {
    val normalized = CategoriaCatalogo.normalizarId(id)
    return categoriaRegistry[normalized] ?: categoriaRegistry[CategoriaCatalogo.SIN_CATEGORIZAR]!!
}

private fun colorParaCategoria(id: String): Color = when (id) {
    CategoriaCatalogo.ALIMENTACION -> CatAlimentacion
    CategoriaCatalogo.TRANSPORTE -> CatTransporte
    CategoriaCatalogo.SALUD -> CatSalud
    CategoriaCatalogo.ENTRETENIMIENTO -> CatPagos
    CategoriaCatalogo.SUSCRIPCIONES -> CatSuscripciones
    CategoriaCatalogo.SERVICIOS -> CatServicios
    CategoriaCatalogo.COMPRAS -> CatCompras
    CategoriaCatalogo.ATM -> CatAtm
    CategoriaCatalogo.TRANSFERENCIA_ENVIADA -> CatOtros
    CategoriaCatalogo.IMPUESTOS -> CatImpuestos
    CategoriaCatalogo.INTERESES_COMISIONES -> CatServicios
    CategoriaCatalogo.SALARIO -> CatIngresos
    CategoriaCatalogo.TRANSFERENCIA_RECIBIDA -> CatIngresos
    CategoriaCatalogo.DEPOSITO -> CatIngresos
    CategoriaCatalogo.CASHBACK -> CatCashback
    CategoriaCatalogo.PAGO_TARJETA -> CatPagos
    CategoriaCatalogo.SIN_CATEGORIZAR -> CatSinCategorizar
    else -> CatSinCategorizar
}
