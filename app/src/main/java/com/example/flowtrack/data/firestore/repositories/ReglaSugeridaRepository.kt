package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.ReglaSugerida
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.data.firestore.mappers.toReglaSugeridaDto

@Singleton
class ReglaSugeridaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    suspend fun guardarReglas(uid: String, reglas: List<ReglaSugerida>): AppResult<Unit> {
        return try {
            offlineStore.upsertReglasSugeridas(reglas)
            val batch = firestore.batch()
            for (regla in reglas) {
                val ref = firestore
                    .collection("usuarios").document(uid)
                    .collection("reglasSugeridas").document(regla.id)
                
                batch.set(ref, regla.toDto())
            }
            batch.commit().await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error guardando sugerencias: ${e.message}", e))
        }
    }

    suspend fun obtenerPendientes(uid: String): AppResult<List<ReglaSugerida>> {
        return try {
            val local = offlineStore.getReglasSugeridas(uid).filter { it.aceptada == null }
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            runCatching {
                val snapshot = firestore
                    .collection("usuarios").document(uid)
                    .collection("reglasSugeridas")
                    .whereEqualTo("aceptada", null)
                    .orderBy("creadaEn", Query.Direction.DESCENDING)
                    .get().await()

                val reglas = snapshot.documents.mapNotNull { doc ->
                    doc.toReglaSugeridaDto()?.toDomain()
                }
                if (reglas.isNotEmpty()) offlineStore.upsertReglasSugeridas(reglas)
            }
            val reglas = offlineStore.getReglasSugeridas(uid).filter { it.aceptada == null }
            AppResult.Success(reglas)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error leyendo sugerencias: ${e.message}", e))
        }
    }

    suspend fun resolverSugerencia(uid: String, reglaId: String, aceptada: Boolean): AppResult<Unit> {
        return try {
            val local = offlineStore.getReglasSugeridas(uid).firstOrNull { it.id == reglaId }
            if (local != null) {
                offlineStore.upsertReglaSugerida(
                    local.copy(
                        aceptada = aceptada,
                        resueltaEn = Instant.now(),
                    )
                )
            }
            firestore
                .collection("usuarios").document(uid)
                .collection("reglasSugeridas").document(reglaId)
                .update(
                    "aceptada", aceptada,
                    "resueltaEn", Timestamp.now()
                ).await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error resolviendo sugerencia: ${e.message}", e))
        }
    }
}
