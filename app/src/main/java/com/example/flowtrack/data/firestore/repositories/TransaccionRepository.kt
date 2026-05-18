package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.firestore.asListFlow
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class TransaccionesPage(
    val transacciones: List<Transaccion>,
    val lastVisible: DocumentSnapshot?,
    val hasMore: Boolean,
)

@Singleton
class TransaccionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("transacciones")

    /** Flow reactivo: emite desde cache local inmediatamente, luego actualiza si hay cambios en servidor. */
    fun observarTransaccionesRecientes(
        uid: String,
        inicio: Instant,
        fin: Instant,
        limite: Int = 100,
    ): Flow<List<Transaccion>> = colRef(uid)
        .orderBy("fecha", Query.Direction.DESCENDING)
        .whereGreaterThanOrEqualTo("fecha", java.util.Date.from(inicio))
        .whereLessThanOrEqualTo("fecha", java.util.Date.from(fin))
        .limit(limite.toLong())
        .asListFlow(TransaccionDto::class.java)
        .map { dtos -> dtos.mapNotNull { it.toDomain() } }
        .flowOn(Dispatchers.Default)

    /**
     * Carga una sola "página" de transacciones. Usa startAfter para cursor-based pagination.
     */
    suspend fun obtenerTransaccionesPage(
        uid: String,
        lastVisible: DocumentSnapshot? = null,
        pageSize: Int = 50,
        inicio: Instant? = null,
        fin: Instant? = null,
    ): AppResult<TransaccionesPage> {
        return try {
            var query: Query = colRef(uid).orderBy("fecha", Query.Direction.DESCENDING)
            if (inicio != null && fin != null) {
                query = query
                    .whereGreaterThanOrEqualTo("fecha", java.util.Date.from(inicio))
                    .whereLessThanOrEqualTo("fecha", java.util.Date.from(fin))
            }
            if (lastVisible != null) query = query.startAfter(lastVisible)
            // Fetch one extra to determine if more pages exist
            val snapshot = query.limit((pageSize + 1).toLong()).get().await()
            val hasMore = snapshot.size() > pageSize
            val docs = if (hasMore) snapshot.documents.dropLast(1) else snapshot.documents
            val transacciones = docs.mapNotNull { it.toObject(TransaccionDto::class.java)?.toDomain() }
            AppResult.Success(TransaccionesPage(transacciones, docs.lastOrNull(), hasMore))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar página: ${e.message}", e))
        }
    }

    /**
     * One-shot: úsalo solo para agregaciones puntuales (use-cases de resumen/comparativa).
     * Para UI reactiva usa [observarTransaccionesRecientes].
     * limite = 0 significa sin límite.
     */
    suspend fun obtenerTransacciones(
        uid: String,
        inicio: Instant? = null,
        fin: Instant? = null,
        limite: Int = 100,
    ): AppResult<List<Transaccion>> {
        return try {
            var query: Query = colRef(uid).orderBy("fecha", Query.Direction.DESCENDING)
            if (inicio != null && fin != null) {
                query = query
                    .whereGreaterThanOrEqualTo("fecha", java.util.Date.from(inicio))
                    .whereLessThanOrEqualTo("fecha", java.util.Date.from(fin))
            }
            val snapshot = if (limite > 0) query.limit(limite.toLong()).get().await()
                           else query.get().await()
            val transacciones = snapshot.documents.mapNotNull { it.toObject(TransaccionDto::class.java)?.toDomain() }
            AppResult.Success(transacciones)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar transacciones: ${e.message}", e))
        }
    }

    /**
     * Actualiza solo los campos editables por el usuario (categoría y notas).
     * Usa update() en lugar de set() para no sobreescribir campos no editados.
     */
    suspend fun actualizarTransaccion(tx: Transaccion): AppResult<Unit> {
        return try {
            val updates = mapOf(
                "categoriaId" to tx.categoriaId,
                "categoriaAutomatica" to tx.categoriaAutomatica,
            )
            colRef(tx.uidUsuario).document(tx.id).update(updates).await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al actualizar transacción: ${e.message}", e))
        }
    }

    suspend fun eliminarTransaccion(uid: String, txId: String): AppResult<Unit> {
        return try {
            colRef(uid).document(txId).delete().await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar transacción: ${e.message}", e))
        }
    }

    /**
     * Guarda transacciones en lote (chunks de 450 bajo el límite de 500 de Firestore).
     */
    suspend fun guardarTransaccionesEnLote(uid: String, transacciones: List<Transaccion>): AppResult<Unit> {
        return try {
            val collRef = colRef(uid)
            for (chunk in transacciones.chunked(450)) {
                val batch = firestore.batch()
                for (tx in chunk) batch.set(collRef.document(tx.id), tx.toDto())
                batch.commit().await()
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error guardando transacciones en lote: ${e.message}", e))
        }
    }
}
