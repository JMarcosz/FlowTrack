package com.example.flowtrack.domain.model

import java.time.Instant

data class DispositivoPush(
    val id: String,
    val uidUsuario: String,
    val tokenFcm: String,
    val activo: Boolean = true,
    val actualizadoEn: Instant = Instant.now(),
    val ultimoUsuarioUid: String? = null,
    val versionApp: String? = null,
    val modeloDispositivo: String? = null,
)
