package com.example.flowtrack.presentation.screens.bancos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoCuenta
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val cuentaRepository: CuentaRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow<BancosEstado>(BancosEstado.Cargando)
    val estado: StateFlow<BancosEstado> = _estado

    init {
        cargar()
    }

    fun cargar() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _estado.value = BancosEstado.Cargando
            when (val result = cuentaRepository.obtenerCuentas(uid)) {
                is AppResult.Success -> {
                    val cuentas = result.data.filter { it.activa }
                    _estado.value = if (cuentas.isEmpty()) BancosEstado.Vacio else BancosEstado.ConDatos(cuentas)
                }
                is AppResult.Error -> _estado.value = BancosEstado.Error(result.error.toMensajeUsuario())
            }
        }
    }

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
            cargar()
        }
    }

    fun eliminarCuenta(cuentaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            cuentaRepository.eliminarCuenta(uid, cuentaId)
            cargar()
        }
    }
}
