package com.example.flowtrack.presentation.screens.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.usecase.BalanceNeto
import com.example.flowtrack.domain.usecase.ObtenerBalanceNetoUseCase
import com.example.flowtrack.domain.usecase.ObtenerResumenUseCase
import com.example.flowtrack.domain.usecase.ResumenGeneral
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.model.FiltrosAvanzadosState
import com.example.flowtrack.presentation.model.PeriodoState
import com.example.flowtrack.presentation.model.RangoMonto
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class ResumenState(
    val isLoading: Boolean = false,
    val resumen: ResumenGeneral? = null,
    val periodo: PeriodoState = PeriodoState(),
    val filtros: FiltrosAvanzadosState = FiltrosAvanzadosState(),
    val tabSeleccionado: Int = 0, // 0 = Categoría, 1 = Banco
    val balanceNeto: BalanceNeto? = null,
    val isLoadingNeto: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
@OptIn(FlowPreview::class)
class ResumenViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val store: AppDataStore,
    private val resumenUseCase: ObtenerResumenUseCase,
    private val balanceNetoUseCase: ObtenerBalanceNetoUseCase,
    private val transaccionRepository: TransaccionRepository,
) : ViewModel() {

    private val zona = ZoneId.of("America/Santo_Domingo")
    private val _state = MutableStateFlow(ResumenState())
    val state: StateFlow<ResumenState> = _state
    private var resumenJob: Job? = null

    init {
        cargarResumen()
        cargarBalanceNeto()

        viewModelScope.launch {
            combine(store.cuentas, store.balancesPorCuenta) { c, b -> c to b }
                .drop(1)
                .distinctUntilChanged()
                .collect { cargarBalanceNeto() }
        }
        viewModelScope.launch {
            transaccionRepository.observarRevisionLocal()
                .drop(1)
                .debounce(150)
                .distinctUntilChanged()
                .collect { cargarResumen() }
        }
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

    fun seleccionarPeriodo(periodo: FiltroPeriodo) {
        if (_state.value.periodo.seleccionado == periodo) return
        _state.update {
            it.copy(
                periodo = PeriodoState(seleccionado = periodo),
                resumen = null,
                filtros = it.filtros.copy(bancosDisponibles = emptyList()),
            )
        }
        cargarResumen()
    }

    fun aplicarFiltros(filtros: FiltrosAvanzadosState) {
        _state.update { it.copy(filtros = filtros, resumen = null) }
        cargarResumen()
    }

    fun limpiarFiltrosAvanzados() {
        _state.update {
            it.copy(
                filtros = it.filtros.copy(
                    bancoId = null,
                    rangoMonto = RangoMonto(),
                    categorias = emptySet(),
                    soloSinCategorizar = false,
                ),
                resumen = null,
            )
        }
        cargarResumen()
    }

    fun setTab(index: Int) {
        _state.value = _state.value.copy(tabSeleccionado = index)
    }

    private fun cargarResumen() {
        val uid = auth.currentUser?.uid ?: return
        resumenJob?.cancel()
        resumenJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val (inicioInst, finInst) = rangoParaPeriodo(_state.value.periodo.seleccionado)

            val res = resumenUseCase.ejecutar(uid, inicioInst, finInst, _state.value.filtros)
            if (res is AppResult.Success) {
                val bancos = res.data.gastosPorBanco.map { it.bancoCodigo }.sorted()
                _state.value = _state.value.copy(
                    isLoading = false,
                    resumen = res.data,
                    filtros = _state.value.filtros.copy(bancosDisponibles = bancos)
                )
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(isLoading = false, error = res.error.toMensajeUsuario())
            }
        }
    }

    private fun rangoParaPeriodo(periodo: FiltroPeriodo): Pair<Instant, Instant> {
        val ahora = LocalDate.now(zona)
        return when (periodo) {
            FiltroPeriodo.MES_PASADO -> {
                val yearMonth = YearMonth.from(ahora).minusMonths(1)
                yearMonth.atDay(1).atStartOfDay(zona).toInstant() to
                    yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()
            }
            FiltroPeriodo.ULTIMOS_3_MESES ->
                ahora.minusMonths(3).withDayOfMonth(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            FiltroPeriodo.ESTE_ANIO ->
                ahora.withDayOfYear(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            FiltroPeriodo.ESTE_MES ->
                ahora.withDayOfMonth(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
        }
    }
}
