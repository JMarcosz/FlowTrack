package com.example.flowtrack.presentation.screens.revision

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Expense50
import com.example.flowtrack.ui.theme.Income
import com.example.flowtrack.ui.theme.Income50
import com.example.flowtrack.ui.theme.Ink
import com.example.flowtrack.ui.theme.Line
import com.example.flowtrack.ui.theme.Line2
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Muted2
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Primary50
import com.example.flowtrack.ui.theme.TabularNumber
import com.example.flowtrack.ui.theme.Warning
import com.example.flowtrack.ui.theme.Warning50
import com.example.flowtrack.ui.theme.Warning700
import com.example.flowtrack.ui.theme.Warning900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionScreen(
    navController: NavController,
    viewModel: RevisionViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Revisar importación", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        bottomBar = {
            if (estado is RevisionEstado.Listo) {
                val listo = estado as RevisionEstado.Listo
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Cancelar") }
                        Button(
                            onClick = { viewModel.confirmar() },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Confirmar (${listo.transacciones.size})", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    ) { padding ->
        when (val s = estado) {
            is RevisionEstado.Cargando -> LoadingContent(Modifier.padding(padding))
            is RevisionEstado.Listo -> RevisionContent(
                estado = s,
                onVerDuplicados = { navController.navigate(Screen.Duplicados.route) },
                modifier = Modifier.padding(padding),
            )
            is RevisionEstado.Confirmado -> {
                LaunchedEffect(Unit) { navController.navigate(Screen.Historial.route) { popUpTo(Screen.Upload.route) } }
            }
            is RevisionEstado.Error -> ErrorContent(s.mensaje, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Primary)
            Text("Analizando archivo...", color = Muted)
        }
    }
}

@Composable
private fun ErrorContent(mensaje: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(mensaje, color = Expense, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun RevisionContent(
    estado: RevisionEstado.Listo,
    onVerDuplicados: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Resumen de la carga
        item {
            ResumenCargaCard(estado)
        }

        // Alerta de duplicados
        if (estado.duplicados > 0) {
            item {
                DuplicadosAlertCard(duplicados = estado.duplicados, onVer = onVerDuplicados)
            }
        }

        // Advertencias del parser
        if (estado.advertencias.isNotEmpty()) {
            item {
                AdvertenciasCard(advertencias = estado.advertencias)
            }
        }

        // Header de transacciones
        item {
            Text(
                "Transacciones (${estado.transacciones.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
        }

        // Lista de transacciones
        items(estado.transacciones, key = { "${it.fecha}${it.descripcionOriginal}${it.monto}" }) { tx ->
            TransaccionRevisionRow(tx)
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ResumenCargaCard(estado: RevisionEstado.Listo) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp).background(Primary50, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Description, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                Column {
                    Text(estado.nombreArchivo, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(estado.banco, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            HorizontalDivider(color = Line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatMini("Transacciones", "${estado.transacciones.size}")
                StatMini("Débitos", formatMoney(estado.totalDebitos))
                StatMini("Créditos", formatMoney(estado.totalCreditos))
            }
            if (estado.periodo.isNotBlank()) {
                Text(estado.periodo, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = Ink, fontSize = 13.sp, style = TabularNumber)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted2, fontSize = 10.sp)
    }
}

@Composable
private fun DuplicadosAlertCard(duplicados: Int, onVer: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Warning50) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clickable(onClick = onVer),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Warning, null, tint = Warning700, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Posibles duplicados: $duplicados", fontWeight = FontWeight.SemiBold, color = Warning900, fontSize = 13.sp)
                Text("Toca para ver detalles", style = MaterialTheme.typography.bodySmall, color = Warning700)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Warning, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AdvertenciasCard(advertencias: List<String>) {
    Surface(shape = RoundedCornerShape(12.dp), color = Line2) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Advertencias del parser", fontWeight = FontWeight.SemiBold, color = Muted, fontSize = 12.sp)
            advertencias.forEach { adv -> Text("• $adv", style = MaterialTheme.typography.bodySmall, color = Muted2) }
        }
    }
}

@Composable
private fun TransaccionRevisionRow(tx: TransaccionNormalizada) {
    val esCredito = tx.tipo == TipoTransaccion.CREDITO
    val colorMonto = if (esCredito) Income else Expense
    val signo = if (esCredito) "+" else "-"

    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 0.5.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(if (esCredito) Income50 else Expense50, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (esCredito) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                    null,
                    tint = if (esCredito) Income else Expense,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(tx.descripcionCorta, fontWeight = FontWeight.Medium, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tx.fecha.toString(), style = MaterialTheme.typography.bodySmall, color = Muted2)
            }
            Text("$signo ${formatMoney(tx.monto)}", fontWeight = FontWeight.SemiBold, color = colorMonto, fontSize = 13.sp, style = TabularNumber)
        }
    }
}
