package com.example.flowtrack.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.MovimientoTarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.usecase.CalcularComparativaMensualUseCase
import com.example.flowtrack.domain.usecase.ComparativaMensual
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

val PERIODOS_DASHBOARD = listOf("Este mes", "Mes pasado", "Últimos 3 meses", "Este año")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val comparativaUseCase: CalcularComparativaMensualUseCase,
    private val store: AppDataStore,
    private val transaccionRepository: TransaccionRepository,
    private val movimientoTarjetaRepository: MovimientoTarjetaRepository,
) : ViewModel() {

    private val _periodo = MutableStateFlow(PERIODOS_DASHBOARD.first())
    val periodo: StateFlow<String> = _periodo.asStateFlow()

    // Comparativa se carga por separado para no bloquear el primer frame del dashboard
    private val _comparativa = MutableStateFlow<ComparativaMensual?>(null)
    val comparativa: StateFlow<ComparativaMensual?> = _comparativa.asStateFlow()

    // Estado principal: reactive combine de cuentas + transacciones + movimientos del período
    val estado: StateFlow<DashboardEstado> = _periodo
        .flatMapLatest { periodo ->
            val uid = auth.currentUser?.uid
                ?: return@flatMapLatest flowOf(DashboardEstado.Error("Sin sesión activa"))
            val (inicio, fin) = rangoParaPeriodo(periodo)
            combine(
                store.cuentas,
                transaccionRepository.observarTransaccionesRecientes(uid, inicio, fin, limite = 100),
                movimientoTarjetaRepository.observarMovimientos(uid, inicio, fin, limite = 200),
            ) { cuentas, txs, movs ->
                DashboardEstado.Exito(
                    comparativa = _comparativa.value,
                    cuentas = cuentas.filter { it.mostrarEnDashboard },
                    transaccionesMes = txs,
                    movimientosMes = movs,
                    periodo = periodo,
                )
            }
        }
        .onStart { emit(DashboardEstado.Cargando) }
        .catch { emit(DashboardEstado.Error(it.message ?: "Error desconocido")) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardEstado.Cargando)

    init {
        // Cargar comparativa en background cuando cambia el período
        viewModelScope.launch {
            _periodo.collectLatest { _ ->
                val uid = auth.currentUser?.uid ?: return@collectLatest
                _comparativa.value = null
                val result = withContext(Dispatchers.Default) { comparativaUseCase.ejecutar(uid) }
                if (result is AppResult.Success) _comparativa.value = result.data
            }
        }
    }

    fun seleccionarPeriodo(p: String) {
        if (_periodo.value != p) _periodo.value = p
    }

    /** Fuerza recarga manual (pull-to-refresh). Re-dispara el período para que flatMapLatest suscriba de nuevo. */
    fun cargarDatos() {
        val current = _periodo.value
        _periodo.value = ""
        _periodo.value = current
    }

    private fun rangoParaPeriodo(periodo: String): Pair<Instant, Instant> {
        val zona  = ZoneId.of("America/Santo_Domingo")
        val ahora = LocalDate.now(zona)
        return when (periodo) {
            "Mes pasado" -> {
                val ym = YearMonth.from(ahora).minusMonths(1)
                ym.atDay(1).atStartOfDay(zona).toInstant() to
                    ym.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()
            }
            "Últimos 3 meses" -> {
                ahora.minusMonths(3).withDayOfMonth(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            }
            "Este año" -> {
                ahora.withDayOfYear(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            }
            else -> { // "Este mes"
                ahora.withDayOfMonth(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            }
        }
    }
}

sealed class DashboardEstado {
    object Cargando : DashboardEstado()
    data class Exito(
        val comparativa: ComparativaMensual?,
        val cuentas: List<Cuenta>,
        val transaccionesMes: List<Transaccion>,
        val movimientosMes: List<MovimientoTarjeta>,
        val periodo: String,
    ) : DashboardEstado()
    data class Error(val mensaje: String) : DashboardEstado()
}
