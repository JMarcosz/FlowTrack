package com.example.flowtrack.presentation.screens.tarjetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Tarjeta
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TarjetasState(
    val isLoading: Boolean = false,
    val tarjetas: List<Tarjeta> = emptyList(),
    val estadosPorTarjeta: Map<String, List<EstadoTarjetaSnap>> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class TarjetasViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: TarjetaRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TarjetasState())
    val state: StateFlow<TarjetasState> = _state

    init {
        cargarDatos()
    }

    fun cargarDatos() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val resTarjetas = repository.obtenerTarjetas(uid)

            if (resTarjetas is AppResult.Error) {
                _state.value = _state.value.copy(isLoading = false, error = resTarjetas.error.toMensajeUsuario())
                return@launch
            }

            val tarjetas = (resTarjetas as AppResult.Success).data
            _state.value = _state.value.copy(tarjetas = tarjetas)

            // Cargar historiales
            val mapEstados = mutableMapOf<String, List<EstadoTarjetaSnap>>()
            for (t in tarjetas) {
                val resEst = repository.obtenerEstadosTarjeta(uid, t.id)
                if (resEst is AppResult.Success) {
                    mapEstados[t.id] = resEst.data
                }
            }
            
            _state.value = _state.value.copy(isLoading = false, estadosPorTarjeta = mapEstados)
        }
    }
}
