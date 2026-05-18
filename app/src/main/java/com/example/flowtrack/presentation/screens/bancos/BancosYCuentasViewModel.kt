package com.example.flowtrack.presentation.screens.bancos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoCuenta
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

sealed class BancosEstado {
    object Cargando : BancosEstado()
    object Vacio : BancosEstado()
    data class ConDatos(val cuentas: List<Cuenta>) : BancosEstado()
    data class Error(val mensaje: String) : BancosEstado()
}

@HiltViewModel
class BancosYCuentasViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val store: AppDataStore,
    private val cuentaRepository: CuentaRepository,
) : ViewModel() {

    // Reactivo: se actualiza automáticamente cuando Firestore cambia
    val estado: StateFlow<BancosEstado> = store.cuentas
        .map { cuentas ->
            val activas = cuentas.filter { it.activa }
            if (activas.isEmpty()) BancosEstado.Vacio else BancosEstado.ConDatos(activas)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BancosEstado.Cargando)

    fun guardarCuenta(
        alias: String,
        bancoCodigo: String,
        numeroCuenta: String,
        tipoCuenta: TipoCuenta,
        moneda: Moneda,
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val cuenta = Cuenta(
                id = UUID.randomUUID().toString(),
                uidUsuario = uid,
                bancoCodigo = bancoCodigo,
                numeroCuenta = numeroCuenta.ifBlank { "MANUAL" },
                numeroCuentaCompleto = null,
                alias = alias,
                tipoCuenta = tipoCuenta,
                moneda = moneda,
                balanceActual = null,
                balanceAlCorte = null,
                fechaUltimoCorte = null,
                titular = "",
                activa = true,
                mostrarEnDashboard = true,
                ultimaSincronizacion = null,
                creadoEn = Instant.now(),
            )
            cuentaRepository.guardarCuenta(cuenta)
            // No se necesita cargar() — el snapshot listener actualiza el estado automáticamente
        }
    }

    fun eliminarCuenta(cuentaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            cuentaRepository.eliminarCuenta(uid, cuentaId)
            // No se necesita cargar() — el snapshot listener actualiza el estado automáticamente
        }
    }
}
