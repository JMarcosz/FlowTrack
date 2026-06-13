package com.example.flowtrack.data.firestore.repositories

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.presentation.components.CategoriaUI
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoriaPersonalRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    suspend fun obtenerCategorias(uid: String): AppResult<List<CategoriaUI>> {
        return try {
            val local = offlineStore.getCategoriasPersonales(uid)
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            runCatching {
                val snapshot = firestore.collection("usuarios").document(uid)
                    .collection("categorias_personales")
                    .get()
                    .await()

                val categorias = snapshot.documents.mapNotNull { doc ->
                    val colorHex = doc.getString("colorHex") ?: "#000000"
                    val color = parsearColorHex(colorHex)
                    CategoriaUI(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "",
                        color = color
                    )
                }
                offlineStore.upsertCategoriasPersonales(uid, categorias)
            }
            AppResult.Success(offlineStore.getCategoriasPersonales(uid))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar categorías personales: ${e.message}", e))
        }
    }

    suspend fun guardarCategoria(uid: String, categoria: CategoriaUI): AppResult<Unit> {
        return try {
            val hexColor = String.format("#%06X", (0xFFFFFF and categoria.color.value.toInt()))
            val dto = mapOf(
                "nombre" to categoria.nombre,
                "colorHex" to hexColor
            )
            offlineStore.upsertCategoriaPersonal(uid, categoria)
            firestore.collection("usuarios").document(uid)
                .collection("categorias_personales").document(categoria.id)
                .set(dto)
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al guardar categoría: ${e.message}", e))
        }
    }

    suspend fun eliminarCategoria(uid: String, categoriaId: String): AppResult<Unit> {
        return try {
            offlineStore.deleteById("CATEGORIA_PERSONAL", uid, categoriaId)
            firestore.collection("usuarios").document(uid)
                .collection("categorias_personales").document(categoriaId)
                .delete()
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar categoría: ${e.message}", e))
        }
    }

    private fun parsearColorHex(hex: String): Color {
        return try {
            val colorStr = if (hex.startsWith("#")) hex.substring(1) else hex
            Color("#FF$colorStr".toColorInt())
        } catch (e: Exception) {
            Color.Gray
        }
    }
}
