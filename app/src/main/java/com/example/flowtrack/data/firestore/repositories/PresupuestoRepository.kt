package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.firestore.asListFlow
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.PresupuestoDto
import com.example.flowtrack.domain.model.PeriodoPresupuesto
import com.example.flowtrack.domain.model.Presupuesto
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
class PresupuestoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("presupuestos")

    fun observarPresupuestos(uid: String): Flow<List<Presupuesto>> =
        colRef(uid)
            .whereEqualTo("activo", true)
            .asListFlow(PresupuestoDto::class.java)
            .map { dtos -> dtos.mapNotNull { it.toDomain() } }

    suspend fun guardarPresupuesto(presupuesto: Presupuesto): AppResult<Unit> = try {
        colRef(presupuesto.uidUsuario).document(presupuesto.id).set(presupuesto.toDto()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error guardando presupuesto: ${e.message}", e))
    }

    suspend fun eliminarPresupuesto(uid: String, id: String): AppResult<Unit> = try {
        colRef(uid).document(id).update("activo", false).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error eliminando presupuesto: ${e.message}", e))
    }
}

private fun PresupuestoDto.toDomain(): Presupuesto? = runCatching {
    Presupuesto(
        id = id,
        uidUsuario = uidUsuario,
        categoriaId = categoriaId,
        montoLimite = BigDecimal.valueOf(montoLimite),
        periodo = PeriodoPresupuesto.valueOf(periodo),
        activo = activo,
        creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
    )
}.getOrNull()

private fun Presupuesto.toDto() = PresupuestoDto(
    id = id,
    uidUsuario = uidUsuario,
    categoriaId = categoriaId,
    montoLimite = montoLimite.toDouble(),
    periodo = periodo.name,
    activo = activo,
    creadoEn = Timestamp(java.util.Date.from(creadoEn)),
)
