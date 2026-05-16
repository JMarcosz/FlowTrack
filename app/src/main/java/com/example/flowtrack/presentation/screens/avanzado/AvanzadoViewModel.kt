package com.example.flowtrack.presentation.screens.avanzado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.LimpiezaRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AvanzadoEstado {
    object Idle : AvanzadoEstado()
    object Cargando : AvanzadoEstado()
    data class Exito(val documentosEliminados: Int) : AvanzadoEstado()
    data class Error(val mensaje: String) : AvanzadoEstado()
}

@HiltViewModel
class AvanzadoViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val limpiezaRepository: LimpiezaRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow<AvanzadoEstado>(AvanzadoEstado.Idle)
    val estado: StateFlow<AvanzadoEstado> = _estado

    fun borrarTodosMisDatos() {
        val uid = auth.currentUser?.uid ?: run {
            _estado.value = AvanzadoEstado.Error("No hay sesión activa.")
            return
        }
        viewModelScope.launch {
            _estado.value = AvanzadoEstado.Cargando
            val result = limpiezaRepository.borrarTodosMisDatos(uid)
            _estado.value = when (result) {
                is AppResult.Success -> AvanzadoEstado.Exito(result.data)
                is AppResult.Error   -> AvanzadoEstado.Error(result.error.toMensajeUsuario())
            }
        }
    }

    fun resetearEstado() {
        _estado.value = AvanzadoEstado.Idle
    }
}
