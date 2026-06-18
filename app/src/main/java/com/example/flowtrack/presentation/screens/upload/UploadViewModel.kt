package com.example.flowtrack.presentation.screens.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.data.parsers.core.ArchivoEntrada
import com.example.flowtrack.domain.model.BancoSoportado
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.FormatoArchivo
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.usecase.ObtenerBancosSoportadosUseCase
import com.example.flowtrack.domain.usecase.ProcesarArchivoUseCase
import com.example.flowtrack.domain.usecase.ResultadoImportacion
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BancoOpcion(
    val codigo: String,
    val nombre: String,
    val formato: FileFormat,
    val productoTipo: ProductoTipo,
    val formatoLabel: String,
    val disponible: Boolean = true,
    val formatos: Set<FileFormat> = setOf(formato),
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val procesarArchivoUseCase: ProcesarArchivoUseCase,
    private val obtenerBancosSoportadosUseCase: ObtenerBancosSoportadosUseCase,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private data class ImportacionPendiente(
        val archivo: ArchivoEntrada,
        val uid: String,
        val banco: BancoOpcion,
        val fechaCorteManual: LocalDate?,
        val fechaLimitePagoManual: LocalDate?,
    )

    private val _estado = MutableStateFlow<UploadEstado>(UploadEstado.Idle)
    val estado: StateFlow<UploadEstado> = _estado.asStateFlow()

    private val _dialogoClave = MutableStateFlow<DialogoClaveEstado?>(null)
    val dialogoClave: StateFlow<DialogoClaveEstado?> = _dialogoClave.asStateFlow()

    private val _bancoSeleccionado = MutableStateFlow<BancoOpcion?>(null)
    val bancoSeleccionado: StateFlow<BancoOpcion?> = _bancoSeleccionado.asStateFlow()

    private val _fechaCorteManual = MutableStateFlow<LocalDate?>(null)
    val fechaCorteManual: StateFlow<LocalDate?> = _fechaCorteManual.asStateFlow()

    private val _fechaLimitePagoManual = MutableStateFlow<LocalDate?>(null)
    val fechaLimitePagoManual: StateFlow<LocalDate?> = _fechaLimitePagoManual.asStateFlow()

    private val _bancosDisponibles = MutableStateFlow(obtenerBancos())
    val bancosDisponibles: StateFlow<List<BancoOpcion>> = _bancosDisponibles.asStateFlow()

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

    fun setFechaCorte(fecha: LocalDate?) {
        _fechaCorteManual.value = fecha
    }

    fun setFechaLimitePago(fecha: LocalDate?) {
        _fechaLimitePagoManual.value = fecha
    }

    fun procesarArchivo(archivo: ArchivoEntrada) {
        val uid = auth.currentUser?.uid ?: run {
            _estado.value = UploadEstado.Error("Debes iniciar sesión para importar archivos.")
            return
        }
        val banco = _bancoSeleccionado.value ?: run {
            _estado.value = UploadEstado.Error("Selecciona un banco antes de importar.")
            return
        }

        val pendiente = ImportacionPendiente(
            archivo = archivo,
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
                archivo = pendiente.archivo,
                uid = pendiente.uid,
                bancoCodigo = pendiente.banco.codigo,
                productoTipo = pendiente.banco.productoTipo,
                formato = pendiente.banco.formato,
                fechaCorteManual = pendiente.fechaCorteManual,
                fechaLimitePagoManual = pendiente.fechaLimitePagoManual,
                claveDocumento = claveDocumento,
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

    private fun obtenerBancos(): List<BancoOpcion> {
        return obtenerBancosSoportadosUseCase().map { banco ->
            banco.toOpcion()
        }
    }

    private fun BancoSoportado.toOpcion(): BancoOpcion {
        val formatoPrincipal = when (formatosPermitidos.firstOrNull() ?: FormatoArchivo.PDF) {
            FormatoArchivo.PDF -> FileFormat.PDF
            FormatoArchivo.CSV -> FileFormat.CSV
            FormatoArchivo.XLS -> FileFormat.XLS
            FormatoArchivo.XLSX -> FileFormat.XLSX
        }
        val formatos = formatosPermitidos.map {
            when (it) {
                FormatoArchivo.PDF -> FileFormat.PDF
                FormatoArchivo.CSV -> FileFormat.CSV
                FormatoArchivo.XLS -> FileFormat.XLS
                FormatoArchivo.XLSX -> FileFormat.XLSX
            }
        }.toSet()
        return BancoOpcion(
            codigo = codigo,
            nombre = nombre,
            formato = formatoPrincipal,
            productoTipo = productoTipo,
            formatoLabel = formatos.joinToString(" o ") { it.name },
            disponible = disponible,
            formatos = formatos,
        )
    }
}

sealed class UploadEstado {
    data object Idle : UploadEstado()
    data class Procesando(val mensaje: String = "Procesando archivo...") : UploadEstado()
    data class Exito(val transaccionesInsertadas: Int, val banco: String) : UploadEstado()
    data class Error(val mensaje: String) : UploadEstado()
}

data class DialogoClaveEstado(
    val errorMensaje: String? = null,
    val procesando: Boolean = false,
)
