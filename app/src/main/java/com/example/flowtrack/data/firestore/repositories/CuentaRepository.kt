package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.CuentaDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.Cuenta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuentaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Obtiene todas las cuentas del usuario, activas o inactivas.
     */
    suspend fun obtenerCuentas(uid: String): AppResult<List<Cuenta>> {
        return try {
            val snapshot = firestore
                .collection("usuarios").document(uid)
                .collection("cuentas")
                .orderBy("creadoEn", Query.Direction.ASCENDING)
                .get()
                .await()

            val cuentas = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CuentaDto::class.java)?.toDomain()
            }
            AppResult.Success(cuentas)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar cuentas: ${e.message}", e))
        }
    }

    suspend fun guardarCuenta(cuenta: Cuenta): AppResult<Unit> {
        return try {
            firestore.collection("usuarios").document(cuenta.uidUsuario)
                .collection("cuentas").document(cuenta.id)
                .set(cuenta.toDto())
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al guardar cuenta: ${e.message}", e))
        }
    }

    suspend fun actualizarCuenta(cuenta: Cuenta): AppResult<Unit> {
        return try {
            firestore.collection("usuarios").document(cuenta.uidUsuario)
                .collection("cuentas").document(cuenta.id)
                .set(cuenta.toDto(), SetOptions.merge())
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al actualizar cuenta: ${e.message}", e))
        }
    }

    /** Soft-delete: marca como inactiva y oculta en dashboard. */
    suspend fun eliminarCuenta(uid: String, cuentaId: String): AppResult<Unit> {
        return try {
            firestore.collection("usuarios").document(uid)
                .collection("cuentas").document(cuentaId)
                .update(
                    mapOf(
                        "activa" to false,
                        "mostrarEnDashboard" to false,
                    )
                )
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar cuenta: ${e.message}", e))
        }
    }
}
