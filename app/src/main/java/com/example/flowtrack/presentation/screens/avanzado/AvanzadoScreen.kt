package com.example.flowtrack.presentation.screens.avanzado

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.ui.theme.BgScreen
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvanzadoScreen(
    navController: NavController,
    viewModel: AvanzadoViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    var mostrarDialogo by remember { mutableStateOf(false) }

    // Resetea el estado al salir para que próxima vez empiece limpio
    DisposableEffect(Unit) { onDispose { viewModel.resetearEstado() } }

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = { Text("Avanzado", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Zona de peligro
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "Zona de peligro",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Las acciones de esta sección son irreversibles. Procede con cautela.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Botón de borrar datos
            OutlinedButton(
                onClick = { mostrarDialogo = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                ),
                enabled = estado !is AvanzadoEstado.Cargando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.DeleteForever, null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Borrar todos mis datos")
            }

            // Estado de resultado
            when (val s = estado) {
                is AvanzadoEstado.Cargando -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Eliminando datos...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is AvanzadoEstado.Exito -> {
                    Text(
                        "Datos eliminados correctamente (${s.documentosEliminados} documentos).",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is AvanzadoEstado.Error -> {
                    Text(
                        s.mensaje,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {}
            }
        }
    }

    // Diálogo de confirmación
    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Borrar todos mis datos?") },
            text = {
                Text(
                    "Se eliminarán permanentemente todas tus cuentas, tarjetas, transacciones, " +
                    "movimientos, cargas e historial. Esta acción no se puede deshacer.\n\n" +
                    "Tu configuración y preferencias se conservarán.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogo = false
                        viewModel.borrarTodosMisDatos()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Sí, borrar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
