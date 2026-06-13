package com.example.flowtrack.presentation.screens.reglas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.ReglaCategoriaRepository
import com.example.flowtrack.data.firestore.repositories.ReglaSugeridaRepository
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.ReglaSugerida
import com.example.flowtrack.domain.model.TipoMatch
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReglasState(
    val tabSeleccionado: Int = 0,
    val reglasPersonales: List<ReglaCategoria> = emptyList(),
    val sugerencias: List<ReglaSugerida> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ReglasViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val reglaRepository: ReglaCategoriaRepository,
    private val sugeridaRepository: ReglaSugeridaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReglasState())
    val state: StateFlow<ReglasState> = _state

    init {
        cargar()
    }

    fun setTab(tab: Int) {
        _state.value = _state.value.copy(tabSeleccionado = tab)
    }

    private fun cargar() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val reglasResult = reglaRepository.obtenerReglasPersonales(uid)
            val sugResult = sugeridaRepository.obtenerPendientes(uid)

            _state.value = _state.value.copy(
                isLoading = false,
                reglasPersonales = if (reglasResult is AppResult.Success) reglasResult.data else emptyList(),
                sugerencias = if (sugResult is AppResult.Success) sugResult.data else emptyList(),
                error = (reglasResult as? AppResult.Error)?.error?.toMensajeUsuario()
                    ?: (sugResult as? AppResult.Error)?.error?.toMensajeUsuario(),
            )
        }
    }

    fun eliminarRegla(reglaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            reglaRepository.eliminarRegla(uid, reglaId)
            _state.value = _state.value.copy(
                reglasPersonales = _state.value.reglasPersonales.filterNot { it.id == reglaId }
            )
        }
    }

    fun aceptarSugerencia(sugerencia: ReglaSugerida, categoriaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            reglaRepository.crearReglaPersonal(
                uidUsuario = uid,
                patron = sugerencia.patronDetectado,
                categoriaId = categoriaId,
                tipoMatch = TipoMatch.CONTIENE,
            )
            sugeridaRepository.resolverSugerencia(uid, sugerencia.id, aceptada = true)
            _state.value = _state.value.copy(
                sugerencias = _state.value.sugerencias.filterNot { it.id == sugerencia.id }
            )
        }
    }

    fun rechazarSugerencia(sugerencia: ReglaSugerida) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            sugeridaRepository.resolverSugerencia(uid, sugerencia.id, aceptada = false)
            _state.value = _state.value.copy(
                sugerencias = _state.value.sugerencias.filterNot { it.id == sugerencia.id }
            )
        }
    }
}
