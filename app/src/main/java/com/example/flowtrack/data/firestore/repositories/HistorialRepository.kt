package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.EstadoCarga
import com.google.firebase.Timestamp
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

    /**
     * Soft-delete de una carga: borra físicamente las subcollecciones (transacciones,
     * movimientos, estados), limpia el documento de cuenta/tarjeta si no quedan más datos,
     * y marca la carga como ELIMINADO (preserva auditoría).
     */
    suspend fun eliminarTransaccionesDeCarga(uid: String, cargaId: String): AppResult<Unit> {
        return try {
            val refUsuario = firestore.collection("usuarios").document(uid)

            // Leer la carga para saber qué cuenta/tarjeta limpiar después
            val cargaDoc = refUsuario.collection("cargas").document(cargaId).get().await()
            val cuentaId = cargaDoc.getString("cuentaId")
            val tarjetaId = cargaDoc.getString("tarjetaId")

            // Borrar datos reales de todas las subcollecciones
            borrarColeccionPorCargaId(refUsuario.collection("transacciones"), cargaId)
            borrarColeccionPorCargaId(refUsuario.collection("movimientosTarjeta"), cargaId)
            borrarColeccionPorCargaId(refUsuario.collection("estadosTarjeta"), cargaId)

            // Si no quedan transacciones para la cuenta, eliminar el documento de cuenta
            if (!cuentaId.isNullOrBlank()) {
                val restantes = refUsuario.collection("transacciones")
                    .whereEqualTo("cuentaId", cuentaId).limit(1).get().await()
                if (restantes.isEmpty) {
                    refUsuario.collection("cuentas").document(cuentaId).delete().await()
                }
            }

            // Si no quedan movimientos para la tarjeta, eliminar el documento de tarjeta
            if (!tarjetaId.isNullOrBlank()) {
                val restantes = refUsuario.collection("movimientosTarjeta")
                    .whereEqualTo("tarjetaId", tarjetaId).limit(1).get().await()
                if (restantes.isEmpty) {
                    refUsuario.collection("tarjetas").document(tarjetaId).delete().await()
                }
            }

            // Soft-delete del documento de carga (queda visible en auditoría)
            refUsuario.collection("cargas").document(cargaId).update(
                mapOf(
                    "estado" to EstadoCarga.ELIMINADO.name,
                    "eliminadoEn" to Timestamp.now(),
                )
            ).await()

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

            // Borrar físicamente todas las subcollecciones en bloque
            borrarColeccionCompleta(refUsuario.collection("transacciones"))
            borrarColeccionCompleta(refUsuario.collection("movimientosTarjeta"))
            borrarColeccionCompleta(refUsuario.collection("estadosTarjeta"))
            borrarColeccionCompleta(refUsuario.collection("cuentas"))
            borrarColeccionCompleta(refUsuario.collection("tarjetas"))

            // Soft-delete de todas las cargas no eliminadas
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
}
