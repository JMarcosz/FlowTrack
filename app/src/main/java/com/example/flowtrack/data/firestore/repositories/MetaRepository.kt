package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.firestore.asListFlow
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.MetaDto
import com.example.flowtrack.domain.model.Meta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("metas")

    fun observarMetas(uid: String): Flow<List<Meta>> =
        colRef(uid)
            .whereEqualTo("activa", true)
            .asListFlow(MetaDto::class.java)
            .map { dtos -> dtos.mapNotNull { it.toDomain() } }

    suspend fun guardarMeta(meta: Meta): AppResult<Unit> = try {
        colRef(meta.uidUsuario).document(meta.id).set(meta.toDto()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error guardando meta: ${e.message}", e))
    }

    suspend fun actualizarMonto(uid: String, id: String, nuevoMonto: BigDecimal): AppResult<Unit> = try {
        colRef(uid).document(id).update("montoActual", nuevoMonto.toDouble()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error actualizando meta: ${e.message}", e))
    }

    suspend fun eliminarMeta(uid: String, id: String): AppResult<Unit> = try {
        colRef(uid).document(id).update("activa", false).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error eliminando meta: ${e.message}", e))
    }
}

private fun MetaDto.toDomain(): Meta? = runCatching {
    Meta(
        id = id,
        uidUsuario = uidUsuario,
        nombre = nombre,
        emoji = emoji,
        montoObjetivo = BigDecimal.valueOf(montoObjetivo),
        montoActual = BigDecimal.valueOf(montoActual),
        fechaLimite = fechaLimite?.toDate()?.toInstant(),
        activa = activa,
        creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
    )
}.getOrNull()

private fun Meta.toDto() = MetaDto(
    id = id,
    uidUsuario = uidUsuario,
    nombre = nombre,
    emoji = emoji,
    montoObjetivo = montoObjetivo.toDouble(),
    montoActual = montoActual.toDouble(),
    fechaLimite = fechaLimite?.let { Timestamp(java.util.Date.from(it)) },
    activa = activa,
    creadoEn = Timestamp(java.util.Date.from(creadoEn)),
)
