package com.example.flowtrack.presentation.screens.exportar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.store.AppDataStore
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.usecase.ExportacionUseCase
import com.example.flowtrack.domain.usecase.FiltroExportacion
import com.example.flowtrack.domain.usecase.FormatoExportacion
import com.example.flowtrack.domain.usecase.SeccionExportacionXlsx
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ExportarState(
    val formato: FormatoExportacion = FormatoExportacion.XLSX,
    val fechaInicio: LocalDate = LocalDate.now().minusMonths(6),
    val fechaFin: LocalDate = LocalDate.now(),
    val cuentas: List<Cuenta> = emptyList(),
    val tarjetas: List<Tarjeta> = emptyList(),
    val cuentasSeleccionadas: Set<String> = emptySet(),
    val tarjetasSeleccionadas: Set<String> = emptySet(),
    val seccionesXlsx: Set<SeccionExportacionXlsx> = SeccionExportacionXlsx.entries.toSet(),
    val isExporting: Boolean = false,
    val error: String? = null,
    val exito: String? = null,
)

@HiltViewModel
class ExportarViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val store: AppDataStore,
    private val exportacionUseCase: ExportacionUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportarState())
    val state: StateFlow<ExportarState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.cuentas.collect { cuentas ->
                val prev = _state.value.cuentasSeleccionadas
                val ids = cuentas.map { it.id }.toSet()
                _state.value = _state.value.copy(
                    cuentas = cuentas.filter { it.activa },
                    cuentasSeleccionadas = if (prev.isEmpty()) ids else prev.intersect(ids).ifEmpty { ids },
                )
            }
        }
        viewModelScope.launch {
            store.tarjetas.collect { tarjetas ->
                val prev = _state.value.tarjetasSeleccionadas
                val ids = tarjetas.map { it.id }.toSet()
                _state.value = _state.value.copy(
                    tarjetas = tarjetas.filter { it.activa },
                    tarjetasSeleccionadas = if (prev.isEmpty()) ids else prev.intersect(ids).ifEmpty { ids },
                )
            }
        }
    }

    fun setFormato(formato: FormatoExportacion) {
        _state.value = _state.value.copy(formato = formato)
    }

    fun setFechaInicio(fecha: LocalDate) {
        if (fecha.isAfter(_state.value.fechaFin)) return
        _state.value = _state.value.copy(fechaInicio = fecha)
    }

    fun setFechaFin(fecha: LocalDate) {
        if (fecha.isBefore(_state.value.fechaInicio)) return
        _state.value = _state.value.copy(fechaFin = fecha)
    }

    fun toggleCuenta(id: String) {
        _state.value = _state.value.copy(
            cuentasSeleccionadas = _state.value.cuentasSeleccionadas.toggle(id),
        )
    }

    fun toggleTarjeta(id: String) {
        _state.value = _state.value.copy(
            tarjetasSeleccionadas = _state.value.tarjetasSeleccionadas.toggle(id),
        )
    }

    fun toggleSeccion(seccion: SeccionExportacionXlsx) {
        _state.value = _state.value.copy(
            seccionesXlsx = _state.value.seccionesXlsx.toggle(seccion),
        )
    }

    fun exportar() {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = _state.value.copy(error = "No hay sesiÃ³n activa.")
            return
        }
        val estado = _state.value
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, error = null, exito = null)
            val zona = ZoneId.of("America/Santo_Domingo")
            val inicio = estado.fechaInicio.atStartOfDay(zona).toInstant()
            val fin = estado.fechaFin.plusDays(1).atStartOfDay(zona).minusNanos(1).toInstant()
            val filtro = FiltroExportacion(
                formato = estado.formato,
                inicio = inicio,
                fin = fin,
                cuentaIds = estado.cuentasSeleccionadas,
                tarjetaIds = estado.tarjetasSeleccionadas,
                seccionesXlsx = estado.seccionesXlsx,
            )
            when (val res = exportacionUseCase.exportar(context, uid, filtro)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isExporting = false, exito = "ExportaciÃ³n generada.")
                    compartir(res.data, mimeType(estado.formato), tituloShare(estado.formato))
                }
                is AppResult.Error -> {
                    _state.value = _state.value.copy(isExporting = false, error = res.error.toMensajeUsuario())
                }
            }
        }
    }

    fun clearMensajes() {
        _state.value = _state.value.copy(error = null, exito = null)
    }

    private fun compartir(uri: Uri, mimeType: String, titulo: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, titulo).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun mimeType(formato: FormatoExportacion): String = when (formato) {
        FormatoExportacion.CSV -> "text/csv"
        FormatoExportacion.PDF -> "application/pdf"
        FormatoExportacion.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    private fun tituloShare(formato: FormatoExportacion): String = when (formato) {
        FormatoExportacion.CSV -> "Compartir CSV"
        FormatoExportacion.PDF -> "Compartir PDF"
        FormatoExportacion.XLSX -> "Compartir XLSX"
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (contains(value)) this - value else this + value
