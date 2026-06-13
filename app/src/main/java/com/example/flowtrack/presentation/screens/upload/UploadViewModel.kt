package com.example.flowtrack.presentation.screens.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.usecase.ProcesarArchivoUseCase
import com.example.flowtrack.domain.usecase.ResultadoImportacion
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
    val formatos: Set<FileFormat> = setOf(formato),
)

val BANCOS_DISPONIBLES = listOf(
    BancoOpcion("BANRESERVAS", "BanReservas",      FileFormat.PDF, ProductoTipo.CUENTA,   "PDF"),
    BancoOpcion(
        "POPULAR",
        "Banco Popular",
        FileFormat.CSV,
        ProductoTipo.CUENTA,
        "CSV o PDF",
        formatos = setOf(FileFormat.CSV, FileFormat.PDF),
    ),
    BancoOpcion("QIK",         "Qik",              FileFormat.PDF, ProductoTipo.TARJETA,  "PDF"),
    BancoOpcion("CIBAO",       "Asociación Cibao", FileFormat.XLS, ProductoTipo.TARJETA,  "XLS"),
    BancoOpcion("BHD",         "BHD León",         FileFormat.PDF, ProductoTipo.CUENTA,   "PDF"),
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val procesarArchivoUseCase: ProcesarArchivoUseCase,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private data class ImportacionPendiente(
        val uri: Uri,
        val uid: String,
        val banco: BancoOpcion,
        val fechaCorteManual: LocalDate?,
        val fechaLimitePagoManual: LocalDate?,
    )

    private val _estado = MutableStateFlow<UploadEstado>(UploadEstado.Idle)
    val estado: StateFlow<UploadEstado> = _estado

    private val _dialogoClave = MutableStateFlow<DialogoClaveEstado?>(null)
    val dialogoClave: StateFlow<DialogoClaveEstado?> = _dialogoClave

    private val _bancoSeleccionado = MutableStateFlow<BancoOpcion?>(null)
    val bancoSeleccionado: StateFlow<BancoOpcion?> = _bancoSeleccionado

    private val _fechaCorteManual = MutableStateFlow<LocalDate?>(null)
    val fechaCorteManual: StateFlow<LocalDate?> = _fechaCorteManual

    private val _fechaLimitePagoManual = MutableStateFlow<LocalDate?>(null)
    val fechaLimitePagoManual: StateFlow<LocalDate?> = _fechaLimitePagoManual

    private var importacionPendiente: ImportacionPendiente? = null

    fun seleccionarBanco(banco: BancoOpcion) {
        if (!banco.disponible) {
            _estado.value = UploadEstado.Error("${banco.nombre} estará disponible próximamente.")
            return
        }
        cancelarDesbloqueo()
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

        val pendiente = ImportacionPendiente(
            uri = uri,
            uid = uid,
            banco = banco,
            fechaCorteManual = _fechaCorteManual.value,
            fechaLimitePagoManual = _fechaLimitePagoManual.value,
        )
        importacionPendiente = pendiente
        procesarImportacion(pendiente)
    }

    fun desbloquearDocumento(clave: String) {
        val pendiente = importacionPendiente ?: return
        if (clave.isBlank()) {
            _dialogoClave.value = DialogoClaveEstado(
                errorMensaje = "Ingresa la clave del documento.",
            )
            return
        }
        if (_dialogoClave.value?.procesando == true) return

        _dialogoClave.value = DialogoClaveEstado(procesando = true)
        procesarImportacion(pendiente, clave)
    }

    fun cancelarDesbloqueo() {
        if (_dialogoClave.value?.procesando == true) return
        importacionPendiente = null
        _dialogoClave.value = null
        if (_estado.value is UploadEstado.Procesando) {
            _estado.value = UploadEstado.Idle
        }
    }

    private fun procesarImportacion(
        pendiente: ImportacionPendiente,
        claveDocumento: String? = null,
    ) {
        viewModelScope.launch {
            _estado.value = UploadEstado.Procesando("Procesando archivo...")

            when (val resultado = procesarArchivoUseCase.ejecutar(
                uri                   = pendiente.uri,
                uid                   = pendiente.uid,
                bancoCodigo           = pendiente.banco.codigo,
                productoTipo          = pendiente.banco.productoTipo,
                formato               = pendiente.banco.formato,
                fechaCorteManual      = pendiente.fechaCorteManual,
                fechaLimitePagoManual = pendiente.fechaLimitePagoManual,
                claveDocumento        = claveDocumento,
            )) {
                is ResultadoImportacion.Exito -> {
                    limpiarImportacionPendiente()
                    _estado.value = UploadEstado.Exito(
                        transaccionesInsertadas = resultado.transaccionesInsertadas,
                        banco = resultado.carga.bancoCodigo,
                    )
                }
                is ResultadoImportacion.Error -> {
                    limpiarImportacionPendiente()
                    _estado.value = UploadEstado.Error(resultado.error.error.toMensajeUsuario())
                }
                ResultadoImportacion.ClaveRequerida -> {
                    _estado.value = UploadEstado.Idle
                    _dialogoClave.value = DialogoClaveEstado()
                }
                ResultadoImportacion.ClaveIncorrecta -> {
                    _estado.value = UploadEstado.Idle
                    _dialogoClave.value = DialogoClaveEstado(
                        errorMensaje = "La clave no es correcta. Inténtalo de nuevo.",
                    )
                }
            }
        }
    }

    private fun limpiarImportacionPendiente() {
        importacionPendiente = null
        _dialogoClave.value = null
    }

    fun resetear() {
        limpiarImportacionPendiente()
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

data class DialogoClaveEstado(
    val errorMensaje: String? = null,
    val procesando: Boolean = false,
)
