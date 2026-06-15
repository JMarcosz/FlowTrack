package com.example.flowtrack.presentation.screens.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.usecase.ObtenerResumenDashboardUseCase
import com.example.flowtrack.domain.usecase.ResumenDashboard
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.model.PeriodoState
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val store: AppDataStore,
    private val resumenUseCase: ObtenerResumenDashboardUseCase,
    private val transaccionRepository: com.example.flowtrack.data.firestore.repositories.TransaccionRepository,
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

            combine(
                flowOf(uid),
                store.cuentas,
                transaccionRepository.observarRevisionLocal().onStart { emit(0L) },
            ) { u, cuentas, _ ->
                val result = withContext(Dispatchers.Default) {
                    resumenUseCase.ejecutar(u, filtroPeriodo.label)
                }
                when (result) {
                    is AppResult.Success -> DashboardEstado.Exito(
                        resumen       = result.data,
                        cuentas       = cuentas.filter { it.mostrarEnDashboard },
                        periodo       = PeriodoState(seleccionado = filtroPeriodo),
                        nombreUsuario = auth.currentUser?.displayName
                            ?.substringBefore(" ")
                            ?.ifBlank { null }
                            ?: "ahí",
                    )
                    is AppResult.Error -> DashboardEstado.Error(
                        result.error.toMensajeUsuario()
                    )
                }
            }
        }
        .onStart { emit(DashboardEstado.Cargando) }
        .flowOn(Dispatchers.Default)
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
    object Cargando : DashboardEstado()
    data class Exito(
        val resumen: ResumenDashboard,
        val cuentas: List<Cuenta>,
        val periodo: PeriodoState,
        val nombreUsuario: String,
    ) : DashboardEstado()
    data class Error(val mensaje: String) : DashboardEstado()
}
