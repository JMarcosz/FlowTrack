package com.example.flowtrack.presentation.screens.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle    : LoginUiState
    object Loading : LoginUiState
    object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun signInWithGoogle(context: Context, webClientId: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val response = fetchCredential(context, webClientId)
                exchangeForFirebaseToken(response)
            } catch (e: GetCredentialCancellationException) {
                _uiState.value = LoginUiState.Idle
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    // Intenta OneTap primero; si no hay credenciales guardadas usa el selector completo.
    private suspend fun fetchCredential(context: Context, webClientId: String): GetCredentialResponse {
        val manager = CredentialManager.create(context)
        return try {
            val oneTap = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
            manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(oneTap).build())
        } catch (e: NoCredentialException) {
            // Fallback: selector completo de cuentas Google
            val signIn = GetSignInWithGoogleOption.Builder(webClientId).build()
            manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(signIn).build())
        }
    }

    private fun exchangeForFirebaseToken(response: GetCredentialResponse) {
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCred).addOnCompleteListener { task ->
            _uiState.value = if (task.isSuccessful) LoginUiState.Success
            else LoginUiState.Error(task.exception?.message ?: "Error de autenticación")
        }
    }
}
