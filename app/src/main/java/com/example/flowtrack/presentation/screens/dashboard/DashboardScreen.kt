package com.example.flowtrack.presentation.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.Spacing
import com.example.flowtrack.ui.theme.TextSecondary

@Composable
fun DashboardScreen(navController: NavController? = null) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Inicio", style = MaterialTheme.typography.titleLarge)
            Text(
                "Importa tu primer estado de cuenta",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { navController?.navigate(Screen.Upload.route) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.CloudUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importar estado de cuenta", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
