package com.example.flowtrack.presentation.screens.notificaciones

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.core.workers.NotificacionScheduler
import com.example.flowtrack.data.firestore.repositories.NotificacionConfigRepository
import com.example.flowtrack.domain.model.NotificacionConfig
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificacionesViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: NotificacionConfigRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _config = MutableStateFlow(NotificacionConfig(uidUsuario = ""))
    val config: StateFlow<NotificacionConfig> = _config

    init {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                repository.observar(uid).collectLatest { _config.value = it }
            }
        }
    }

    private fun actualizar(transform: (NotificacionConfig) -> NotificacionConfig) {
        val uid = auth.currentUser?.uid ?: return
        val nueva = transform(_config.value).copy(uidUsuario = uid)
        _config.value = nueva // optimista
        viewModelScope.launch {
            repository.guardar(nueva)
            NotificacionScheduler.aplicar(context, nueva)
        }
    }

    fun setActiva(v: Boolean) = actualizar { it.copy(activa = v) }
    fun setPago7(v: Boolean) = actualizar { it.copy(pago7dias = v) }
    fun setPago3(v: Boolean) = actualizar { it.copy(pago3dias = v) }
    fun setPago1(v: Boolean) = actualizar { it.copy(pago1dia = v) }
    fun setPagoMismoDia(v: Boolean) = actualizar { it.copy(pagoMismoDia = v) }
    fun setResumenMensual(v: Boolean) = actualizar { it.copy(resumenMensual = v) }
    fun setAlertasGastosAltos(v: Boolean) = actualizar { it.copy(alertasGastosAltos = v) }

    /** Dispara un chequeo inmediato de recordatorios (para verificación en dispositivo). */
    fun probarNotificacion() = NotificacionScheduler.dispararPruebaInmediata(context)
}
