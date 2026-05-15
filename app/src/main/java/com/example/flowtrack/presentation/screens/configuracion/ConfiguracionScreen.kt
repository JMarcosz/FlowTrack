package com.example.flowtrack.presentation.screens.configuracion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Money
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    navController: NavController,
    viewModel: ConfiguracionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.error, state.exito) {
        if (state.error != null || state.exito != null) {
            // Ideally use SnackbarHostState, handled via Scaffold in a real app
            kotlinx.coroutines.delay(3000)
            viewModel.clearMensajes()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            
            // Mensajes (Feedback)
            if (state.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, modifier = Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (state.exito != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(state.exito!!, modifier = Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Text("Preferencias", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.primary)
            
            // Tema Oscuro
            ConfigSwitchRow(
                icon = Icons.Default.DarkMode,
                title = "Modo Oscuro",
                subtitle = "Cambiar a tema oscuro",
                checked = state.config.temaOscuro,
                onCheckedChange = { viewModel.toggleTema(it) }
            )
            
            Divider()
            
            // Moneda Predeterminada
            var showCurrencyMenu by remember { mutableStateOf(false) }
            ConfigActionRow(
                icon = Icons.Default.Money,
                title = "Moneda Base",
                subtitle = state.config.monedaPredeterminada.name,
                onClick = { showCurrencyMenu = true }
            )
            
            if (showCurrencyMenu) {
                AlertDialog(
                    onDismissRequest = { showCurrencyMenu = false },
                    title = { Text("Seleccionar Moneda") },
                    text = {
                        Column {
                            Moneda.values().forEach { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            viewModel.setMonedaBase(m)
                                            showCurrencyMenu = false
                                        }
                                        .padding(Spacing.md)
                                ) {
                                    RadioButton(selected = state.config.monedaPredeterminada == m, onClick = null)
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(m.name)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCurrencyMenu = false }) { Text("Cancelar") }
                    }
                )
            }

            Divider()

            // Exportación
            Text("Gestión de Datos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.primary)
            
            ConfigActionRow(
                icon = Icons.Default.Download,
                title = "Exportar Transacciones a CSV",
                subtitle = "Descarga tus transacciones de los últimos 6 meses",
                onClick = { viewModel.exportarDatosCsv() }
            )

            if (state.isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Divider()
            
            // Gestión de categorías
            ConfigActionRow(
                icon = Icons.Default.Language, // Placeholder icon
                title = "Administrar Categorías",
                subtitle = "Ver y editar tus categorías personales",
                onClick = { navController.navigate("categorias") }
            )
        }
    }
}

@Composable
fun ConfigSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ConfigActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
