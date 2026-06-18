package com.example.flowtrack.presentation.screens.metas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.CategoriaMeta
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.usecase.CancelarMetaUseCase
import com.example.flowtrack.domain.usecase.CrearMetaUseCase
import com.example.flowtrack.domain.usecase.CuentaMetaDisponible
import com.example.flowtrack.domain.usecase.DepositarEnMetaUseCase
import com.example.flowtrack.domain.usecase.ObservarMetasUseCase
import com.example.flowtrack.domain.usecase.ObtenerCuentasDisponiblesParaMetasUseCase
import com.example.flowtrack.domain.usecase.RetirarDeMetaUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

data class MetasState(
    val isLoading: Boolean = true,
    val metas: List<Meta> = emptyList(),
    val cuentasDisponibles: List<CuentaMetaDisponible> = emptyList(),
)

sealed interface MetasEvent {
    data class MostrarMensaje(val mensaje: String) : MetasEvent
}

@HiltViewModel
class MetasViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val observarMetasUseCase: ObservarMetasUseCase,
    private val crearMetaUseCase: CrearMetaUseCase,
    private val cuentasDisponiblesUseCase: ObtenerCuentasDisponiblesParaMetasUseCase,
    private val depositarEnMetaUseCase: DepositarEnMetaUseCase,
    private val retirarDeMetaUseCase: RetirarDeMetaUseCase,
    private val cancelarMetaUseCase: CancelarMetaUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MetasState())
    val state: StateFlow<MetasState> = _state

    private val _events = Channel<MetasEvent>()
    val events = _events.receiveAsFlow()

    init {
        cargar()
        cargarCuentasDisponibles()
    }

    private fun cargar() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            observarMetasUseCase(uid)
                .catch { e ->
                    _state.update { it.copy(isLoading = false) }
                    emitirMensaje(e.message ?: "No se pudieron cargar las metas")
                }
                .collect { lista ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            metas = lista.sortedWith(compareBy<Meta> { it.completada }.thenByDescending { meta -> meta.actualizadaEn }),
                        )
                    }
                }
        }
    }

    fun cargarCuentasDisponibles() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            when (val result = cuentasDisponiblesUseCase(uid)) {
                is AppResult.Success -> _state.update { it.copy(cuentasDisponibles = result.data) }
                is AppResult.Error -> emitirMensaje(result.error.toMensajeUsuario())
            }
        }
    }

    fun guardar(
        nombre: String,
        emoji: String,
        montoObjetivo: BigDecimal,
        categoria: CategoriaMeta,
        cuentaId: String?,
        descripcion: String?,
        fechaObjetivo: Instant?,
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            when (val result = crearMetaUseCase(uid, nombre, montoObjetivo, categoria, cuentaId, descripcion, fechaObjetivo, emoji)) {
                is AppResult.Success -> {
                    emitirMensaje("Tu meta fue creada exitosamente.")
                    cargarCuentasDisponibles()
                }
                is AppResult.Error -> emitirMensaje(result.error.toMensajeUsuario())
            }
        }
    }

    fun depositar(meta: Meta, cuentaId: String, monto: BigDecimal) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            when (val result = depositarEnMetaUseCase(uid, meta, cuentaId, monto)) {
                is AppResult.Success -> {
                    emitirMensaje("Has agregado ${monto.toPlainString()} a tu meta ${meta.nombre}.")
                    cargarCuentasDisponibles()
                }
                is AppResult.Error -> emitirMensaje(result.error.toMensajeUsuario())
            }
        }
    }

    fun retirar(meta: Meta, cuentaId: String, monto: BigDecimal) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            when (val result = retirarDeMetaUseCase(uid, meta, cuentaId, monto)) {
                is AppResult.Success -> {
                    emitirMensaje("Retiro registrado desde ${meta.nombre}.")
                    cargarCuentasDisponibles()
                }
                is AppResult.Error -> emitirMensaje(result.error.toMensajeUsuario())
            }
        }
    }

    fun cancelar(id: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            when (val result = cancelarMetaUseCase(uid, id)) {
                is AppResult.Success -> {
                    emitirMensaje("Meta cancelada.")
                    cargarCuentasDisponibles()
                }
                is AppResult.Error -> emitirMensaje(result.error.toMensajeUsuario())
            }
        }
    }

    private suspend fun emitirMensaje(mensaje: String) {
        _events.send(MetasEvent.MostrarMensaje(mensaje))
    }
}
