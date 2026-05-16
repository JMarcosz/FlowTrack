package com.example.flowtrack.data.firestore.repositories

import androidx.compose.ui.graphics.Color
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.presentation.components.CategoriaUI
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoriaPersonalRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun obtenerCategorias(uid: String): AppResult<List<CategoriaUI>> {
        return try {
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
            AppResult.Success(categorias)
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
            Color(android.graphics.Color.parseColor("#FF$colorStr"))
        } catch (e: Exception) {
            Color.Gray
        }
    }
}
