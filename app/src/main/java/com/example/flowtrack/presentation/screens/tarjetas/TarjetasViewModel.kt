package com.example.flowtrack.presentation.screens.tarjetas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.OrigenTasa
import com.example.flowtrack.domain.model.Tarjeta
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class TarjetasState(
    val isLoading: Boolean = false,
    val tarjetas: List<Tarjeta> = emptyList(),
    val estadosPorTarjeta: Map<String, List<EstadoTarjetaSnap>> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class TarjetasViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val store: AppDataStore,
    private val repository: TarjetaRepository,
) : ViewModel() {

    private val _estadosPorTarjeta = MutableStateFlow<Map<String, List<EstadoTarjetaSnap>>>(emptyMap())
    private val _error = MutableStateFlow<String?>(null)

    // Tarjetas reactivas desde el store; estados cargados bajo demanda
    val state: StateFlow<TarjetasState> = combine(
        store.tarjetas,
        _estadosPorTarjeta,
        _error,
    ) { tarjetas, estados, error ->
        TarjetasState(
            isLoading = false,
            tarjetas = tarjetas.filter { it.activa },
            estadosPorTarjeta = estados,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TarjetasState(isLoading = true))

    init {
        // Cargar estados cuando la lista de tarjetas cambia
        viewModelScope.launch {
            store.tarjetas.collect { tarjetas ->
                val uid = auth.currentUser?.uid ?: return@collect
                val activas = tarjetas.filter { it.activa }
                val mapEstados = mutableMapOf<String, List<EstadoTarjetaSnap>>()
                for (t in activas) {
                    val res = repository.obtenerEstadosTarjeta(uid, t.id)
                    if (res is AppResult.Success) mapEstados[t.id] = res.data
                }
                _estadosPorTarjeta.value = mapEstados
            }
        }
    }

    fun guardarTarjeta(
        bancoCodigo: String,
        ultimos4: String,
        alias: String,
        limiteCredito: BigDecimal,
        diaCorte: Int,
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val tarjeta = Tarjeta(
                id = UUID.randomUUID().toString(),
                uidUsuario = uid,
                bancoCodigo = bancoCodigo,
                ultimos4 = ultimos4.take(4),
                alias = alias,
                tipoRed = null,
                limiteCredito = limiteCredito,
                moneda = Moneda.DOP,
                diaCorte = diaCorte,
                diaPago = ((diaCorte + 20 - 1) % 31) + 1,
                tasaInteresAnual = 0.0,
                tasaInteresOrigen = OrigenTasa.AUTO_EXTRAIDA,
                estado = EstadoTarjeta.ACTIVO,
                titular = "",
                activa = true,
                ultimaSincronizacion = null,
                creadoEn = Instant.now(),
            )
            repository.guardarTarjeta(tarjeta)
            // El snapshot listener del store actualiza tarjetas automáticamente
        }
    }

    fun eliminarTarjeta(tarjetaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.eliminarTarjeta(uid, tarjetaId)
            // El snapshot listener del store actualiza tarjetas automáticamente
        }
    }
}
