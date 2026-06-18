package com.example.flowtrack.presentation.screens.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.usecase.ObservarRevisionLocalUseCase
import com.example.flowtrack.domain.usecase.ObtenerResumenDashboardUseCase
import com.example.flowtrack.domain.usecase.ResumenDashboard
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.model.PeriodoState
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val resumenUseCase: ObtenerResumenDashboardUseCase,
    private val observarRevisionLocalUseCase: ObservarRevisionLocalUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _periodoLabel = savedStateHandle.getStateFlow(
        KEY_PERIODO,
        FiltroPeriodo.ESTE_MES.label,
    )

    val periodo: StateFlow<PeriodoState> = _periodoLabel
        .map { label -> PeriodoState(seleccionado = FiltroPeriodo.fromLabel(label) ?: FiltroPeriodo.ESTE_MES) }
        .stateIn(viewModelScope, SharingStarted.Lazily, PeriodoState())

    val estado: StateFlow<DashboardEstado> = _periodoLabel
        .flatMapLatest { label ->
            val uid = auth.currentUser?.uid
                ?: return@flatMapLatest flowOf(DashboardEstado.Error("Sin sesión activa"))

            val filtroPeriodo = FiltroPeriodo.fromLabel(label) ?: FiltroPeriodo.ESTE_MES

            observarRevisionLocalUseCase().map {
                when (val result = resumenUseCase.ejecutar(uid, filtroPeriodo.label)) {
                    is AppResult.Success -> {
                        val resumen = result.data
                        if (resumen.esVacio()) {
                            DashboardEstado.Vacio
                        } else {
                            val nombreUsuario = auth.currentUser?.displayName
                                ?.substringBefore(" ")
                                ?.ifBlank { null }
                                ?: "ahí"
                            DashboardEstado.Exito(
                                data = resumen.toDashboardUiState(nombreUsuario),
                                periodo = PeriodoState(seleccionado = filtroPeriodo),
                            )
                        }
                    }
                    is AppResult.Error -> DashboardEstado.Error(
                        result.error.toMensajeUsuario()
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, DashboardEstado.Cargando)

    fun seleccionarPeriodo(p: FiltroPeriodo) {
        if (_periodoLabel.value != p.label) {
            savedStateHandle[KEY_PERIODO] = p.label
        }
    }

    fun cargarDatos() {
        val current = _periodoLabel.value
        savedStateHandle[KEY_PERIODO] = ""
        savedStateHandle[KEY_PERIODO] = current
    }

    private companion object {
        const val KEY_PERIODO = "dashboard_periodo"
    }
}

sealed class DashboardEstado {
    data object Cargando : DashboardEstado()
    data object Vacio : DashboardEstado()
    data class Exito(
        val data: DashboardUiState,
        val periodo: PeriodoState,
    ) : DashboardEstado()
    data class Error(val mensaje: String) : DashboardEstado()
}

private fun ResumenDashboard.esVacio(): Boolean {
    val sinMontos = gastoTotal == BigDecimal.ZERO &&
        ingresoTotal == BigDecimal.ZERO &&
        balanceNeto == BigDecimal.ZERO
    return sinMontos && gastosPorBanco.isEmpty() && gastosPorCategoria.isEmpty()
}
