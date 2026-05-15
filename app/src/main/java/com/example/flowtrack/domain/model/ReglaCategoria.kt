package com.example.flowtrack.domain.model

import java.time.Instant

data class ReglaCategoria(
    val id: String,
    val uidUsuario: String?,                // null si es global (SISTEMA)
    val patron: String,                     // texto a buscar, ya normalizado
    val tipoMatch: TipoMatch,
    val categoriaId: String,
    val prioridad: Int,                     // mayor = se aplica primero
    val confianza: Int,                     // cuántas veces ha matcheado
    val activa: Boolean = true,
    val creadoPor: String,                  // "SISTEMA" o uid del usuario
    val creadoEn: Instant,
)

/** Regla sugerida por el motor de clustering — pendiente de aceptación por el usuario. */
data class ReglaSugerida(
    val id: String,
    val uidUsuario: String,
    val patronDetectado: String,            // descripción normalizada del cluster
    val categoriaSugerida: String,
    val muestras: List<String>,             // IDs de transacciones que forman el cluster
    val confianzaCluster: Float,
    val creadaEn: Instant,
    val aceptada: Boolean? = null,          // null = pendiente, true = aceptada, false = rechazada
    val resueltaEn: Instant? = null,
)
