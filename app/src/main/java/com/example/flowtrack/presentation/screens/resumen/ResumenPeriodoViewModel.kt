package com.example.flowtrack.presentation.screens.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.usecase.ObtenerResumenPeriodoUseCase
import com.example.flowtrack.domain.usecase.ResumenPorPeriodo
import com.example.flowtrack.domain.usecase.TipoPeriodo
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ResumenPeriodoState(
    val tipo: TipoPeriodo = TipoPeriodo.MES,
    val resumen: ResumenPorPeriodo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ResumenPeriodoViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val obtenerResumenPeriodo: ObtenerResumenPeriodoUseCase,
) : ViewModel() {

    private val zona = ZoneId.of("America/Santo_Domingo")
    private val _state = MutableStateFlow(ResumenPeriodoState())
    val state: StateFlow<ResumenPeriodoState> = _state

    init {
        cargar(TipoPeriodo.MES)
    }

    fun setTipo(tipo: TipoPeriodo) {
        if (tipo != _state.value.tipo) cargar(tipo)
    }

    private fun cargar(tipo: TipoPeriodo) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(tipo = tipo, isLoading = true, error = null)

            val fin = Instant.now()
            // Ventana amplia para que el agrupamiento muestre varios buckets.
            val inicio = fin.atZone(zona).minusMonths(6).toInstant()

            when (val res = obtenerResumenPeriodo.ejecutar(uid, inicio, fin, tipo)) {
                is AppResult.Success -> _state.value = _state.value.copy(isLoading = false, resumen = res.data)
                is AppResult.Error   -> _state.value = _state.value.copy(
                    isLoading = false,
                    error = res.error.toMensajeUsuario(),
                )
            }
        }
    }
}
