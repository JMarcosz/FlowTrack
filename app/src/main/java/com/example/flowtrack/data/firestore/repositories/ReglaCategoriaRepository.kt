package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.data.firestore.mappers.toReglaCategoriaDto
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReglaCategoriaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    private fun colRef(uidUsuario: String) = firestore
        .collection("usuarios").document(uidUsuario)
        .collection("reglasCategorias")

    suspend fun obtenerReglasPersonales(uidUsuario: String): AppResult<List<ReglaCategoria>> {
        return try {
            val local = offlineStore.getReglasCategoria(uidUsuario)
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            runCatching {
                val snapshot = colRef(uidUsuario)
                    .whereEqualTo("activa", true)
                    .orderBy("prioridad", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                val reglas = snapshot.documents.mapNotNull { doc ->
                    doc.toReglaCategoriaDto()?.toDomain()
                }
                if (reglas.isNotEmpty()) offlineStore.upsertReglasCategoria(reglas)
            }
            AppResult.Success(offlineStore.getReglasCategoria(uidUsuario))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar reglas: ${e.message}", e))
        }
    }

    suspend fun eliminarRegla(uidUsuario: String, reglaId: String): AppResult<Unit> = try {
        offlineStore.deleteById("REGLA_CATEGORIA", uidUsuario, reglaId)
        colRef(uidUsuario).document(reglaId).delete().await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error al eliminar regla: ${e.message}", e))
    }

    suspend fun crearReglaPersonal(
        uidUsuario: String,
        patron: String,
        categoriaId: String,
        tipoMatch: TipoMatch = TipoMatch.CONTIENE,
    ): AppResult<ReglaCategoria> = try {
        val id = UUID.randomUUID().toString()
        val regla = ReglaCategoria(
            id = id,
            uidUsuario = uidUsuario,
            patron = patron,
            tipoMatch = tipoMatch,
            categoriaId = categoriaId,
            prioridad = 10,
            confianza = 1,
            activa = true,
            creadoPor = uidUsuario,
            creadoEn = Instant.now(),
        )

        offlineStore.upsertReglaCategoria(regla)
        colRef(uidUsuario).document(id).set(regla.toDto()).await()
        AppResult.Success(regla)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error al guardar regla: ${e.message}", e))
    }
}
