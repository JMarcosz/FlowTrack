package com.example.flowtrack.presentation.screens.metas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtrack.data.firestore.repositories.MetaRepository
import com.example.flowtrack.domain.model.Meta
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class MetasState(
    val isLoading: Boolean = true,
    val metas: List<Meta> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MetasViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: MetaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MetasState())
    val state: StateFlow<MetasState> = _state

    init {
        cargar()
    }

    private fun cargar() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.observarMetas(uid)
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                .collect { lista ->
                    _state.update { it.copy(isLoading = false, metas = lista.sortedBy { m -> m.completada }) }
                }
        }
    }

    fun guardar(nombre: String, emoji: String, montoObjetivo: BigDecimal) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val meta = Meta(
                id = UUID.randomUUID().toString(),
                uidUsuario = uid,
                nombre = nombre,
                emoji = emoji,
                montoObjetivo = montoObjetivo,
                montoActual = BigDecimal.ZERO,
                fechaLimite = null,
                activa = true,
                creadoEn = Instant.now(),
            )
            repository.guardarMeta(meta)
        }
    }

    fun depositar(meta: Meta, monto: BigDecimal) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val nuevo = (meta.montoActual + monto).min(meta.montoObjetivo)
            repository.actualizarMonto(uid, meta.id, nuevo)
        }
    }

    fun eliminar(id: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch { repository.eliminarMeta(uid, id) }
    }
}
