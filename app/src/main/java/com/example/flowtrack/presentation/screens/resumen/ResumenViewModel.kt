package com.example.flowtrack.presentation.screens.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.usecase.BalanceNeto
import com.example.flowtrack.domain.usecase.ObtenerBalanceNetoUseCase
import com.example.flowtrack.domain.usecase.ObtenerResumenUseCase
import com.example.flowtrack.domain.usecase.ResumenGeneral
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ResumenState(
    val isLoading: Boolean = false,
    val resumen: ResumenGeneral? = null,
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,
    val tabSeleccionado: Int = 0, // 0 = Categoría, 1 = Banco
    val balanceNeto: BalanceNeto? = null,
    val isLoadingNeto: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ResumenViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val resumenUseCase: ObtenerResumenUseCase,
    private val balanceNetoUseCase: ObtenerBalanceNetoUseCase,
) : ViewModel() {

    private val zona = ZoneId.of("America/Santo_Domingo")

    private val _state = MutableStateFlow(
        ResumenState(
            fechaInicio = LocalDate.now(zona).withDayOfMonth(1),
            fechaFin = LocalDate.now(zona)
        )
    )
    val state: StateFlow<ResumenState> = _state

    init {
        cargarResumen()
        cargarBalanceNeto()
    }

    fun cargarBalanceNeto() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingNeto = true)
            val result = balanceNetoUseCase.ejecutar(uid)
            _state.value = if (result is AppResult.Success) {
                _state.value.copy(isLoadingNeto = false, balanceNeto = result.data)
            } else {
                _state.value.copy(isLoadingNeto = false)
            }
        }
    }

    fun setFechas(inicio: LocalDate, fin: LocalDate) {
        _state.value = _state.value.copy(fechaInicio = inicio, fechaFin = fin)
        cargarResumen()
    }

    fun setTab(index: Int) {
        _state.value = _state.value.copy(tabSeleccionado = index)
    }

    fun setRangoPredefinido(rango: RangoFecha) {
        val hoy = LocalDate.now(zona)
        val (inicio, fin) = when (rango) {
            RangoFecha.HOY -> hoy to hoy
            RangoFecha.ESTA_SEMANA -> hoy.minusDays(hoy.dayOfWeek.value.toLong() - 1) to hoy
            RangoFecha.ESTE_MES -> hoy.withDayOfMonth(1) to hoy
            RangoFecha.MES_PASADO -> {
                val mesPasado = hoy.minusMonths(1)
                mesPasado.withDayOfMonth(1) to mesPasado.withDayOfMonth(mesPasado.lengthOfMonth())
            }
        }
        setFechas(inicio, fin)
    }

    fun cargarResumen() {
        if (_state.value.resumen != null || _state.value.isLoading) return
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val inicioInst = _state.value.fechaInicio.atStartOfDay(zona).toInstant()
            val finInst = _state.value.fechaFin.atTime(23, 59, 59).atZone(zona).toInstant()

            val res = resumenUseCase.ejecutar(uid, inicioInst, finInst)
            if (res is AppResult.Success) {
                _state.value = _state.value.copy(isLoading = false, resumen = res.data)
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(isLoading = false, error = res.error.toMensajeUsuario())
            }
        }
    }
}

enum class RangoFecha {
    HOY, ESTA_SEMANA, ESTE_MES, MES_PASADO
}
