package com.example.flowtrack.presentation.screens.notificaciones

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.example.flowtrack.presentation.components.FinanzasSwitch
import com.example.flowtrack.ui.theme.BgScreen
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificacionesScreen(navController: NavController) {
    var resumenSemanal by remember { mutableStateOf(true) }
    var alertasSaldo by remember { mutableStateOf(true) }
    var recordatoriosPago by remember { mutableStateOf(false) }
    var infoImportacion by remember { mutableStateOf(true) }

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
            Text(
                "Actividad",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            NotifRow(
                titulo = "Resumen semanal",
                subtitulo = "Recibe un resumen de gastos cada lunes",
                checked = resumenSemanal,
                onCheckedChange = { resumenSemanal = it },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            NotifRow(
                titulo = "Alertas de saldo bajo",
                subtitulo = "Notificar cuando el balance baja de un umbral",
                checked = alertasSaldo,
                onCheckedChange = { alertasSaldo = it },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            Text(
                "Tarjetas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            NotifRow(
                titulo = "Recordatorios de pago",
                subtitulo = "Aviso 3 días antes del límite de pago de tarjeta",
                checked = recordatoriosPago,
                onCheckedChange = { recordatoriosPago = it },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            Text(
                "Sistema",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            NotifRow(
                titulo = "Resultado de importaciones",
                subtitulo = "Notificar al terminar de procesar un archivo",
                checked = infoImportacion,
                onCheckedChange = { infoImportacion = it },
            )
        }
    }
}

@Composable
private fun NotifRow(
    titulo: String,
    subtitulo: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        FinanzasSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
