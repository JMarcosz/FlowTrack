package com.example.flowtrack.presentation.screens.historial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.presentation.components.BankLogo
import com.example.flowtrack.ui.theme.ExtendedTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val ZONA = ZoneId.of("America/Santo_Domingo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onDrawerReopen: () -> Unit = {},
    fromSidebar: Boolean = false,
    viewModel: HistorialViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var confirmarEliminarTodo by remember { mutableStateOf(false) }
    val volver = {
        onNavigateBack()
        if (fromSidebar) {
            onDrawerReopen()
        }
    }

    BackHandler(onBack = volver)

    if (confirmarEliminarTodo) {
        AlertDialog(
            onDismissRequest = { confirmarEliminarTodo = false },
            title = { Text("Eliminar todo") },
            text = { Text("Se eliminarán todas las transacciones, movimientos y estados de tarjeta de todas las importaciones. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.eliminarTodo(); confirmarEliminarTodo = false }) {
                    Text("Eliminar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminarTodo = false }) { Text("Cancelar") } },
        )
    }

    // Mostrar error como Snackbar si el estado es Error (sin perder la lista anterior)
    LaunchedEffect(estado) {
        if (estado is HistorialEstado.Error) {
            snackbarHostState.showSnackbar((estado as HistorialEstado.Error).mensaje)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Historial de importaciones", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
            IconButton(onClick = volver) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
            }
                },
                actions = {
            if (estado is HistorialEstado.ConDatos) {
                IconButton(onClick = { confirmarEliminarTodo = true }) {
                    Icon(Icons.Outlined.DeleteSweep, "Eliminar todo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToUpload,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "Importar nuevo") }
        }
    ) { padding ->
        when (val s = estado) {
            is HistorialEstado.Cargando -> LoadingHistorial(Modifier.padding(padding))
            is HistorialEstado.Vacio -> EmptyHistorial(onNavigateToUpload, Modifier.padding(padding))
            is HistorialEstado.ConDatos -> HistorialContent(
                cargas = s.cargas,
                onEliminar = { cargaId -> viewModel.eliminar(cargaId) },
                modifier = Modifier.padding(padding),
            )
            is HistorialEstado.Error -> ErrorHistorial(s.mensaje, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LoadingHistorial(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyHistorial(onNavigateToUpload: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
            Text("Sin importaciones aún", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Importa tu primer estado de cuenta para empezar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(
                onClick = onNavigateToUpload,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Importar ahora", color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@Composable
private fun ErrorHistorial(mensaje: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(mensaje, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun HistorialContent(
    cargas: List<Carga>,
    onEliminar: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "${cargas.size} importación(es)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(cargas, key = { it.id }) { carga ->
            CargaCard(carga = carga, onEliminar = { onEliminar(carga.id) })
        }
        item { Spacer(Modifier.height(80.dp)) } // margen para FAB
    }
}

@Composable
private fun CargaCard(carga: Carga, onEliminar: () -> Unit) {
    var expandido by remember { mutableStateOf(false) }
    var confirmarEliminar by remember { mutableStateOf(false) }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            title = { Text("Eliminar importación") },
            text = { Text("Se eliminarán ${carga.transaccionesInsertadas} transacción(es) importadas con '${carga.nombreArchivo}'.") },
            confirmButton = {
                TextButton(onClick = { onEliminar(); confirmarEliminar = false }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") } },
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Cabecera clickeable
            Row(
                Modifier.fillMaxWidth().clickable { expandido = !expandido }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BankLogo(bancoCodigo = carga.bancoCodigo)

                Column(Modifier.weight(1f)) {
                    Text(carga.nombreArchivo, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        carga.procesadoEn.atZone(ZONA).format(FMT_FECHA),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                EstadoBadge(carga.estado)
                Icon(
                    if (expandido) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Detalle expandible
            AnimatedVisibility(visible = expandido, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoMini("Insertadas", "${carga.transaccionesInsertadas}")
                        InfoMini("Duplicadas", "${carga.transaccionesDuplicadas}")
                        InfoMini("Parser v${carga.parserVersion}", carga.tipoDocumento.name)
                    }

                    if (carga.advertencias.isNotEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                carga.advertencias.forEach { adv ->
                                    Text("• $adv", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                        }
                    }

                    if (carga.estado != EstadoCarga.ELIMINADO) {
                        OutlinedButton(
                            onClick = { confirmarEliminar = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Eliminar carga", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstadoBadge(estado: EstadoCarga) {
    val (bgColor, textColor, texto) = when (estado) {
        EstadoCarga.EXITOSO  -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Exitoso")
        EstadoCarga.PARCIAL  -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "Parcial")
        EstadoCarga.FALLIDO  -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, "Fallido")
        EstadoCarga.ELIMINADO -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Eliminado")
    }
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(texto, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun InfoMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
