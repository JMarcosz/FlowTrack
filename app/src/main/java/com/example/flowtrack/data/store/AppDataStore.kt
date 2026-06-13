package com.example.flowtrack.data.store

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.usecase.ObtenerBalancesPorCuentaUseCase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de verdad compartida a nivel app para datos que no dependen de rango de fechas.
 * El UID se establece automáticamente via addAuthStateListener — no requiere inicialización manual.
 * WhileSubscribed(5_000) mantiene la suscripción activa 5 s después del último consumidor,
 * evitando re-suscribir al snapshot listener en cambios rápidos de tab.
 *
 * balancesPorCuenta se recomputa automáticamente cuando cambian las cuentas (= tras cada
 * importación), garantizando que todas las pantallas muestren el mismo balance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AppDataStore @Inject constructor(
    auth: FirebaseAuth,
    cuentaRepository: CuentaRepository,
    tarjetaRepository: TarjetaRepository,
    private val balancesUseCase: ObtenerBalancesPorCuentaUseCase,
) {
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uidFlow = MutableStateFlow(auth.currentUser?.uid)

    init {
        auth.addAuthStateListener { fa -> _uidFlow.value = fa.currentUser?.uid }
    }

    val cuentas: StateFlow<List<Cuenta>> = _uidFlow
        .flatMapLatest { uid ->
            if (uid != null) cuentaRepository.observarCuentas(uid) else flowOf(emptyList())
        }
        .stateIn(storeScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tarjetas: StateFlow<List<Tarjeta>> = _uidFlow
        .flatMapLatest { uid ->
            if (uid != null) tarjetaRepository.observarTarjetas(uid) else flowOf(emptyList())
        }
        .stateIn(storeScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _balancesPorCuenta = MutableStateFlow<Map<String, BigDecimal>>(emptyMap())
    val balancesPorCuenta: StateFlow<Map<String, BigDecimal>> = _balancesPorCuenta

    init {
        // Recomputar balances al iniciar sesión y cada vez que Firestore actualice cuentas
        // (lo que ocurre tras cada importación exitosa).
        storeScope.launch {
            _uidFlow.collect { uid ->
                if (uid != null) refreshBalances(uid)
                else _balancesPorCuenta.value = emptyMap()
            }
        }
        storeScope.launch {
            cuentas.drop(1).distinctUntilChanged().collect {
                val uid = _uidFlow.value ?: return@collect
                refreshBalances(uid)
            }
        }
    }

    suspend fun refreshBalances(uid: String) {
        val result = balancesUseCase.ejecutar(uid)
        if (result is AppResult.Success) {
            _balancesPorCuenta.value = result.data
        }
    }
}
