package com.example.flowtrack.presentation.screens.transacciones

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.ReglaCategoriaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val PERIODOS_TRANSACCIONES = listOf("Este mes", "Mes pasado", "Últimos 3 meses", "Este año")

@Stable
data class TransaccionesState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val transacciones: List<Transaccion> = emptyList(),
    val searchQuery: String = "",
    val filtroTipo: TipoTransaccionFiltro = TipoTransaccionFiltro.TODAS,
    val bancosDisponibles: List<String> = emptyList(),
    val bancoFiltro: String? = null,
    val periodo: String = PERIODOS_TRANSACCIONES.first(),
    val montoMin: BigDecimal? = null,
    val montoMax: BigDecimal? = null,
    val categoriasFiltro: Set<String> = emptySet(),
    val soloSinCategorizar: Boolean = false,
    val error: String? = null,
) {
    val filtrosActivos: Int
        get() {
            var n = 0
            if (bancoFiltro != null) n++
            if (montoMin != null) n++
            if (montoMax != null) n++
            n += categoriasFiltro.size
            if (soloSinCategorizar) n++
            return n
        }
}

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
    private var lastVisible: DocumentSnapshot? = null
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private val searchQueryFlow = MutableStateFlow(_state.value.searchQuery)

    init {
        cargarPagina(reset = true)

        viewModelScope.launch {
            searchQueryFlow
                .drop(1)
                .debounce(300)
                .distinctUntilChanged()
                .collect { aplicarFiltrosAsync() }
        }
    }

    fun seleccionarPeriodo(periodo: String) {
        if (_state.value.periodo == periodo || periodo !in PERIODOS_TRANSACCIONES) return
        savedStateHandle[KEY_PERIODO] = periodo
        _state.update {
            it.copy(
                periodo = periodo,
                isLoading = true,
                isLoadingMore = false,
                hasMore = true,
                transacciones = emptyList(),
                bancosDisponibles = emptyList(),
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

    fun setBancoFiltro(banco: String?) {
        savedStateHandle[KEY_BANCO] = banco
        _state.update { it.copy(bancoFiltro = banco) }
        aplicarFiltrosAsync()
    }

    fun aplicarFiltros(
        banco: String?,
        montoMin: BigDecimal?,
        montoMax: BigDecimal?,
        categorias: Set<String>,
        soloSinCategorizar: Boolean,
    ) {
        savedStateHandle[KEY_BANCO] = banco
        savedStateHandle[KEY_MONTO_MIN] = montoMin?.toPlainString()
        savedStateHandle[KEY_MONTO_MAX] = montoMax?.toPlainString()
        savedStateHandle[KEY_CATEGORIAS] = ArrayList(categorias)
        savedStateHandle[KEY_SIN_CATEGORIA] = soloSinCategorizar
        _state.update {
            it.copy(
                bancoFiltro = banco,
                montoMin = montoMin,
                montoMax = montoMax,
                categoriasFiltro = categorias,
                soloSinCategorizar = soloSinCategorizar,
            )
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
                bancoFiltro = null,
                montoMin = null,
                montoMax = null,
                categoriasFiltro = emptySet(),
                soloSinCategorizar = false,
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
            val (inicio, fin) = rangoParaPeriodo(_state.value.periodo)
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

                    val bancoSeleccionado = _state.value.bancoFiltro
                    val bancos = buildSet {
                        addAll(allTransacciones.map { it.bancoCodigo })
                        bancoSeleccionado?.let(::add)
                    }.sorted()

                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = page.hasMore,
                            bancosDisponibles = bancos,
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

    private fun aplicarFiltrosAsync() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val current = _state.value
            val transacciones = allTransacciones
            val queryNormalizada = current.searchQuery.normalizarDescripcion()
            val montoBuscado = current.searchQuery.trim()

            val filtradas = transacciones.filter { tx ->
                if (tx.esDerivada) return@filter true

                val matchBanco =
                    current.bancoFiltro == null || tx.bancoCodigo == current.bancoFiltro
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
                    (current.montoMin == null || tx.monto >= current.montoMin) &&
                    (current.montoMax == null || tx.monto <= current.montoMax)
                val matchCategoria = when {
                    current.soloSinCategorizar -> tx.categoriaId == null
                    current.categoriasFiltro.isEmpty() -> true
                    else -> tx.categoriaId in current.categoriasFiltro
                }

                matchBanco && matchTipo && matchQuery && matchMonto && matchCategoria
            }

            withContext(Dispatchers.Main) {
                _state.update { it.copy(transacciones = filtradas) }
            }
        }
    }

    private fun restaurarEstado(): TransaccionesState {
        val periodo = savedStateHandle.get<String>(KEY_PERIODO)
            ?.takeIf { it in PERIODOS_TRANSACCIONES }
            ?: PERIODOS_TRANSACCIONES.first()
        val tipo = savedStateHandle.get<String>(KEY_TIPO)
            ?.let { runCatching { TipoTransaccionFiltro.valueOf(it) }.getOrNull() }
            ?: TipoTransaccionFiltro.TODAS

        return TransaccionesState(
            searchQuery = savedStateHandle[KEY_BUSQUEDA] ?: "",
            filtroTipo = tipo,
            bancoFiltro = savedStateHandle[KEY_BANCO],
            periodo = periodo,
            montoMin = savedStateHandle.get<String>(KEY_MONTO_MIN)?.toBigDecimalOrNull(),
            montoMax = savedStateHandle.get<String>(KEY_MONTO_MAX)?.toBigDecimalOrNull(),
            categoriasFiltro = savedStateHandle
                .get<ArrayList<String>>(KEY_CATEGORIAS)
                ?.toSet()
                .orEmpty(),
            soloSinCategorizar = savedStateHandle[KEY_SIN_CATEGORIA] ?: false,
        )
    }

    private fun rangoParaPeriodo(periodo: String): Pair<Instant, Instant> {
        val zona = ZoneId.of("America/Santo_Domingo")
        val ahora = LocalDate.now(zona)
        return when (periodo) {
            "Mes pasado" -> {
                val yearMonth = YearMonth.from(ahora).minusMonths(1)
                yearMonth.atDay(1).atStartOfDay(zona).toInstant() to
                    yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()
            }
            "Últimos 3 meses" ->
                ahora.minusMonths(3).withDayOfMonth(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            "Este año" ->
                ahora.withDayOfYear(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            else ->
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
