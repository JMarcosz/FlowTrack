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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TransaccionesState(
    val isLoading: Boolean = false,
    val transacciones: List<Transaccion> = emptyList(),
    val searchQuery: String = "",
    val filtroTipo: TipoTransaccionFiltro = TipoTransaccionFiltro.TODAS,
    val error: String? = null
)

enum class TipoTransaccionFiltro { TODAS, INGRESOS, GASTOS }

@HiltViewModel
class TransaccionesViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: TransaccionRepository,
    private val reglaRepository: ReglaCategoriaRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TransaccionesState())
    val state: StateFlow<TransaccionesState> = _state

    private var allTransacciones: List<Transaccion> = emptyList()

    init {
        cargarTransacciones()
    }

    fun cargarTransacciones() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repository.obtenerTransacciones(uid, limite = 2000)
            when (result) {
                is AppResult.Success -> {
                    allTransacciones = result.data
                    aplicarFiltros()
                }
                is AppResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.error.toMensajeUsuario()
                    )
                }
            }
        }
    }

    fun setFiltroTipo(filtro: TipoTransaccionFiltro) {
        _state.value = _state.value.copy(filtroTipo = filtro)
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
            val matchTipo = when (currState.filtroTipo) {
                TipoTransaccionFiltro.TODAS -> true
                TipoTransaccionFiltro.INGRESOS -> tx.tipo == TipoTransaccion.CREDITO
                TipoTransaccionFiltro.GASTOS -> tx.tipo == TipoTransaccion.DEBITO
            }
            val matchQuery = if (queryNorm.isBlank()) true else {
                tx.descripcionNormalizada.contains(queryNorm) ||
                tx.monto.toPlainString().contains(currState.searchQuery)
            }
            matchTipo && matchQuery
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

    fun getTransaccionesAgrupadasPorDia(): Map<LocalDate, List<Transaccion>> {
        val zona = ZoneId.of("America/Santo_Domingo")
        return _state.value.transacciones.groupBy {
            it.fecha.atZone(zona).toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    fun recategorizar(tx: Transaccion, nuevaCategoria: String, aplicarATodas: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            // Actualizar la transacción individual
            val txActualizada = tx.copy(categoriaId = nuevaCategoria, categoriaAutomatica = false)
            val res = repository.actualizarTransaccion(txActualizada)
            
            if (res is AppResult.Success) {
                // Actualizar en memoria local
                allTransacciones = allTransacciones.map { if (it.id == tx.id) txActualizada else it }
                
                // Si el usuario marcó 'Aplicar a todas similares'
                if (aplicarATodas) {
                    val patron = tx.descripcionNormalizada
                    // Guardar la regla
                    reglaRepository.crearReglaPersonal(
                        uidUsuario = uid,
                        patron = patron,
                        categoriaId = nuevaCategoria,
                        tipoMatch = TipoMatch.EXACTO
                    )
                    
                    // Aplicar retroactivamente local e idealmente a BD
                    allTransacciones = allTransacciones.map {
                        if (it.descripcionNormalizada == patron && it.id != tx.id) {
                            val similarTx = it.copy(categoriaId = nuevaCategoria, categoriaAutomatica = true)
                            // Fire and forget al repositorio para no bloquear
                            launch { repository.actualizarTransaccion(similarTx) }
                            similarTx
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
