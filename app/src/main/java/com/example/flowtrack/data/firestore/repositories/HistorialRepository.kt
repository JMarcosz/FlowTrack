package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.domain.model.Carga
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Repositorio de solo lectura para el historial de cargas. */
@Singleton
class HistorialRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Obtiene las últimas N cargas del usuario, ordenadas por procesadoEn desc.
     * Límite razonable para historial visible: 50.
     */
    suspend fun obtenerCargas(uid: String, limite: Int = 50): AppResult<List<Carga>> {
        return try {
            val snapshot = firestore
                .collection("usuarios").document(uid)
                .collection("cargas")
                .orderBy("procesadoEn", Query.Direction.DESCENDING)
                .limit(limite.toLong())
                .get()
                .await()

            val cargas = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CargaDto::class.java)?.toDomain()
            }
            AppResult.Success(cargas)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar historial: ${e.message}", e))
        }
    }

    /** Elimina una carga y todas sus transacciones asociadas. */
    suspend fun eliminarCarga(uid: String, cargaId: String): AppResult<Unit> {
        return try {
            // 1. Obtener IDs de transacciones de esta carga
            val txSnapshot = firestore
                .collection("usuarios").document(uid)
                .collection("transacciones")
                .whereEqualTo("cargaId", cargaId)
                .get()
                .await()

            // 2. Eliminar en batches
            val refUsuario = firestore.collection("usuarios").document(uid)
            txSnapshot.documents.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.delete(refUsuario.collection("cargas").document(cargaId))
                batch.commit().await()
            }
            // Si no había transacciones, eliminar solo la carga
            if (txSnapshot.isEmpty) {
                refUsuario.collection("cargas").document(cargaId).delete().await()
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar carga: ${e.message}", e))
        }
    }
}
