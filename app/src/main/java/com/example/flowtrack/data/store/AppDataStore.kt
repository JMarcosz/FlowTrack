package com.example.flowtrack.data.store

import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Tarjeta
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de verdad compartida a nivel app para datos que no dependen de rango de fechas.
 * El UID se establece automáticamente via addAuthStateListener — no requiere inicialización manual.
 * WhileSubscribed(5_000) mantiene la suscripción activa 5 s después del último consumidor,
 * evitando re-suscribir al snapshot listener en cambios rápidos de tab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AppDataStore @Inject constructor(
    auth: FirebaseAuth,
    cuentaRepository: CuentaRepository,
    tarjetaRepository: TarjetaRepository,
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
}
