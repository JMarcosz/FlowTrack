package com.example.flowtrack.presentation.screens.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.ConfiguracionRepository
import com.example.flowtrack.data.firestore.repositories.LimpiezaRepository
import com.example.flowtrack.domain.model.ConfiguracionUsuario
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.usecase.ObtenerBalanceNetoUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class ConfiguracionState(
    val config: ConfiguracionUsuario = ConfiguracionUsuario(""),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val balanceNeto: BigDecimal = BigDecimal.ZERO,
)

sealed interface ConfiguracionEvent {
    data class MostrarError(val mensaje: String) : ConfiguracionEvent
    data class MostrarExito(val mensaje: String) : ConfiguracionEvent
}

@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val configuracionRepository: ConfiguracionRepository,
    private val limpiezaRepository: LimpiezaRepository,
    private val balanceNetoUseCase: ObtenerBalanceNetoUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ConfiguracionState())
    val state: StateFlow<ConfiguracionState> = _state

    private val _events = MutableSharedFlow<ConfiguracionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConfiguracionEvent> = _events.asSharedFlow()

    init {
        observarConfig()
        cargarBalance()
    }

    private fun cargarBalance() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = balanceNetoUseCase.ejecutar(uid)
            if (result is AppResult.Success) {
                _state.value = _state.value.copy(balanceNeto = result.data.neto)
            }
        }
    }

    private fun observarConfig() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            configuracionRepository.observarConfiguracion(uid).collectLatest { conf ->
                _state.value = _state.value.copy(config = conf)
            }
        }
    }

    fun toggleTema(oscuro: Boolean) {
        guardarConfiguracion(_state.value.config.copy(temaOscuro = oscuro))
    }

    fun setMonedaBase(moneda: Moneda) {
        guardarConfiguracion(_state.value.config.copy(monedaPredeterminada = moneda))
    }

    fun setFormatoFecha(formato: String) {
        guardarConfiguracion(_state.value.config.copy(formatoFecha = formato))
    }

    fun setFormatoMoneda(formato: String) {
        guardarConfiguracion(_state.value.config.copy(formatoMoneda = formato))
    }

    private fun guardarConfiguracion(config: ConfiguracionUsuario) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val res = configuracionRepository.actualizarConfiguracion(config)
            _state.value = _state.value.copy(isLoading = false)
            if (res is AppResult.Error) {
                _events.tryEmit(
                    ConfiguracionEvent.MostrarError("Error al guardar preferencias"),
                )
            } else {
                _events.tryEmit(
                    ConfiguracionEvent.MostrarExito("Configuración guardada"),
                )
            }
        }
    }

    fun borrarTodosMisDatos() {
        val uid = auth.currentUser?.uid ?: run {
            _events.tryEmit(
                ConfiguracionEvent.MostrarError("No hay sesión activa."),
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true)
            val res = limpiezaRepository.borrarTodosMisDatos(uid)
            _state.value = _state.value.copy(isDeleting = false)
            when (res) {
                is AppResult.Success -> {
                    _events.tryEmit(
                        ConfiguracionEvent.MostrarExito(
                            "Datos eliminados correctamente (${res.data} documentos).",
                        ),
                    )
                }

                is AppResult.Error -> {
                    _events.tryEmit(
                        ConfiguracionEvent.MostrarError(res.error.toMensajeUsuario()),
                    )
                }
            }
        }
    }
}
