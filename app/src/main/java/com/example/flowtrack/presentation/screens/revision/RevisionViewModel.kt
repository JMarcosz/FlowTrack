package com.example.flowtrack.presentation.screens.revision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.TipoTransaccion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class RevisionViewModel @Inject constructor() : ViewModel() {

    private val _estado = MutableStateFlow<RevisionEstado>(RevisionEstado.Cargando)
    val estado: StateFlow<RevisionEstado> = _estado

    /** Llamado por UploadViewModel tras parseo exitoso, antes de persistir. */
    fun cargarResultado(
        nombreArchivo: String,
        banco: String,
        transacciones: List<TransaccionNormalizada>,
        advertencias: List<String>,
        duplicados: Int,
        periodo: String,
    ) {
        viewModelScope.launch {
            val debitos = transacciones.filter { it.tipo == TipoTransaccion.DEBITO }
            val creditos = transacciones.filter { it.tipo == TipoTransaccion.CREDITO }
            _estado.value = RevisionEstado.Listo(
                nombreArchivo = nombreArchivo,
                banco = banco,
                transacciones = transacciones,
                advertencias = advertencias,
                duplicados = duplicados,
                periodo = periodo,
                totalDebitos = debitos.fold(BigDecimal.ZERO) { a, t -> a + t.monto },
                totalCreditos = creditos.fold(BigDecimal.ZERO) { a, t -> a + t.monto },
            )
        }
    }

    fun confirmar() {
        // La persistencia real la orquesta ProcesarArchivoUseCase;
        // aquí solo señalamos que el usuario aprobó la revisión.
        _estado.value = RevisionEstado.Confirmado
    }

    fun consumirConfirmacion() {
        if (_estado.value is RevisionEstado.Confirmado) {
            _estado.value = RevisionEstado.Cargando
        }
    }
}

sealed class RevisionEstado {
    object Cargando : RevisionEstado()
    object Confirmado : RevisionEstado()
    data class Listo(
        val nombreArchivo: String,
        val banco: String,
        val transacciones: List<TransaccionNormalizada>,
        val advertencias: List<String>,
        val duplicados: Int,
        val periodo: String,
        val totalDebitos: BigDecimal,
        val totalCreditos: BigDecimal,
    ) : RevisionEstado()
    data class Error(val mensaje: String) : RevisionEstado()
}
