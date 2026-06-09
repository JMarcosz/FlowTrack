package com.example.flowtrack.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.usecase.ObtenerResumenDashboardUseCase
import com.example.flowtrack.domain.usecase.ResumenDashboard
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

val PERIODOS_DASHBOARD = listOf("Este mes", "Mes pasado", "Últimos 3 meses", "Este año")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val store: AppDataStore,
    private val resumenUseCase: ObtenerResumenDashboardUseCase,
) : ViewModel() {

    private val _periodo = MutableStateFlow(PERIODOS_DASHBOARD.first())
    val periodo: StateFlow<String> = _periodo.asStateFlow()

    val estado: StateFlow<DashboardEstado> = _periodo
        .flatMapLatest { periodo ->
            val uid = auth.currentUser?.uid
                ?: return@flatMapLatest flowOf(DashboardEstado.Error("Sin sesión activa"))

            combine(
                flowOf(uid),
                store.cuentas,
            ) { u, cuentas ->
                val result = withContext(Dispatchers.Default) {
                    resumenUseCase.ejecutar(u, periodo)
                }
                when (result) {
                    is AppResult.Success -> DashboardEstado.Exito(
                        resumen       = result.data,
                        cuentas       = cuentas.filter { it.mostrarEnDashboard },
                        periodo       = periodo,
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardEstado.Cargando)

    fun seleccionarPeriodo(p: String) {
        if (_periodo.value != p) _periodo.value = p
    }

    fun cargarDatos() {
        val current = _periodo.value
        _periodo.value = ""
        _periodo.value = current
    }
}

sealed class DashboardEstado {
    object Cargando : DashboardEstado()
    data class Exito(
        val resumen: ResumenDashboard,
        val cuentas: List<Cuenta>,
        val periodo: String,
        val nombreUsuario: String,
    ) : DashboardEstado()
    data class Error(val mensaje: String) : DashboardEstado()
}
