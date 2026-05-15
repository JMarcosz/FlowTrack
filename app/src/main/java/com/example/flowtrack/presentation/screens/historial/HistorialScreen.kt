package com.example.flowtrack.presentation.screens.historial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.presentation.navigation.Screen
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val ZONA = ZoneId.of("America/Santo_Domingo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    navController: NavController,
    viewModel: HistorialViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()

    Scaffold(
        containerColor = Color(0xFFF4F6FA),
        topBar = {
            TopAppBar(
                title = { Text("Historial de importaciones", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F6FA)),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Upload.route) },
                containerColor = Color(0xFF2F6FED),
                contentColor = Color.White,
            ) { Icon(Icons.Outlined.Add, "Importar nuevo") }
        }
    ) { padding ->
        when (val s = estado) {
            is HistorialEstado.Cargando -> LoadingHistorial(Modifier.padding(padding))
            is HistorialEstado.Vacio -> EmptyHistorial(navController, Modifier.padding(padding))
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
        CircularProgressIndicator(color = Color(0xFF2F6FED))
    }
}

@Composable
private fun EmptyHistorial(navController: NavController, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Inbox, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(56.dp))
            Text("Sin importaciones aún", fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
            Text("Importa tu primer estado de cuenta para empezar", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
            Button(
                onClick = { navController.navigate(Screen.Upload.route) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Importar ahora") }
        }
    }
}

@Composable
private fun ErrorHistorial(mensaje: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(mensaje, color = Color(0xFFDC2626), textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
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
                color = Color(0xFF94A3B8),
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
                    Text("Eliminar", color = Color(0xFFDC2626))
                }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") } },
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Cabecera clickeable
            Row(
                Modifier.fillMaxWidth().clickable { expandido = !expandido }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Icono banco
                Box(
                    Modifier.size(40.dp).background(bancoCcolor(carga.bancoCodigo).copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        carga.bancoCodigo.take(2),
                        color = bancoCcolor(carga.bancoCodigo),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(carga.nombreArchivo, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        carga.procesadoEn.atZone(ZONA).format(FMT_FECHA),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                }

                EstadoBadge(carga.estado)
                Icon(
                    if (expandido) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Detalle expandible
            AnimatedVisibility(visible = expandido, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoMini("Insertadas", "${carga.transaccionesInsertadas}")
                        InfoMini("Duplicadas", "${carga.transaccionesDuplicadas}")
                        InfoMini("Parser v${carga.parserVersion}", carga.tipoDocumento.name)
                        InfoMini("Confianza", "${(carga.confianzaDeteccion * 100).toInt()}%")
                    }

                    if (carga.advertencias.isNotEmpty()) {
                        Surface(color = Color(0xFFFFF7ED), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                carga.advertencias.forEach { adv ->
                                    Text("• $adv", style = MaterialTheme.typography.bodySmall, color = Color(0xFF92400E))
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { confirmarEliminar = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626).copy(alpha = 0.4f))
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

@Composable
private fun EstadoBadge(estado: EstadoCarga) {
    val (bgColor, textColor, texto) = when (estado) {
        EstadoCarga.EXITOSO -> Triple(Color(0xFFE7F7EC), Color(0xFF16A34A), "Exitoso")
        EstadoCarga.PARCIAL -> Triple(Color(0xFFFFF7ED), Color(0xFFF59E0B), "Parcial")
        EstadoCarga.FALLIDO -> Triple(Color(0xFFFDECEC), Color(0xFFDC2626), "Fallido")
    }
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(texto, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun InfoMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
        Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8))
    }
}

private fun bancoCcolor(codigo: String) = when (codigo) {
    "BANRESERVAS" -> Color(0xFF0F5DAB)
    "POPULAR"     -> Color(0xFF005DA4)
    "QIK"         -> Color(0xFFE6A800)
    "CIBAO"       -> Color(0xFFE30613)
    else          -> Color(0xFF64748B)
}
