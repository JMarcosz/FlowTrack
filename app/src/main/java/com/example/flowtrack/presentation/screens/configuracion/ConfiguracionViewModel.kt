package com.example.flowtrack.presentation.screens.configuracion

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.ConfiguracionRepository
import com.example.flowtrack.data.firestore.repositories.LimpiezaRepository
import com.example.flowtrack.domain.model.ConfiguracionUsuario
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.usecase.ExportacionUseCase
import com.example.flowtrack.domain.usecase.ObtenerBalanceNetoUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ConfiguracionState(
    val config: ConfiguracionUsuario = ConfiguracionUsuario(""),
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val isExportingPdf: Boolean = false,
    val isDeleting: Boolean = false,
    val balanceNeto: BigDecimal = BigDecimal.ZERO,
    val error: String? = null,
    val exito: String? = null
)

@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val configuracionRepository: ConfiguracionRepository,
    private val limpiezaRepository: LimpiezaRepository,
    private val exportacionUseCase: ExportacionUseCase,
    private val balanceNetoUseCase: ObtenerBalanceNetoUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ConfiguracionState())
    val state: StateFlow<ConfiguracionState> = _state

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
        val nuevaConfig = _state.value.config.copy(temaOscuro = oscuro)
        guardarConfiguracion(nuevaConfig)
    }

    fun setMonedaBase(moneda: Moneda) {
        val nuevaConfig = _state.value.config.copy(monedaPredeterminada = moneda)
        guardarConfiguracion(nuevaConfig)
    }

    fun setFormatoFecha(formato: String) {
        guardarConfiguracion(_state.value.config.copy(formatoFecha = formato))
    }

    fun setFormatoMoneda(formato: String) {
        guardarConfiguracion(_state.value.config.copy(formatoMoneda = formato))
    }

    private fun guardarConfiguracion(config: ConfiguracionUsuario) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val res = configuracionRepository.actualizarConfiguracion(config)
            _state.value = _state.value.copy(
                isLoading = false,
                error = if (res is AppResult.Error) "Error al guardar preferencias" else null
            )
        }
    }

    fun exportarDatosCsv() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, error = null, exito = null)
            
            // Exportar ultimo mes por defecto o todo el año
            val zona = ZoneId.of("America/Santo_Domingo")
            val fin = Instant.now()
            val inicio = fin.atZone(zona).minusMonths(6).toInstant() // Ultimos 6 meses
            
            val res = exportacionUseCase.exportarCsv(context, uid, inicio, fin)
            
            if (res is AppResult.Success) {
                _state.value = _state.value.copy(isExporting = false, exito = "Archivo exportado con éxito")
                compartirUri(res.data)
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(isExporting = false, error = res.error.toMensajeUsuario())
            }
        }
    }

    fun exportarPdf() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExportingPdf = true, error = null, exito = null)
            val zona = ZoneId.of("America/Santo_Domingo")
            val fin = Instant.now()
            val inicio = fin.atZone(zona).minusMonths(6).toInstant()
            val res = exportacionUseCase.exportarPdf(context, uid, inicio, fin)
            if (res is AppResult.Success) {
                _state.value = _state.value.copy(isExportingPdf = false, exito = "PDF generado con éxito")
                compartirUri(res.data, "application/pdf", "Compartir PDF con...")
            } else if (res is AppResult.Error) {
                _state.value = _state.value.copy(isExportingPdf = false, error = res.error.toMensajeUsuario())
            }
        }
    }

    fun borrarTodosMisDatos() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(error = "No hay sesión activa.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true, error = null, exito = null)
            val res = limpiezaRepository.borrarTodosMisDatos(uid)
            _state.value = when (res) {
                is AppResult.Success -> _state.value.copy(
                    isDeleting = false,
                    exito = "Datos eliminados correctamente (${res.data} documentos).",
                )
                is AppResult.Error -> _state.value.copy(
                    isDeleting = false,
                    error = res.error.toMensajeUsuario(),
                )
            }
        }
    }

    private fun compartirUri(uri: Uri, mimeType: String = "text/csv", titulo: String = "Compartir con...") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, "Exportación Transacciones FlowTrack")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, titulo)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun clearMensajes() {
        _state.value = _state.value.copy(error = null, exito = null)
    }
}
