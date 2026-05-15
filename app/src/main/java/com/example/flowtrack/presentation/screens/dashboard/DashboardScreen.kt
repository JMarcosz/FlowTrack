package com.example.flowtrack.presentation.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.flowtrack.ui.theme.Spacing
import com.example.flowtrack.ui.theme.TextSecondary

@Composable
fun DashboardScreen() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Inicio", style = MaterialTheme.typography.titleLarge)
            Text("Importa tu primer estado de cuenta", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
