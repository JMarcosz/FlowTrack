package com.example.flowtrack.presentation.screens.resumen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.presentation.components.categoriaRegistry
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.presentation.components.ResumenShimmerItem
import com.example.flowtrack.ui.theme.Income
import com.example.flowtrack.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenScreen(viewModel: ResumenViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.cargarResumen() }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Filtrar por fecha")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header: Range Selector & Totals
            Surface(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        "${formatDate(state.fechaInicio)} - ${formatDate(state.fechaFin)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Gastos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                state.resumen?.gastosTotales?.let { formatMoney(it) } ?: "RD$ 0.00",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ingresos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                state.resumen?.ingresosTotales?.let { formatMoney(it) } ?: "RD$ 0.00",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Income
                            )
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = state.tabSeleccionado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(selected = state.tabSeleccionado == 0, onClick = { viewModel.setTab(0) }, text = { Text("Categorías") })
                Tab(selected = state.tabSeleccionado == 1, onClick = { viewModel.setTab(1) }, text = { Text("Bancos") })
            }

            if (state.isLoading) {
                Column(modifier = Modifier.fillMaxSize()) {
                    repeat(5) { ResumenShimmerItem() }
                }
            } else if (state.resumen != null) {
                if (state.resumen!!.porCategoria.isEmpty() && state.tabSeleccionado == 0 || state.resumen!!.porBanco.isEmpty() && state.tabSeleccionado == 1) {
                    EmptyState(
                        icon = Icons.Outlined.BarChart,
                        title = "Sin Datos",
                        description = "No hay datos para resumir en este rango de fechas.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(vertical = Spacing.md)
                ) {
                    if (state.tabSeleccionado == 0) {
                        items(state.resumen!!.porCategoria) { cat ->
                            val catInfo = categoriaRegistry[cat.categoriaId] ?: categoriaRegistry["sin_categorizar"]!!
                            ResumenItem(catInfo.nombre, cat.total, cat.porcentaje, catInfo.color)
                        }
                    } else {
                        items(state.resumen!!.porBanco) { b ->
                            ResumenItem(b.bancoCodigo, b.total, b.porcentaje, MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                }
            }
        }
    }


    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.fechaInicio.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = state.fechaFin.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val startMillis = dateRangePickerState.selectedStartDateMillis
                    val endMillis = dateRangePickerState.selectedEndDateMillis ?: startMillis
                    if (startMillis != null && endMillis != null) {
                        val start = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val end = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.setFechas(start, end)
                    }
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f),
                title = { Text("Selecciona Rango de Fechas", modifier = Modifier.padding(16.dp)) },
                headline = {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = false, onClick = { viewModel.setRangoPredefinido(RangoFecha.ESTE_MES); showDatePicker = false }, label = { Text("Este mes") })
                        FilterChip(selected = false, onClick = { viewModel.setRangoPredefinido(RangoFecha.MES_PASADO); showDatePicker = false }, label = { Text("Mes pasado") })
                    }
                }
            )
        }
    }
}

@Composable
fun ResumenItem(label: String, total: java.math.BigDecimal, porcentaje: Float, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(Spacing.sm))
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(formatMoney(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = (porcentaje / 100f).coerceIn(0f, 1f),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Text("${"%.1f".format(porcentaje)}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
        }
    }
}
