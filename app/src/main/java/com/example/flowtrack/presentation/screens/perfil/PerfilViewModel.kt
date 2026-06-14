package com.example.flowtrack.presentation.screens.perfil

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class PerfilState(
    val nombre: String = "",
    val email: String = "",
    val uid: String = "",
    val isLoading: Boolean = false
)

@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _state = MutableStateFlow(PerfilState())
    val state: StateFlow<PerfilState> = _state

    init {
        val user = auth.currentUser
        if (user != null) {
            _state.value = PerfilState(
                nombre = user.displayName ?: "Usuario",
                email = user.email ?: "",
                uid = user.uid
            )
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
