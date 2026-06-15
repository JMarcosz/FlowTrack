package com.example.flowtrack.presentation.screens.conversor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TasaCambioRepository
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.usecase.ConvertirMonedaUseCase
import com.example.flowtrack.domain.usecase.ObtenerHistoricoTasasUseCase
import com.example.flowtrack.domain.usecase.PuntoHistoricoTasa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class ConversorState(
    val isLoading: Boolean = false,
    val tasa: TasaCambio? = null,
    val montoEntrada: String = "",
    val direccionDopAUsd: Boolean = true, // true = DOP -> USD, false = USD -> DOP
    val historico: List<TasaCambio> = emptyList(),
    val serieHistorico: List<PuntoHistoricoTasa> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ConversorViewModel @Inject constructor(
    private val repository: TasaCambioRepository,
    private val convertirMonedaUseCase: ConvertirMonedaUseCase,
    private val obtenerHistoricoTasasUseCase: ObtenerHistoricoTasasUseCase,
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
            when (val res = obtenerHistoricoTasasUseCase.ejecutar(30)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        historico = res.data.historico,
                        serieHistorico = res.data.serie,
                    )
                }
                is AppResult.Error -> {
                    _state.value = _state.value.copy(error = res.error.toMensajeUsuario())
                }
            }
        }
    }

    fun setMonto(monto: String) {
        _state.value = _state.value.copy(montoEntrada = monto)
    }

    fun invertirDireccion() {
        _state.value = _state.value.copy(direccionDopAUsd = !_state.value.direccionDopAUsd)
    }

    fun calcularResultado(): BigDecimal {
        val st = _state.value
        val montoStr = st.montoEntrada.takeIf { it.isNotBlank() } ?: "0"
        val monto = runCatching { BigDecimal(montoStr) }.getOrDefault(BigDecimal.ZERO)
        return convertirMonedaUseCase.ejecutar(monto, st.direccionDopAUsd, st.tasa)
    }
}
