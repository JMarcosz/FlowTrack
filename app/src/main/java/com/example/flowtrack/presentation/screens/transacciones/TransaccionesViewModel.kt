package com.example.flowtrack.presentation.screens.transacciones

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.data.firestore.repositories.ReglaCategoriaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

val PERIODOS_TRANSACCIONES = listOf("Este mes", "Mes pasado", "Últimos 3 meses", "Este año")

@Stable
data class TransaccionesState(
    val isLoading: Boolean = true,
    val transacciones: List<Transaccion> = emptyList(),
    val searchQuery: String = "",
    val filtroTipo: TipoTransaccionFiltro = TipoTransaccionFiltro.TODAS,
    val bancosDisponibles: List<String> = emptyList(),
    val bancoFiltro: String? = null,
    val periodo: String = PERIODOS_TRANSACCIONES.first(),
    val montoMin: java.math.BigDecimal? = null,
    val montoMax: java.math.BigDecimal? = null,
    val categoriasFiltro: Set<String> = emptySet(),
    val soloSinCategorizar: Boolean = false,
    val error: String? = null,
) {
    val filtrosActivos: Int get() {
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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransaccionesViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: TransaccionRepository,
    private val reglaRepository: ReglaCategoriaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransaccionesState())
    val state: StateFlow<TransaccionesState> = _state.asStateFlow()

    // Lista base que llega de Firestore (sin filtros de UI)
    private var allTransacciones: List<Transaccion> = emptyList()

    private val _periodo = MutableStateFlow(PERIODOS_TRANSACCIONES.first())
    private val searchQueryFlow = MutableStateFlow("")
    private var filterJob: Job? = null

    init {
        // Listener reactivo: se re-suscribe a Firestore cada vez que cambia el período.
        // Cuando llegan documentos nuevos (ej. tras importar), emite automáticamente sin
        // necesidad de recargar manualmente.
        viewModelScope.launch {
            _periodo.flatMapLatest { periodo ->
                val uid = auth.currentUser?.uid
                    ?: return@flatMapLatest flowOf(emptyList())
                val (inicio, fin) = rangoParaPeriodo(periodo)
                repository.observarTransaccionesRecientes(uid, inicio, fin, limite = 50)
            }.collect { txs ->
                allTransacciones = txs
                val bancos = txs.map { it.bancoCodigo }.distinct().sorted()
                val filtroValido = _state.value.bancoFiltro?.takeIf { it in bancos }
                _state.update { it.copy(bancosDisponibles = bancos, bancoFiltro = filtroValido) }
                aplicarFiltrosAsync()
            }
        }

        // Búsqueda con debounce
        viewModelScope.launch {
            searchQueryFlow
                .drop(1)
                .debounce(300)
                .distinctUntilChanged()
                .collect { aplicarFiltrosAsync() }
        }
    }

    fun seleccionarPeriodo(p: String) {
        if (_state.value.periodo == p) return
        _state.update { it.copy(periodo = p, isLoading = true) }
        _periodo.value = p
    }

    fun setFiltroTipo(filtro: TipoTransaccionFiltro) {
        _state.update { it.copy(filtroTipo = filtro) }
        aplicarFiltrosAsync()
    }

    fun setBancoFiltro(banco: String?) {
        _state.update { it.copy(bancoFiltro = banco) }
        aplicarFiltrosAsync()
    }

    fun aplicarFiltros(
        banco: String?,
        montoMin: java.math.BigDecimal?,
        montoMax: java.math.BigDecimal?,
        categorias: Set<String>,
        soloSinCategorizar: Boolean,
    ) {
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
        _state.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    private fun aplicarFiltrosAsync() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val currState = _state.value
            val txSnapshot = allTransacciones.toList()
            val queryNorm = currState.searchQuery.normalizarDescripcion()

            val filtradas = txSnapshot.filter { tx ->
                if (tx.esDerivada) return@filter true

                val matchBanco = currState.bancoFiltro == null || tx.bancoCodigo == currState.bancoFiltro

                val matchTipo = when (currState.filtroTipo) {
                    TipoTransaccionFiltro.TODAS   -> true
                    TipoTransaccionFiltro.DEBITO  -> tx.tipo == TipoTransaccion.DEBITO
                    TipoTransaccionFiltro.CREDITO -> tx.tipo == TipoTransaccion.CREDITO
                }

                val matchQuery = if (queryNorm.isBlank()) true else {
                    tx.descripcionNormalizada.normalizarDescripcion().contains(queryNorm) ||
                        tx.descripcionOriginal.normalizarDescripcion().contains(queryNorm) ||
                        tx.bancoCodigo.normalizarDescripcion().contains(queryNorm) ||
                        tx.monto.toPlainString().contains(currState.searchQuery.trim())
                }

                val matchMonto =
                    (currState.montoMin == null || tx.monto >= currState.montoMin) &&
                    (currState.montoMax == null || tx.monto <= currState.montoMax)

                val matchCategoria = when {
                    currState.soloSinCategorizar -> tx.categoriaId == null
                    currState.categoriasFiltro.isEmpty() -> true
                    else -> tx.categoriaId in currState.categoriasFiltro
                }

                matchBanco && matchTipo && matchQuery && matchMonto && matchCategoria
            }

            withContext(Dispatchers.Main) {
                _state.update { it.copy(isLoading = false, transacciones = filtradas) }
            }
        }
    }

    fun eliminarTransaccion(tx: Transaccion) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // El listener de Firestore actualizará la lista automáticamente tras la eliminación
            repository.eliminarTransaccion(uid, tx.id)
        }
    }

    fun getDerivadasParaPadre(padreId: String): List<Transaccion> =
        _state.value.transacciones.filter { it.esDerivada && it.transaccionPadreId == padreId }

    fun recategorizar(tx: Transaccion, nuevaCategoria: String, aplicarATodas: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
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
                    .filter { it.descripcionNormalizada == tx.descripcionNormalizada && it.id != tx.id }
                    .forEach { similar ->
                        launch {
                            repository.actualizarTransaccion(
                                similar.copy(categoriaId = nuevaCategoria, categoriaAutomatica = true)
                            )
                        }
                    }
            }
            // El listener de Firestore recoge los cambios y actualiza la UI
        }
    }

    private fun rangoParaPeriodo(periodo: String): Pair<Instant, Instant> {
        val zona = ZoneId.of("America/Santo_Domingo")
        val ahora = LocalDate.now(zona)
        return when (periodo) {
            "Mes pasado" -> {
                val ym = YearMonth.from(ahora).minusMonths(1)
                ym.atDay(1).atStartOfDay(zona).toInstant() to
                    ym.atEndOfMonth().atTime(23, 59, 59).atZone(zona).toInstant()
            }
            "Últimos 3 meses" ->
                ahora.minusMonths(3).withDayOfMonth(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            "Este año" ->
                ahora.withDayOfYear(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
            else -> // "Este mes"
                ahora.withDayOfMonth(1).atStartOfDay(zona).toInstant() to
                    ahora.atTime(23, 59, 59).atZone(zona).toInstant()
        }
    }
}
