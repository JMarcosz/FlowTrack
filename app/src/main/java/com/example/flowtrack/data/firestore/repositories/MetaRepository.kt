package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.money
import com.example.flowtrack.domain.model.Meta
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("metas")

    fun observarMetas(uid: String): Flow<List<Meta>> =
        offlineStore.observeMetas(uid)
            .onStart {
                if (!offlineStore.hasRecords("META", uid)) {
                    syncRemote(uid)
                }
            }

    suspend fun guardarMeta(meta: Meta): AppResult<Unit> = try {
        offlineStore.upsertMeta(meta)
        colRef(meta.uidUsuario).document(meta.id).set(meta.toMetaDto()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error guardando meta: ${e.message}", e))
    }

    suspend fun actualizarMonto(uid: String, id: String, nuevoMonto: BigDecimal): AppResult<Unit> = try {
        val meta = offlineStore.getMetas(uid).firstOrNull { it.id == id }
        if (meta != null) offlineStore.upsertMeta(meta.copy(montoActual = nuevoMonto))
        colRef(uid).document(id).update("montoActual", nuevoMonto.toPlainString()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error actualizando meta: ${e.message}", e))
    }

    suspend fun eliminarMeta(uid: String, id: String): AppResult<Unit> = try {
        val meta = offlineStore.getMetas(uid).firstOrNull { it.id == id }
        if (meta != null) offlineStore.upsertMeta(meta.copy(activa = false))
        colRef(uid).document(id).update("activa", false).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error eliminando meta: ${e.message}", e))
    }

    private suspend fun syncRemote(uid: String) {
        runCatching {
            val snapshot = colRef(uid)
                .whereEqualTo("activa", true)
                .get()
                .await()
            val metas = snapshot.documents.mapNotNull { doc -> doc.toMetaCompat() }
            if (metas.isNotEmpty()) offlineStore.upsertMetas(metas)
        }
    }
}

private fun DocumentSnapshot.toMetaCompat(): Meta? = runCatching {
    Meta(
        id = id,
        uidUsuario = getString("uidUsuario") ?: return null,
        nombre = getString("nombre") ?: "",
        emoji = getString("emoji") ?: "",
        montoObjetivo = money("montoObjetivo") ?: BigDecimal.ZERO.setScale(2),
        montoActual = money("montoActual") ?: BigDecimal.ZERO.setScale(2),
        fechaLimite = getTimestamp("fechaLimite")?.toDate()?.toInstant(),
        activa = getBoolean("activa") ?: true,
        creadoEn = getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now(),
    )
}.getOrNull()

private fun Meta.toMetaDto() = com.example.flowtrack.data.firestore.dto.MetaDto(
    id = id,
    uidUsuario = uidUsuario,
    nombre = nombre,
    emoji = emoji,
    montoObjetivo = montoObjetivo.toPlainString(),
    montoActual = montoActual.toPlainString(),
    fechaLimite = fechaLimite?.let { com.google.firebase.Timestamp(java.util.Date.from(it)) },
    activa = activa,
    creadoEn = com.google.firebase.Timestamp(java.util.Date.from(creadoEn)),
)
