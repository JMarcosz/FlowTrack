package com.example.flowtrack.presentation.screens.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.HistorialRepository
import com.example.flowtrack.domain.model.Carga
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistorialViewModel @Inject constructor(
    private val historialRepository: HistorialRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _estado = MutableStateFlow<HistorialEstado>(HistorialEstado.Cargando)
    val estado: StateFlow<HistorialEstado> = _estado

    init { cargar() }

    fun cargar() {
        val uid = auth.currentUser?.uid ?: run {
            _estado.value = HistorialEstado.Error("No hay sesión activa.")
            return
        }
        viewModelScope.launch {
            _estado.value = HistorialEstado.Cargando
            when (val result = historialRepository.obtenerCargas(uid)) {
                is AppResult.Success -> {
                    _estado.value = if (result.data.isEmpty())
                        HistorialEstado.Vacio
                    else
                        HistorialEstado.ConDatos(result.data)
                }
                is AppResult.Error -> {
                    _estado.value = HistorialEstado.Error(result.error.toMensajeUsuario())
                }
            }
        }
    }

    fun eliminar(cargaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            historialRepository.eliminarCarga(uid, cargaId)
            cargar() // recargar lista tras eliminar
        }
    }
}

sealed class HistorialEstado {
    object Cargando : HistorialEstado()
    object Vacio : HistorialEstado()
    data class ConDatos(val cargas: List<Carga>) : HistorialEstado()
    data class Error(val mensaje: String) : HistorialEstado()
}
