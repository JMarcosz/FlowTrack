package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.TipoMatch
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReglaCategoriaRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun crearReglaPersonal(
        uidUsuario: String,
        patron: String,
        categoriaId: String,
        tipoMatch: TipoMatch = TipoMatch.CONTIENE
    ): AppResult<ReglaCategoria> {
        return try {
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
                creadoEn = Instant.now()
            )

            val dto = mapOf(
                "id" to regla.id,
                "uidUsuario" to regla.uidUsuario,
                "patron" to regla.patron,
                "tipoMatch" to regla.tipoMatch.name,
                "categoriaId" to regla.categoriaId,
                "prioridad" to regla.prioridad,
                "confianza" to regla.confianza,
                "activa" to regla.activa,
                "creadoPor" to regla.creadoPor,
                "creadoEn" to Timestamp(regla.creadoEn.epochSecond, regla.creadoEn.nano)
            )

            firestore
                .collection("usuarios").document(uidUsuario)
                .collection("reglasCategorias").document(id)
                .set(dto)
                .await()

            AppResult.Success(regla)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al guardar regla: ${e.message}", e))
        }
    }
}
