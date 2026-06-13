package com.example.flowtrack.presentation.screens.login

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun signInWithGoogle(context: Context, webClientId: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val response = fetchCredential(context, webClientId)
                exchangeForFirebaseToken(response)
            } catch (e: GetCredentialCancellationException) {
                Log.i(TAG, "El usuario canceló el selector de cuentas")
                _uiState.value = LoginUiState.Idle
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager falló. type=${e.type}", e)
                _uiState.value = LoginUiState.Error(mensajeCredentialManager(e))
            } catch (t: Throwable) {
                Log.e(TAG, "Google Sign-In falló", t)
                _uiState.value = LoginUiState.Error(
                    t.message ?: "No se pudo iniciar sesión con Google."
                )
            }
        }
    }

    fun signInWithEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = LoginUiState.Error("Por favor, completa todos los campos.")
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                auth.signInWithEmailAndPassword(email, pass).await()
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Error en login con email", e)
                _uiState.value = LoginUiState.Error(mensajeAuth(e))
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = LoginUiState.Error("Por favor, completa todos los campos.")
            return
        }
        if (pass.length < 6) {
            _uiState.value = LoginUiState.Error("La contraseña debe tener al menos 6 caracteres.")
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                auth.createUserWithEmailAndPassword(email, pass).await()
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Error en registro con email", e)
                _uiState.value = LoginUiState.Error(mensajeAuth(e))
            }
        }
    }

    private fun mensajeAuth(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("user-not-found", true) -> "No existe una cuenta con este email."
            msg.contains("wrong-password", true) -> "Contraseña incorrecta."
            msg.contains("invalid-email", true) -> "El formato del email no es válido."
            msg.contains("email-already-in-use", true) -> "Ya existe una cuenta con este email."
            msg.contains("weak-password", true) -> "La contraseña es muy débil."
            else -> "Ocurrió un error en la autenticación. Revisa tus datos e inténtalo de nuevo."
        }
    }

    private suspend fun fetchCredential(
        context: Context,
        webClientId: String,
    ): GetCredentialResponse {
        require(webClientId.isNotBlank()) {
            "Falta default_web_client_id en google-services.json."
        }

        val signIn = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signIn)
            .build()
        return CredentialManager.create(context).getCredential(context, request)
    }

    private suspend fun exchangeForFirebaseToken(response: GetCredentialResponse) {
        val credential = response.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "Credential Manager devolvió una credencial Google no compatible."
        }

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential).await()
        _uiState.value = LoginUiState.Success
    }

    private fun mensajeCredentialManager(error: GetCredentialException): String {
        return when (error) {
            is NoCredentialException ->
                "No se encontró una cuenta Google disponible en el dispositivo."
            else ->
                "Google no pudo completar el acceso. Revisa la configuración OAuth e inténtalo de nuevo."
        }
    }
}
