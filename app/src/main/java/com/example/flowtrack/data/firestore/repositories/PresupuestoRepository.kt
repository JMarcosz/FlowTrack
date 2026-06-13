package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.money
import com.example.flowtrack.domain.model.PeriodoPresupuesto
import com.example.flowtrack.domain.model.Presupuesto
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
class PresupuestoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("presupuestos")

    fun observarPresupuestos(uid: String): Flow<List<Presupuesto>> =
        offlineStore.observePresupuestos(uid)
            .onStart {
                if (!offlineStore.hasRecords("PRESUPUESTO", uid)) {
                    syncRemote(uid)
                }
            }

    suspend fun guardarPresupuesto(presupuesto: Presupuesto): AppResult<Unit> = try {
        offlineStore.upsertPresupuesto(presupuesto)
        colRef(presupuesto.uidUsuario).document(presupuesto.id).set(presupuesto.toPresupuestoDto()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error guardando presupuesto: ${e.message}", e))
    }

    suspend fun eliminarPresupuesto(uid: String, id: String): AppResult<Unit> = try {
        val presupuesto = offlineStore.getPresupuestos(uid).firstOrNull { it.id == id }
        if (presupuesto != null) offlineStore.upsertPresupuesto(presupuesto.copy(activo = false))
        colRef(uid).document(id).update("activo", false).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error eliminando presupuesto: ${e.message}", e))
    }

    private suspend fun syncRemote(uid: String) {
        runCatching {
            val snapshot = colRef(uid)
                .whereEqualTo("activo", true)
                .get()
                .await()
            val presupuestos = snapshot.documents.mapNotNull { doc -> doc.toPresupuestoCompat() }
            if (presupuestos.isNotEmpty()) offlineStore.upsertPresupuestos(presupuestos)
        }
    }
}

private fun DocumentSnapshot.toPresupuestoCompat(): Presupuesto? = runCatching {
    Presupuesto(
        id = id,
        uidUsuario = getString("uidUsuario") ?: return null,
        categoriaId = getString("categoriaId") ?: "",
        montoLimite = money("montoLimite") ?: BigDecimal.ZERO.setScale(2),
        periodo = PeriodoPresupuesto.valueOf(getString("periodo") ?: return null),
        activo = getBoolean("activo") ?: true,
        creadoEn = getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now(),
    )
}.getOrNull()

private fun Presupuesto.toPresupuestoDto() = com.example.flowtrack.data.firestore.dto.PresupuestoDto(
    id = id,
    uidUsuario = uidUsuario,
    categoriaId = categoriaId,
    montoLimite = montoLimite.toPlainString(),
    periodo = periodo.name,
    activo = activo,
    creadoEn = com.google.firebase.Timestamp(java.util.Date.from(creadoEn)),
)
