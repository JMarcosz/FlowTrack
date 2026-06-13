package com.example.flowtrack.presentation.screens.conversor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversorState(
    val isLoading: Boolean = false,
    val tasa: TasaCambio? = null,
    val montoEntrada: String = "",
    val direccionDopAUsd: Boolean = true, // true = DOP -> USD, false = USD -> DOP
    val historico: List<TasaCambio> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ConversorViewModel @Inject constructor(
    private val repository: TasaCambioRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConversorState())
    val state: StateFlow<ConversorState> = _state

    init {
        cargarTasa()
        cargarHistorico()
    }

    private fun cargarTasa() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val res = repository.obtenerTasaDelDia()
            if (res is AppResult.Success) {
                _state.value = _state.value.copy(isLoading = false, tasa = res.data)
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(isLoading = false, error = res.error.toMensajeUsuario())
            }
        }
    }

    private fun cargarHistorico() {
        viewModelScope.launch {
            val res = repository.obtenerHistorico(30)
            if (res is AppResult.Success) {
                _state.value = _state.value.copy(historico = res.data)
            }
        }
    }

    fun setMonto(monto: String) {
        _state.value = _state.value.copy(montoEntrada = monto)
    }

    fun invertirDireccion() {
        _state.value = _state.value.copy(direccionDopAUsd = !_state.value.direccionDopAUsd)
    }

    fun calcularResultado(): java.math.BigDecimal {
        val st = _state.value
        val montoStr = st.montoEntrada.takeIf { it.isNotBlank() } ?: "0"
        val monto = runCatching { java.math.BigDecimal(montoStr) }.getOrDefault(java.math.BigDecimal.ZERO)
        val tasa = st.tasa ?: return java.math.BigDecimal.ZERO
        
        return if (st.direccionDopAUsd) {
            // Comprando dólares -> usar tasa de venta del banco
            if (tasa.venta.compareTo(java.math.BigDecimal.ZERO) == 0) java.math.BigDecimal.ZERO
            else monto.divide(tasa.venta, 2, java.math.RoundingMode.HALF_UP)
        } else {
            // Vendiendo dólares -> usar tasa de compra del banco
            monto.multiply(tasa.compra).setScale(2, java.math.RoundingMode.HALF_UP)
        }
    }
}
