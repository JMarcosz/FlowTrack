package com.example.flowtrack.presentation.screens.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.usecases.carga.ProcesarArchivoUseCase
import com.example.flowtrack.domain.usecases.carga.ResultadoImportacion
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Catálogo estático de bancos. [disponible] = false indica "próximamente". */
data class BancoOpcion(
    val codigo: String,
    val nombre: String,
    val formato: FileFormat,
    val productoTipo: ProductoTipo,
    val formatoLabel: String,
    val disponible: Boolean = true,
)

val BANCOS_DISPONIBLES = listOf(
    BancoOpcion("BANRESERVAS", "BanReservas",      FileFormat.PDF, ProductoTipo.CUENTA,   "PDF"),
    BancoOpcion("POPULAR",     "Banco Popular",    FileFormat.CSV, ProductoTipo.CUENTA,   "CSV"),
    BancoOpcion("QIK",         "Qik",              FileFormat.PDF, ProductoTipo.TARJETA,  "PDF"),
    BancoOpcion("CIBAO",       "Asociación Cibao", FileFormat.XLS, ProductoTipo.TARJETA,  "XLS"),
    BancoOpcion("BHD",         "BHD León",         FileFormat.PDF, ProductoTipo.CUENTA,   "PDF", disponible = false),
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val procesarArchivoUseCase: ProcesarArchivoUseCase,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _estado = MutableStateFlow<UploadEstado>(UploadEstado.Idle)
    val estado: StateFlow<UploadEstado> = _estado

    private val _bancoSeleccionado = MutableStateFlow<BancoOpcion?>(null)
    val bancoSeleccionado: StateFlow<BancoOpcion?> = _bancoSeleccionado

    private val _fechaCorteManual = MutableStateFlow<LocalDate?>(null)
    val fechaCorteManual: StateFlow<LocalDate?> = _fechaCorteManual

    private val _fechaLimitePagoManual = MutableStateFlow<LocalDate?>(null)
    val fechaLimitePagoManual: StateFlow<LocalDate?> = _fechaLimitePagoManual

    fun seleccionarBanco(banco: BancoOpcion) {
        if (!banco.disponible) {
            _estado.value = UploadEstado.Error("${banco.nombre} estará disponible próximamente.")
            return
        }
        _bancoSeleccionado.value = banco
        _fechaCorteManual.value = null
        _fechaLimitePagoManual.value = null
        if (_estado.value is UploadEstado.Error) _estado.value = UploadEstado.Idle
    }

    fun setFechaCorte(fecha: LocalDate?) { _fechaCorteManual.value = fecha }
    fun setFechaLimitePago(fecha: LocalDate?) { _fechaLimitePagoManual.value = fecha }

    fun procesarArchivo(uri: Uri) {
        val uid = auth.currentUser?.uid ?: run {
            _estado.value = UploadEstado.Error("Debes iniciar sesión para importar archivos.")
            return
        }
        val banco = _bancoSeleccionado.value ?: run {
            _estado.value = UploadEstado.Error("Selecciona un banco antes de importar.")
            return
        }

        viewModelScope.launch {
            _estado.value = UploadEstado.Procesando("Procesando archivo...")

            when (val resultado = procesarArchivoUseCase.ejecutar(
                uri                   = uri,
                uid                   = uid,
                bancoCodigo           = banco.codigo,
                productoTipo          = banco.productoTipo,
                formato               = banco.formato,
                fechaCorteManual      = _fechaCorteManual.value,
                fechaLimitePagoManual = _fechaLimitePagoManual.value,
            )) {
                is ResultadoImportacion.Exito -> _estado.value = UploadEstado.Exito(
                    transaccionesInsertadas = resultado.transaccionesInsertadas,
                    banco = resultado.carga.bancoCodigo,
                )
                is ResultadoImportacion.Error -> _estado.value = UploadEstado.Error(
                    resultado.error.error.toMensajeUsuario()
                )
            }
        }
    }

    fun resetear() {
        _estado.value = UploadEstado.Idle
        _bancoSeleccionado.value = null
        _fechaCorteManual.value = null
        _fechaLimitePagoManual.value = null
    }
}

// ─── Estados de pantalla ──────────────────────────────────────────────────────

sealed class UploadEstado {
    object Idle : UploadEstado()
    data class Procesando(val mensaje: String = "Procesando archivo...") : UploadEstado()
    data class Exito(val transaccionesInsertadas: Int, val banco: String) : UploadEstado()
    data class Error(val mensaje: String) : UploadEstado()
}
