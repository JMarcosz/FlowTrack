package com.example.flowtrack.presentation.screens.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.usecases.carga.ProcesarArchivoUseCase
import com.example.flowtrack.domain.usecases.carga.ResultadoImportacion
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val procesarArchivoUseCase: ProcesarArchivoUseCase,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _estado = MutableStateFlow<UploadEstado>(UploadEstado.Idle)
    val estado: StateFlow<UploadEstado> = _estado

    fun procesarArchivo(uri: Uri) {
        val uid = auth.currentUser?.uid ?: run {
            _estado.value = UploadEstado.Error("Debes iniciar sesión para importar archivos.")
            return
        }

        viewModelScope.launch {
            _estado.value = UploadEstado.Procesando("Leyendo archivo...")

            when (val resultado = procesarArchivoUseCase.ejecutar(uri, uid)) {
                is ResultadoImportacion.Exito -> {
                    _estado.value = UploadEstado.Exito(
                        transaccionesInsertadas = resultado.transaccionesInsertadas,
                        banco = resultado.carga.bancoCodigo,
                    )
                }

                is ResultadoImportacion.RequiereSeleccion -> {
                    // TODO Sprint 2: navegar a SeleccionBancoScreen
                    // Por ahora mostrar mensaje orientativo
                    _estado.value = UploadEstado.Error(
                        "No se pudo detectar el banco automáticamente. " +
                            "Selección manual próximamente."
                    )
                }

                is ResultadoImportacion.Error -> {
                    _estado.value = UploadEstado.Error(
                        resultado.error.error.toMensajeUsuario()
                    )
                }
            }
        }
    }

    fun resetear() {
        _estado.value = UploadEstado.Idle
    }
}
