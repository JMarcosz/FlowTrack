package com.example.flowtrack.presentation.screens.transacciones

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.ReglaCategoriaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.data.local.TransaccionesCursor
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.model.FiltrosAvanzadosState
import com.example.flowtrack.presentation.model.PeriodoState
import com.example.flowtrack.presentation.model.RangoMonto

@Stable
data class TransaccionesState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val transacciones: List<Transaccion> = emptyList(),
    val searchQuery: String = "",
    val filtroTipo: TipoTransaccionFiltro = TipoTransaccionFiltro.TODAS,
    val periodo: PeriodoState = PeriodoState(),
    val filtros: FiltrosAvanzadosState = FiltrosAvanzadosState(),
    val error: String? = null,
)

enum class TipoTransaccionFiltro { TODAS, DEBITO, CREDITO }

@OptIn(FlowPreview::class)
@HiltViewModel
class TransaccionesViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: TransaccionRepository,
    private val reglaRepository: ReglaCategoriaRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(restaurarEstado())
    val state: StateFlow<TransaccionesState> = _state.asStateFlow()

    private var allTransacciones: List<Transaccion> = emptyList()
    private var lastVisible: TransaccionesCursor? = null
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var revisionJob: Job? = null
    private val searchQueryFlow = MutableStateFlow(_state.value.searchQuery)

    init {
        cargarPagina(reset = true)
        observarCambiosLocales()

        viewModelScope.launch {
            searchQueryFlow
                .drop(1)
                .debounce(300)
                .distinctUntilChanged()
                .collect { aplicarFiltrosAsync() }
        }
    }

    fun seleccionarPeriodo(periodo: FiltroPeriodo) {
        if (_state.value.periodo.seleccionado == periodo) return
        savedStateHandle[KEY_PERIODO] = periodo.label
        _state.update {
            it.copy(
                periodo = PeriodoState(seleccionado = periodo),
                isLoading = true,
                isLoadingMore = false,
                hasMore = true,
                transacciones = emptyList(),
                filtros = it.filtros.copy(bancosDisponibles = emptyList()),
                error = null,
            )
        }
        cargarPagina(reset = true)
    }

    fun cargarMas() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        cargarPagina(reset = false)
    }

    fun setFiltroTipo(filtro: TipoTransaccionFiltro) {
        savedStateHandle[KEY_TIPO] = filtro.name
        _state.update { it.copy(filtroTipo = filtro) }
        aplicarFiltrosAsync()
    }

    fun setBancoFiltro(bancoId: String?) {
        savedStateHandle[KEY_BANCO] = bancoId
        _state.update {
            it.copy(filtros = it.filtros.copy(bancoId = bancoId))
        }
        aplicarFiltrosAsync()
    }

    fun aplicarFiltros(filtros: FiltrosAvanzadosState) {
        savedStateHandle[KEY_BANCO] = filtros.bancoId
        savedStateHandle[KEY_MONTO_MIN] = filtros.rangoMonto.minimo?.toPlainString()
        savedStateHandle[KEY_MONTO_MAX] = filtros.rangoMonto.maximo?.toPlainString()
        savedStateHandle[KEY_CATEGORIAS] = ArrayList(filtros.categorias)
        savedStateHandle[KEY_SIN_CATEGORIA] = filtros.soloSinCategorizar
        
        _state.update {
            it.copy(filtros = filtros)
        }
        aplicarFiltrosAsync()
    }

    fun limpiarFiltrosAvanzados() {
        savedStateHandle[KEY_BANCO] = null
        savedStateHandle[KEY_MONTO_MIN] = null
        savedStateHandle[KEY_MONTO_MAX] = null
        savedStateHandle[KEY_CATEGORIAS] = arrayListOf<String>()
        savedStateHandle[KEY_SIN_CATEGORIA] = false
        
        _state.update {
            it.copy(
                filtros = it.filtros.copy(
                    bancoId = null,
                    rangoMonto = RangoMonto(),
                    categorias = emptySet(),
                    soloSinCategorizar = false,
                )
            )
        }
        aplicarFiltrosAsync()
    }

    fun setSearchQuery(query: String) {
        savedStateHandle[KEY_BUSQUEDA] = query
        _state.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun eliminarTransaccion(tx: Transaccion) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            if (repository.eliminarTransaccion(uid, tx.id) is AppResult.Success) {
                allTransacciones = allTransacciones.filterNot { it.id == tx.id }
                aplicarFiltrosAsync()
            }
        }
    }

    private fun observarCambiosLocales() {
        val uid = auth.currentUser?.uid ?: return
        revisionJob?.cancel()
        revisionJob = viewModelScope.launch {
            repository.observarRevisionLocal()
                .drop(1)
                .debounce(150)
                .distinctUntilChanged()
                .collect {
                    refrescarDesdeCache(uid)
                }
        }
    }

    fun getDerivadasParaPadre(padreId: String): List<Transaccion> =
        _state.value.transacciones.filter {
            it.esDerivada && it.transaccionPadreId == padreId
        }

    fun recategorizar(tx: Transaccion, nuevaCategoria: String, aplicarATodas: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val idsActualizados = if (aplicarATodas) {
                allTransacciones
                    .filter { it.descripcionNormalizada == tx.descripcionNormalizada }
                    .mapTo(mutableSetOf()) { it.id }
            } else {
                mutableSetOf(tx.id)
            }

            val txActualizada = tx.copy(categoriaId = nuevaCategoria, categoriaAutomatica = false)
            repository.actualizarTransaccion(txActualizada)

            if (aplicarATodas) {
                reglaRepository.crearReglaPersonal(
                    uidUsuario = uid,
                    patron = tx.descripcionNormalizada,
                    categoriaId = nuevaCategoria,
                    tipoMatch = TipoMatch.EXACTO,
                )
                allTransacciones
                    .filter { it.id in idsActualizados && it.id != tx.id }
                    .forEach { similar ->
                        launch {
                            repository.actualizarTransaccion(
                                similar.copy(
                                    categoriaId = nuevaCategoria,
                                    categoriaAutomatica = true,
                                )
                            )
                        }
                    }
            }

            allTransacciones = allTransacciones.map { current ->
                if (current.id !in idsActualizados) {
                    current
                } else {
                    current.copy(
                        categoriaId = nuevaCategoria,
                        categoriaAutomatica = current.id != tx.id,
                    )
                }
            }
            aplicarFiltrosAsync()
        }
    }

    private fun cargarPagina(reset: Boolean) {
        loadJob?.cancel()
        if (reset) {
            filterJob?.cancel()
            allTransacciones = emptyList()
            lastVisible = null
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = false,
                    error = "Sin sesión activa",
                )
            }
            return
        }

        if (!reset) {
            _state.update { it.copy(isLoadingMore = true, error = null) }
        }

        loadJob = viewModelScope.launch {
            val (inicio, fin) = rangoParaPeriodo(_state.value.periodo.seleccionado)
            when (
                val result = repository.obtenerTransaccionesPage(
                    uid = uid,
                    lastVisible = lastVisible,
                    pageSize = PAGE_SIZE,
                    inicio = inicio,
                    fin = fin,
                )
            ) {
                is AppResult.Success -> {
                    val page = result.data
                    lastVisible = page.lastVisible
                    allTransacciones = if (reset) {
                        page.transacciones
                    } else {
                        (allTransacciones + page.transacciones).distinctBy { it.id }
                    }

                    val bancoSeleccionado = _state.value.filtros.bancoId
                    val bancos = buildSet {
                        addAll(allTransacciones.map { it.bancoCodigo })
                        bancoSeleccionado?.let(::add)
                    }.sorted()

                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = page.hasMore,
                            filtros = it.filtros.copy(bancosDisponibles = bancos),
                            error = null,
                        )
                    }
                    aplicarFiltrosAsync()
                }
                is AppResult.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = result.error.toMensajeUsuario(),
                        )
                    }
                }
            }
        }
    }

    private suspend fun refrescarDesdeCache(uid: String) {
        val current = _state.value
        val (inicio, fin) = rangoParaPeriodo(current.periodo.seleccionado)
        val pageSize = maxOf(PAGE_SIZE, allTransacciones.size)

        when (
            val result = repository.obtenerTransaccionesPageLocal(
                uid = uid,
                lastVisible = null,
                pageSize = pageSize,
                inicio = inicio,
                fin = fin,
            )
        ) {
            is AppResult.Success -> {
                val page = result.data
                lastVisible = page.lastVisible
                allTransacciones = page.transacciones

                val bancoSeleccionado = current.filtros.bancoId
                val bancos = buildSet {
                    addAll(allTransacciones.map { it.bancoCodigo })
                    bancoSeleccionado?.let(::add)
                }.sorted()

                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                        filtros = it.filtros.copy(bancosDisponibles = bancos),
                        error = null,
                    )
                }
                aplicarFiltrosAsync()
            }
            is AppResult.Error -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = result.error.toMensajeUsuario(),
                    )
                }
            }
        }
    }

    private fun aplicarFiltrosAsync() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val current = _state.value
            val transacciones = allTransacciones
            val queryNormalizada = current.searchQuery.normalizarDescripcion()
            val montoBuscado = current.searchQuery.trim()
            val filtros = current.filtros

            val filtradas = transacciones.filter { tx ->
                if (tx.esDerivada) return@filter true

                val matchBanco =
                    filtros.bancoId == null || tx.bancoCodigo == filtros.bancoId
                val matchTipo = when (current.filtroTipo) {
                    TipoTransaccionFiltro.TODAS -> true
                    TipoTransaccionFiltro.DEBITO -> tx.tipo == TipoTransaccion.DEBITO
                    TipoTransaccionFiltro.CREDITO -> tx.tipo == TipoTransaccion.CREDITO
                }
                val matchQuery = queryNormalizada.isBlank() ||
                    tx.descripcionNormalizada.contains(queryNormalizada) ||
                    tx.descripcionOriginal.normalizarDescripcion().contains(queryNormalizada) ||
                    tx.bancoCodigo.normalizarDescripcion().contains(queryNormalizada) ||
                    tx.monto.toPlainString().contains(montoBuscado)
                val matchMonto =
                    (filtros.rangoMonto.minimo == null || tx.monto >= filtros.rangoMonto.minimo) &&
                    (filtros.rangoMonto.maximo == null || tx.monto <= filtros.rangoMonto.maximo)
                val matchCategoria = when {
                    filtros.soloSinCategorizar -> tx.categoriaId == null
                    filtros.categorias.isEmpty() -> true
                    else -> tx.categoriaId in filtros.categorias
                }

                matchBanco && matchTipo && matchQuery && matchMonto && matchCategoria
            }

            withContext(Dispatchers.Main) {
                _state.update { it.copy(transacciones = filtradas) }
            }
        }
    }

    private fun restaurarEstado(): TransaccionesState {
        val label = savedStateHandle.get<String>(KEY_PERIODO) ?: FiltroPeriodo.ESTE_MES.label
        val periodoSel = FiltroPeriodo.fromLabel(label) ?: FiltroPeriodo.ESTE_MES
        val tipo = savedStateHandle.get<String>(KEY_TIPO)
            ?.let { runCatching { TipoTransaccionFiltro.valueOf(it) }.getOrNull() }
            ?: TipoTransaccionFiltro.TODAS

        return TransaccionesState(
            searchQuery = savedStateHandle[KEY_BUSQUEDA] ?: "",
            filtroTipo = tipo,
            periodo = PeriodoState(seleccionado = periodoSel),
            filtros = FiltrosAvanzadosState(
                bancoId = savedStateHandle[KEY_BANCO],
                rangoMonto = RangoMonto(
                    minimo = savedStateHandle.get<String>(KEY_MONTO_MIN)?.toBigDecimalOrNull(),
                    maximo = savedStateHandle.get<String>(KEY_MONTO_MAX)?.toBigDecimalOrNull(),
                ),
                categorias = savedStateHandle
                    .get<ArrayList<String>>(KEY_CATEGORIAS)
                    ?.toSet()
                    .orEmpty(),
                soloSinCategorizar = savedStateHandle[KEY_SIN_CATEGORIA] ?: false,
            ),
        )
    }

    private fun rangoParaPeriodo(periodo: FiltroPeriodo): Pair<Instant, Instant> {
        val zona = ZoneId.of("America/Santo_Domingo")
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

    private companion object {
        const val PAGE_SIZE = 30
        const val KEY_PERIODO = "transacciones_periodo"
        const val KEY_TIPO = "transacciones_tipo"
        const val KEY_BANCO = "transacciones_banco"
        const val KEY_MONTO_MIN = "transacciones_monto_min"
        const val KEY_MONTO_MAX = "transacciones_monto_max"
        const val KEY_CATEGORIAS = "transacciones_categorias"
        const val KEY_SIN_CATEGORIA = "transacciones_sin_categoria"
        const val KEY_BUSQUEDA = "transacciones_busqueda"
    }
}
