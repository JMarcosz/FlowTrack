package com.example.flowtrack.domain.model

data class BancoSoportado(
    val codigo: String,
    val nombre: String,
    val formatosPermitidos: List<FormatoArchivo>,
    val productoTipo: ProductoTipo,
    val disponible: Boolean = true,
)
