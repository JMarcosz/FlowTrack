package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.EstadoCarga
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistorialRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid).collection("cargas")

    /** Flow reactivo: actualiza automÃ¡ticamente cuando se importa o elimina una carga. */
    fun observarCargas(uid: String, limite: Int = 20): Flow<List<Carga>> =
        offlineStore.observeCargas(uid, limite)
            .onStart {
                if (!offlineStore.hasRecords("CARGA", uid)) {
                    syncRemote(uid, limite)
                }
            }

    suspend fun obtenerCargas(uid: String, limite: Int = 50): AppResult<List<Carga>> {
        return try {
            val local = offlineStore.getCargas(uid, limite)
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            syncRemote(uid, limite)
            AppResult.Success(offlineStore.getCargas(uid, limite))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar historial: ${e.message}", e))
        }
    }

    /**
     * Soft-delete de una carga: borra fÃ­sicamente las subcollecciones (transacciones,
     * movimientos, estados), limpia el documento de cuenta/tarjeta si no quedan mÃ¡s datos,
     * y marca la carga como ELIMINADO (preserva auditorÃ­a).
     */
    suspend fun eliminarTransaccionesDeCarga(uid: String, cargaId: String): AppResult<Unit> {
        return try {
            val refUsuario = firestore.collection("usuarios").document(uid)

            val cargaDoc = refUsuario.collection("cargas").document(cargaId).get().await()
            val cuentaId = cargaDoc.getString("cuentaId")
            val tarjetaId = cargaDoc.getString("tarjetaId")

            borrarColeccionPorCargaId(refUsuario.collection("transacciones"), cargaId)
            borrarColeccionPorCargaId(refUsuario.collection("movimientosTarjeta"), cargaId)
            borrarColeccionPorCargaId(refUsuario.collection("estadosTarjeta"), cargaId)

            offlineStore.deleteByCargaId("TRANSACCION", uid, cargaId)
            offlineStore.deleteByCargaId("MOVIMIENTO_TARJETA", uid, cargaId)
            offlineStore.deleteByCargaId("ESTADO_TARJETA", uid, cargaId)

            if (!cuentaId.isNullOrBlank()) {
                val restantes = refUsuario.collection("transacciones")
                    .whereEqualTo("cuentaId", cuentaId).limit(1).get().await()
                if (restantes.isEmpty) {
                    refUsuario.collection("cuentas").document(cuentaId).delete().await()
                    offlineStore.deleteById("CUENTA", uid, cuentaId)
                }
            }

            if (!tarjetaId.isNullOrBlank()) {
                val restantes = refUsuario.collection("movimientosTarjeta")
                    .whereEqualTo("tarjetaId", tarjetaId).limit(1).get().await()
                if (restantes.isEmpty) {
                    refUsuario.collection("tarjetas").document(tarjetaId).delete().await()
                    offlineStore.deleteById("TARJETA", uid, tarjetaId)
                }
            }

            refUsuario.collection("cargas").document(cargaId).update(
                mapOf(
                    "estado" to EstadoCarga.ELIMINADO.name,
                    "eliminadoEn" to Timestamp.now(),
                )
            ).await()
            offlineStore.deleteById("CARGA", uid, cargaId)

            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar carga: ${e.message}", e))
        }
    }

    /**
     * Elimina todas las cargas no eliminadas del usuario, junto con todas las subcollecciones
     * y los documentos de cuentas y tarjetas.
     */
    suspend fun eliminarTodo(uid: String): AppResult<Unit> {
        return try {
            val refUsuario = firestore.collection("usuarios").document(uid)

            borrarColeccionCompleta(refUsuario.collection("transacciones"))
            borrarColeccionCompleta(refUsuario.collection("movimientosTarjeta"))
            borrarColeccionCompleta(refUsuario.collection("estadosTarjeta"))
            borrarColeccionCompleta(refUsuario.collection("cuentas"))
            borrarColeccionCompleta(refUsuario.collection("tarjetas"))

            val snapshot = refUsuario.collection("cargas")
                .whereNotEqualTo("estado", EstadoCarga.ELIMINADO.name)
                .get().await()
            snapshot.documents.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf("estado" to EstadoCarga.ELIMINADO.name, "eliminadoEn" to Timestamp.now())
                    )
                }
                batch.commit().await()
            }

            offlineStore.clearUser(uid)

            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar todo: ${e.message}", e))
        }
    }

    private suspend fun borrarColeccionPorCargaId(
        colRef: com.google.firebase.firestore.CollectionReference,
        cargaId: String,
    ) {
        val snapshot = colRef.whereEqualTo("cargaId", cargaId).get().await()
        snapshot.documents.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
    }

    private suspend fun borrarColeccionCompleta(
        colRef: com.google.firebase.firestore.CollectionReference,
    ) {
        val snapshot = colRef.get().await()
        snapshot.documents.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
    }

    private suspend fun syncRemote(uid: String, limite: Int) {
        runCatching {
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
            if (cargas.isNotEmpty()) offlineStore.upsertCargas(cargas)
        }
    }
}
