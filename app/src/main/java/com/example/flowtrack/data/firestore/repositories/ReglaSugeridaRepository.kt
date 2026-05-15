package com.example.flowtrack.data.firestore.repositories

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

@Singleton
class ReglaSugeridaRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun guardarReglas(uid: String, reglas: List<ReglaSugerida>): AppResult<Unit> {
        return try {
            val batch = firestore.batch()
            for (regla in reglas) {
                val ref = firestore
                    .collection("usuarios").document(uid)
                    .collection("reglasSugeridas").document(regla.id)
                
                val dto = mapOf(
                    "id" to regla.id,
                    "uidUsuario" to regla.uidUsuario,
                    "patronDetectado" to regla.patronDetectado,
                    "categoriaSugerida" to regla.categoriaSugerida,
                    "muestras" to regla.muestras,
                    "confianzaCluster" to regla.confianzaCluster,
                    "creadaEn" to Timestamp(regla.creadaEn.epochSecond, regla.creadaEn.nano),
                    "aceptada" to regla.aceptada,
                    "resueltaEn" to regla.resueltaEn?.let { Timestamp(it.epochSecond, it.nano) }
                )
                batch.set(ref, dto)
            }
            batch.commit().await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error guardando sugerencias: ${e.message}", e))
        }
    }

    suspend fun obtenerPendientes(uid: String): AppResult<List<ReglaSugerida>> {
        return try {
            val snapshot = firestore
                .collection("usuarios").document(uid)
                .collection("reglasSugeridas")
                .whereEqualTo("aceptada", null)
                .orderBy("creadaEn", Query.Direction.DESCENDING)
                .get().await()

            val reglas = snapshot.documents.mapNotNull { doc ->
                ReglaSugerida(
                    id = doc.id,
                    uidUsuario = doc.getString("uidUsuario") ?: uid,
                    patronDetectado = doc.getString("patronDetectado") ?: "",
                    categoriaSugerida = doc.getString("categoriaSugerida") ?: "sin_categorizar",
                    muestras = (doc.get("muestras") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                    confianzaCluster = doc.getDouble("confianzaCluster")?.toFloat() ?: 0f,
                    creadaEn = doc.getTimestamp("creadaEn")?.toDate()?.toInstant() ?: Instant.now(),
                    aceptada = doc.getBoolean("aceptada"),
                    resueltaEn = doc.getTimestamp("resueltaEn")?.toDate()?.toInstant()
                )
            }
            AppResult.Success(reglas)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error leyendo sugerencias: ${e.message}", e))
        }
    }

    suspend fun resolverSugerencia(uid: String, reglaId: String, aceptada: Boolean): AppResult<Unit> {
        return try {
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
