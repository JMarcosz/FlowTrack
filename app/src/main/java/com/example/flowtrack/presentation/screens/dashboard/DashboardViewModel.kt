package com.example.flowtrack.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.usecase.CalcularComparativaMensualUseCase
import com.example.flowtrack.domain.usecase.ComparativaMensual
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val comparativaUseCase: CalcularComparativaMensualUseCase,
    private val cuentaRepository: CuentaRepository,
    private val transaccionRepository: TransaccionRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow<DashboardEstado>(DashboardEstado.Cargando)
    val estado: StateFlow<DashboardEstado> = _estado

    init {
        cargarDatos()
    }

    fun cargarDatos() {
        val uid = auth.currentUser?.uid ?: run {
            _estado.value = DashboardEstado.Error("No hay sesión activa")
            return
        }

        viewModelScope.launch {
            _estado.value = DashboardEstado.Cargando

            val comparativaDef = async { comparativaUseCase.ejecutar(uid) }
            val cuentasDef = async { cuentaRepository.obtenerCuentas(uid) }
            
            // Transacciones recientes para lista y donut (mes actual)
            val zona = ZoneId.of("America/Santo_Domingo")
            val ahora = LocalDate.now(zona)
            val inicioMes = ahora.withDayOfMonth(1).atStartOfDay(zona).toInstant()
            val transaccionesDef = async { transaccionRepository.obtenerTransacciones(uid, inicioMes, limite = 1000) }

            val resComparativa = comparativaDef.await()
            val resCuentas = cuentasDef.await()
            val resTransacciones = transaccionesDef.await()

            if (resComparativa is AppResult.Error) {
                _estado.value = DashboardEstado.Error(resComparativa.error.toMensajeUsuario())
                return@launch
            }
            if (resCuentas is AppResult.Error) {
                _estado.value = DashboardEstado.Error(resCuentas.error.toMensajeUsuario())
                return@launch
            }
            if (resTransacciones is AppResult.Error) {
                _estado.value = DashboardEstado.Error(resTransacciones.error.toMensajeUsuario())
                return@launch
            }

            val comparativa = (resComparativa as AppResult.Success).data
            val cuentas = (resCuentas as AppResult.Success).data.filter { it.mostrarEnDashboard }
            val transaccionesMes = (resTransacciones as AppResult.Success).data

            _estado.value = DashboardEstado.Exito(
                comparativa = comparativa,
                cuentas = cuentas,
                transaccionesMes = transaccionesMes
            )
        }
    }
}

sealed class DashboardEstado {
    object Cargando : DashboardEstado()
    data class Exito(
        val comparativa: ComparativaMensual,
        val cuentas: List<Cuenta>,
        val transaccionesMes: List<Transaccion>
    ) : DashboardEstado()
    data class Error(val mensaje: String) : DashboardEstado()
}
