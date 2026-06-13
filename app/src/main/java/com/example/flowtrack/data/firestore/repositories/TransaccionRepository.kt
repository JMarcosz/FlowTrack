package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.firestore.asListFlow
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.data.local.TransaccionesCursor
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.repository.ITransaccionRepository
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
    val lastVisible: TransaccionesCursor?,
    val hasMore: Boolean,
)

@Singleton
class TransaccionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) : ITransaccionRepository {

    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("transacciones")

    override fun observarTransaccionesRecientes(
        uid: String,
        inicio: Instant,
        fin: Instant,
        limite: Int,
    ): Flow<List<Transaccion>> = offlineStore.observeTransacciones(uid, inicio, fin, limite)

    override suspend fun obtenerTransaccionesPage(
        uid: String,
        lastVisible: TransaccionesCursor?,
        pageSize: Int,
        inicio: Instant?,
        fin: Instant?
    ): AppResult<TransaccionesPage> {
        return try {
            val (localPage, localCursor) = offlineStore.getTransaccionesPage(
                uid = uid,
                inicio = inicio,
                fin = fin,
                cursor = lastVisible,
                pageSize = pageSize,
            )
            AppResult.Success(TransaccionesPage(localPage, localCursor, localCursor != null))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar página: ${e.message}", e))
        }
    }

    override suspend fun obtenerTransacciones(
        uid: String,
        inicio: Instant?,
        fin: Instant?,
        limite: Int,
    ): AppResult<List<Transaccion>> {
        return try {
            val local = offlineStore.getTransacciones(uid, inicio, fin, limite)
            AppResult.Success(local)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar transacciones: ${e.message}", e))
        }
    }

    override suspend fun obtenerDerivadas(uid: String, padreId: String): AppResult<List<Transaccion>> {
        return try {
            val derivadas = offlineStore.getDerivadas(uid, padreId)
            AppResult.Success(derivadas)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar derivadas: ${e.message}", e))
        }
    }

    override suspend fun actualizarTransaccion(tx: Transaccion): AppResult<Unit> {
        return try {
            offlineStore.upsertTransaccion(tx)
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

    override suspend fun eliminarTransaccion(uid: String, txId: String): AppResult<Unit> {
        return try {
            offlineStore.markTransaccionDeleted(uid, txId)
            colRef(uid).document(txId).delete().await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar transacción: ${e.message}", e))
        }
    }

    override suspend fun guardarTransaccionesEnLote(uid: String, transacciones: List<Transaccion>): AppResult<Unit> {
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
