package com.example.flowtrack.presentation.screens.categorias

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CategoriaPersonalRepository
import com.example.flowtrack.presentation.components.CategoriaUI
import com.example.flowtrack.presentation.components.categoriaRegistry
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoriasState(
    val isLoading: Boolean = false,
    val categoriasSistema: List<CategoriaUI> = emptyList(),
    val categoriasPersonales: List<CategoriaUI> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class CategoriasViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: CategoriaPersonalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriasState())
    val state: StateFlow<CategoriasState> = _state

    init {
        cargarCategorias()
    }

    private fun cargarCategorias() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                categoriasSistema = categoriaRegistry.values.toList()
            )
            
            val res = repository.obtenerCategorias(uid)
            if (res is AppResult.Success) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    categoriasPersonales = res.data
                )
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = res.error.toMensajeUsuario()
                )
            }
        }
    }

    fun crearCategoria(nombre: String, color: Color) {
        val uid = auth.currentUser?.uid ?: return
        if (nombre.isBlank()) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val nueva = CategoriaUI(
                id = "pers_${UUID.randomUUID().toString().take(8)}",
                nombre = nombre,
                color = color
            )
            val res = repository.guardarCategoria(uid, nueva)
            if (res is AppResult.Success) {
                val actuales = _state.value.categoriasPersonales.toMutableList()
                actuales.add(nueva)
                _state.value = _state.value.copy(isLoading = false, categoriasPersonales = actuales)
            } else {
                _state.value = _state.value.copy(isLoading = false, error = "Error guardando categoría")
            }
        }
    }

    fun eliminarCategoria(categoriaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val res = repository.eliminarCategoria(uid, categoriaId)
            if (res is AppResult.Success) {
                val filtradas = _state.value.categoriasPersonales.filter { it.id != categoriaId }
                _state.value = _state.value.copy(isLoading = false, categoriasPersonales = filtradas)
            } else {
                _state.value = _state.value.copy(isLoading = false, error = "Error eliminando categoría")
            }
        }
    }
}
