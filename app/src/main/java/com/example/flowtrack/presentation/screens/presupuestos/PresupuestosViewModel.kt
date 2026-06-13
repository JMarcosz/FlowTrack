package com.example.flowtrack.presentation.screens.presupuestos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.data.firestore.repositories.PresupuestoRepository
import com.example.flowtrack.domain.model.PeriodoPresupuesto
import com.example.flowtrack.domain.model.Presupuesto
import com.example.flowtrack.domain.usecase.ObtenerPresupuestosConGastoUseCase
import com.example.flowtrack.domain.usecase.PresupuestoConGasto
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class PresupuestosState(
    val isLoading: Boolean = true,
    val presupuestos: List<PresupuestoConGasto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class PresupuestosViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: PresupuestoRepository,
    private val useCase: ObtenerPresupuestosConGastoUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PresupuestosState())
    val state: StateFlow<PresupuestosState> = _state

    init {
        cargar()
    }

    private fun cargar() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            useCase.observar(uid)
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                .collect { lista ->
                    _state.update { it.copy(isLoading = false, presupuestos = lista) }
                }
        }
    }

    fun guardar(categoriaId: String, montoLimite: BigDecimal, periodo: PeriodoPresupuesto) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val presupuesto = Presupuesto(
                id = UUID.randomUUID().toString(),
                uidUsuario = uid,
                categoriaId = categoriaId,
                montoLimite = montoLimite,
                periodo = periodo,
                activo = true,
                creadoEn = Instant.now(),
            )
            repository.guardarPresupuesto(presupuesto)
        }
    }

    fun eliminar(id: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch { repository.eliminarPresupuesto(uid, id) }
    }
}
