package com.example.flowtrack.presentation.screens.duplicados

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DuplicadosViewModel @Inject constructor() : ViewModel() {

    private val _estado = MutableStateFlow<DuplicadosEstado>(DuplicadosEstado.Vacio)
    val estado: StateFlow<DuplicadosEstado> = _estado

    fun cargarPares(pares: List<ParDuplicado>) {
        _estado.value = if (pares.isEmpty()) DuplicadosEstado.Vacio else DuplicadosEstado.ConDatos(pares)
    }
}

sealed class DuplicadosEstado {
    object Vacio : DuplicadosEstado()
    data class ConDatos(val pares: List<ParDuplicado>) : DuplicadosEstado()
}
