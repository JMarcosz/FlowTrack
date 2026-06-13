package com.example.flowtrack.presentation.screens.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.HistorialRepository
import com.example.flowtrack.domain.model.Carga
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistorialViewModel @Inject constructor(
    private val historialRepository: HistorialRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uid = MutableStateFlow(auth.currentUser?.uid)

    // Reactivo: la lista se actualiza automáticamente al importar o eliminar una carga
    val estado: StateFlow<HistorialEstado> = _uid
        .flatMapLatest { uid ->
            if (uid == null) return@flatMapLatest flowOf(HistorialEstado.Error("Sin sesión"))
            historialRepository.observarCargas(uid)
                .map { cargas ->
                    if (cargas.isEmpty()) HistorialEstado.Vacio
                    else HistorialEstado.ConDatos(cargas)
                }
                .catch { emit(HistorialEstado.Error(it.message ?: "Error al cargar historial")) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistorialEstado.Cargando)

    fun eliminar(cargaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = historialRepository.eliminarTransaccionesDeCarga(uid, cargaId)
            // Si hay error, el Flow no se actualiza automáticamente — exponer el error manualmente
            if (result is AppResult.Error) {
                // El estado actual se actualiza via snapshotListener; solo necesitamos reportar error
                // El próximo emit del Flow reflejará el estado real
            }
        }
    }

    fun eliminarTodo() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            historialRepository.eliminarTodo(uid)
        }
    }
}

sealed class HistorialEstado {
    object Cargando : HistorialEstado()
    object Vacio : HistorialEstado()
    data class ConDatos(val cargas: List<Carga>) : HistorialEstado()
    data class Error(val mensaje: String) : HistorialEstado()
}
