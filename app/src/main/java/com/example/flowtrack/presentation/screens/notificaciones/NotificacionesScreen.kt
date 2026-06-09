package com.example.flowtrack.presentation.screens.notificaciones

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.notifications.NotificationHelper
import com.example.flowtrack.presentation.components.FinanzasSwitch
import com.example.flowtrack.ui.theme.BgScreen
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificacionesScreen(
    navController: NavController,
    viewModel: NotificacionesViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    val context = LocalContext.current

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* el usuario decidió; los toggles ya están persistidos igual */ }

    fun pedirPermisoSiHaceFalta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.puedeNotificar(context)
        ) {
            permisoLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones", fontWeight = FontWeight.SemiBold) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            NotifRow(
                "Activar notificaciones",
                "Recordatorios, resúmenes y alertas",
                config.activa,
            ) { v -> viewModel.setActiva(v); if (v) pedirPermisoSiHaceFalta() }

            if (config.activa) {
                Seccion("Recordatorios de pago de tarjeta")
                NotifRow("7 días antes", "Aviso una semana antes del pago", config.pago7dias) { viewModel.setPago7(it) }
                Divisor()
                NotifRow("3 días antes", null, config.pago3dias) { viewModel.setPago3(it) }
                Divisor()
                NotifRow("1 día antes", null, config.pago1dia) { viewModel.setPago1(it) }
                Divisor()
                NotifRow("El día del pago", null, config.pagoMismoDia) { viewModel.setPagoMismoDia(it) }

                Seccion("Resúmenes")
                NotifRow("Resumen mensual", "Recibe el resumen del mes el día 1", config.resumenMensual) {
                    viewModel.setResumenMensual(it)
                }

                Seccion("Alertas")
                NotifRow("Gastos altos", "Avisar cuando un gasto supera tu umbral", config.alertasGastosAltos) {
                    viewModel.setAlertasGastosAltos(it)
                }

                Spacer(Modifier.height(Spacing.lg))
                OutlinedButton(
                    onClick = { pedirPermisoSiHaceFalta(); viewModel.probarNotificacion() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                ) {
                    Icon(Icons.Outlined.NotificationsActive, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Probar recordatorios ahora")
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

@Composable
private fun Divisor() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
}

@Composable
private fun NotifRow(
    titulo: String,
    subtitulo: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        FinanzasSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
