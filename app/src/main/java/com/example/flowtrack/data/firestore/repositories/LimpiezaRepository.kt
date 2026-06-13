package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.local.OfflineStore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Subcolecciones del usuario que se borran al limpiar datos. `configuracion` se conserva. */
private val COLECCIONES_USUARIO = listOf(
    "cuentas",
    "tarjetas",
    "transacciones",
    "movimientosTarjeta",
    "estadosTarjeta",
    "reglasCategorias",
    "categorias_personales",
    "reglasSugeridas",
    "cargas",
)

@Singleton
class LimpiezaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    /**
     * Borra todos los documentos de las subcolecciones financieras del usuario.
     * La colección `configuracion` se conserva.
     * Retorna el número total de documentos eliminados.
     */
    suspend fun borrarTodosMisDatos(uid: String): AppResult<Int> {
        return try {
            val refUsuario = firestore.collection("usuarios").document(uid)
            var totalEliminados = 0

            for (coleccion in COLECCIONES_USUARIO) {
                totalEliminados += eliminarColeccion(refUsuario.collection(coleccion))
            }

            offlineStore.clearUser(uid)

            AppResult.Success(totalEliminados)
        } catch (e: Exception) {
            AppResult.Error(
                ErrorApp.FirestoreError("Error al limpiar datos: ${e.message}", e)
            )
        }
    }

    private suspend fun eliminarColeccion(ref: CollectionReference): Int {
        var eliminados = 0
        var continuar = true

        while (continuar) {
            val snapshot = ref.limit(450).get().await()
            if (snapshot.isEmpty) {
                continuar = false
                break
            }
            val batch = firestore.batch()
            snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
            eliminados += snapshot.size()
            if (snapshot.size() < 450) continuar = false
        }

        return eliminados
    }
}
