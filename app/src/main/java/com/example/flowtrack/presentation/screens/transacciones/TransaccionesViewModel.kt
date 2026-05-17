package com.example.flowtrack.presentation.screens.transacciones

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransaccionesState(
    val isLoading: Boolean = false,
    val transacciones: List<Transaccion> = emptyList(),
    val searchQuery: String = "",
    val filtroTipo: TipoTransaccionFiltro = TipoTransaccionFiltro.TODAS,
    val bancosDisponibles: List<String> = emptyList(),
    val bancoFiltro: String? = null,
    val error: String? = null,
)

enum class TipoTransaccionFiltro { TODAS, DEBITO, CREDITO }

@HiltViewModel
class TransaccionesViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: TransaccionRepository,
    private val reglaRepository: ReglaCategoriaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransaccionesState())
    val state: StateFlow<TransaccionesState> = _state

    private var allTransacciones: List<Transaccion> = emptyList()

    fun cargarTransacciones() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = repository.obtenerTransacciones(uid, limite = 2000)) {
                is AppResult.Success -> {
                    allTransacciones = result.data
                    val bancos = allTransacciones
                        .map { it.bancoCodigo }
                        .distinct()
                        .sorted()
                    _state.value = _state.value.copy(bancosDisponibles = bancos)
                    aplicarFiltros()
                }
                is AppResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.error.toMensajeUsuario(),
                    )
                }
            }
        }
    }

    fun setFiltroTipo(filtro: TipoTransaccionFiltro) {
        _state.value = _state.value.copy(filtroTipo = filtro)
        aplicarFiltros()
    }

    fun setBancoFiltro(banco: String?) {
        _state.value = _state.value.copy(bancoFiltro = banco)
        aplicarFiltros()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        aplicarFiltros()
    }

    private fun aplicarFiltros() {
        val currState = _state.value
        val queryNorm = currState.searchQuery.normalizarDescripcion()

        val filtradas = allTransacciones.filter { tx ->
            // Derivadas siempre acompañan a su transacción padre — no se filtran individualmente
            if (tx.esDerivada) return@filter true

            val matchBanco = currState.bancoFiltro == null || tx.bancoCodigo == currState.bancoFiltro

            val matchTipo = when (currState.filtroTipo) {
                TipoTransaccionFiltro.TODAS  -> true
                TipoTransaccionFiltro.DEBITO -> tx.tipo == TipoTransaccion.DEBITO
                TipoTransaccionFiltro.CREDITO -> tx.tipo == TipoTransaccion.CREDITO
            }

            val matchQuery = if (queryNorm.isBlank()) true else {
                tx.descripcionNormalizada.normalizarDescripcion().contains(queryNorm) ||
                    tx.descripcionOriginal.normalizarDescripcion().contains(queryNorm) ||
                    tx.bancoCodigo.normalizarDescripcion().contains(queryNorm) ||
                    tx.monto.toPlainString().contains(currState.searchQuery.trim())
            }

            matchBanco && matchTipo && matchQuery
        }

        _state.value = currState.copy(isLoading = false, transacciones = filtradas)
    }

    fun eliminarTransaccion(tx: Transaccion) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val res = repository.eliminarTransaccion(uid, tx.id)
            if (res is AppResult.Success) {
                allTransacciones = allTransacciones.filterNot { it.id == tx.id }
                aplicarFiltros()
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(isLoading = false, error = res.error.toMensajeUsuario())
            }
        }
    }

    /** Retorna las transacciones derivadas de un padre dado (siempre desde la lista completa filtrada). */
    fun getDerivadasParaPadre(padreId: String): List<Transaccion> =
        _state.value.transacciones.filter { it.esDerivada && it.transaccionPadreId == padreId }

    fun recategorizar(tx: Transaccion, nuevaCategoria: String, aplicarATodas: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val txActualizada = tx.copy(categoriaId = nuevaCategoria, categoriaAutomatica = false)
            val res = repository.actualizarTransaccion(txActualizada)

            if (res is AppResult.Success) {
                allTransacciones = allTransacciones.map { if (it.id == tx.id) txActualizada else it }

                if (aplicarATodas) {
                    val patron = tx.descripcionNormalizada
                    reglaRepository.crearReglaPersonal(
                        uidUsuario = uid,
                        patron = patron,
                        categoriaId = nuevaCategoria,
                        tipoMatch = TipoMatch.EXACTO,
                    )
                    allTransacciones = allTransacciones.map {
                        if (it.descripcionNormalizada == patron && it.id != tx.id) {
                            val similar = it.copy(categoriaId = nuevaCategoria, categoriaAutomatica = true)
                            launch { repository.actualizarTransaccion(similar) }
                            similar
                        } else it
                    }
                }

                aplicarFiltros()
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(isLoading = false, error = res.error.toMensajeUsuario())
            }
        }
    }
}
