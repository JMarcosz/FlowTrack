package com.example.flowtrack.presentation.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.R
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.*

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier            = Modifier.padding(Spacing.xl),
        ) {
            Text(
                text      = "FlowTrack",
                style     = MaterialTheme.typography.displayLarge,
                color     = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text      = "Controla tus finanzas en un solo lugar",
                style     = MaterialTheme.typography.bodyMedium,
                color     = Muted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))

            val isLoading = uiState is LoginUiState.Loading
            Button(
                onClick  = {
                    val clientId = context.getString(R.string.default_web_client_id)
                    viewModel.signInWithGoogle(context, clientId)
                },
                enabled  = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = Radii.lg,
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color    = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Continuar con Google", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (uiState is LoginUiState.Error) {
                Text(
                    text  = (uiState as LoginUiState.Error).message,
                    color = Expense,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
