package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransaccionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Obtiene transacciones del usuario, opcionalmente filtradas por rango de fechas
     * y ordenadas por fecha descendente.
     */
    suspend fun obtenerTransacciones(
        uid: String,
        inicio: Instant? = null,
        fin: Instant? = null,
        limite: Int = 100
    ): AppResult<List<Transaccion>> {
        return try {
            var query = firestore
                .collection("usuarios").document(uid)
                .collection("transacciones")
                .orderBy("fecha", Query.Direction.DESCENDING)

            if (inicio != null && fin != null) {
                // Firebase timestamp format is handled automatically by the SDK if passing Date or Timestamp
                // We'll map Instant to java.util.Date for Firestore queries
                query = query.whereGreaterThanOrEqualTo("fecha", java.util.Date.from(inicio))
                             .whereLessThanOrEqualTo("fecha", java.util.Date.from(fin))
            }

            val snapshot = query.limit(limite.toLong()).get().await()

            val transacciones = snapshot.documents.mapNotNull { doc ->
                doc.toObject(TransaccionDto::class.java)?.toDomain()
            }
            AppResult.Success(transacciones)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar transacciones: ${e.message}", e))
        }
    }

    /**
     * Actualiza una transacción (ej: cambio de categoría o notas).
     */
    suspend fun actualizarTransaccion(tx: Transaccion): AppResult<Unit> {
        return try {
            firestore
                .collection("usuarios").document(tx.uidUsuario)
                .collection("transacciones").document(tx.id)
                .set(tx.toDto()) // sobrescribe completo
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al actualizar transacción: ${e.message}", e))
        }
    }

    /**
     * Elimina una transacción.
     */
    suspend fun eliminarTransaccion(uid: String, txId: String): AppResult<Unit> {
        return try {
            firestore
                .collection("usuarios").document(uid)
                .collection("transacciones").document(txId)
                .delete()
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar transacción: ${e.message}", e))
        }
    }
}
