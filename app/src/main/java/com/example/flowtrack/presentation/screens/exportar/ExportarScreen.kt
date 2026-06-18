package com.example.flowtrack.presentation.screens.exportar

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.presentation.components.BankLogo
import com.example.flowtrack.presentation.components.MerchantLogo
import com.example.flowtrack.domain.usecase.FormatoExportacion
import com.example.flowtrack.domain.usecase.SeccionExportacionXlsx
import com.example.flowtrack.ui.theme.Radii
import com.example.flowtrack.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

private val FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportarScreen(
    onNavigateBack: () -> Unit,
    fromSidebar: Boolean = false,
    onDrawerReopen: () -> Unit = {},
    viewModel: ExportarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showInicioPicker by remember { mutableStateOf(false) }
    var showFinPicker by remember { mutableStateOf(false) }
    val volver = {
        onNavigateBack()
        if (fromSidebar) {
            onDrawerReopen()
        }
    }

    BackHandler(onBack = volver)

    LaunchedEffect(state.error, state.exito) {
        if (state.error != null || state.exito != null) {
            kotlinx.coroutines.delay(2500L)
            viewModel.clearMensajes()
        }
    }

    if (showInicioPicker) {
        DatePicker(
            fecha = state.fechaInicio,
            onDismiss = { showInicioPicker = false },
            onSelect = {
                viewModel.setFechaInicio(it)
                showInicioPicker = false
            },
        )
    }
    if (showFinPicker) {
        DatePicker(
            fecha = state.fechaFin,
            onDismiss = { showFinPicker = false },
            onSelect = {
                viewModel.setFechaFin(it)
                showFinPicker = false
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Exportar estados", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = volver) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = Radii.lg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Exporta con filtros por rango y origen", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Selecciona formato, fechas, cuentas y tarjetas. XLSX permite secciones adicionales.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionTitle("Formato")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FormatoExportacion.entries.forEach { formato ->
                        FilterChip(
                            selected = state.formato == formato,
                            onClick = { viewModel.setFormato(formato) },
                            label = { Text(formato.name) },
                            leadingIcon = if (state.formato == formato) {
                                { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }

            item {
                SectionTitle("Rango")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DateButton(
                        label = "Desde",
                        value = state.fechaInicio.format(FMT_FECHA),
                        onClick = { showInicioPicker = true },
                    )
                    DateButton(
                        label = "Hasta",
                        value = state.fechaFin.format(FMT_FECHA),
                        onClick = { showFinPicker = true },
                    )
                }
            }

            item {
                SectionTitle("Cuentas")
                if (state.cuentas.isEmpty()) {
                    Text("No hay cuentas activas", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    SelectionCard {
                        state.cuentas.forEachIndexed { index, cuenta ->
                            SelectionRow(
                                title = cuenta.alias.ifBlank { cuenta.numeroCuenta },
                                subtitle = cuenta.bancoCodigo,
                                selected = cuenta.id in state.cuentasSeleccionadas,
                                onToggle = { viewModel.toggleCuenta(cuenta.id) },
                                leading = { BankLogo(bancoCodigo = cuenta.bancoCodigo) },
                            )
                            if (index < state.cuentas.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            item {
                SectionTitle("Tarjetas")
                if (state.tarjetas.isEmpty()) {
                    Text("No hay tarjetas activas", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    SelectionCard {
                        state.tarjetas.forEachIndexed { index, tarjeta ->
                            SelectionRow(
                                title = tarjeta.alias.ifBlank { "${tarjeta.bancoCodigo} ****${tarjeta.ultimos4}" },
                                subtitle = tarjeta.bancoCodigo,
                                selected = tarjeta.id in state.tarjetasSeleccionadas,
                                onToggle = { viewModel.toggleTarjeta(tarjeta.id) },
                                leading = { MerchantLogo(descripcionNormalizada = tarjeta.alias.ifBlank { tarjeta.bancoCodigo }, size = 34.dp, fontSize = 12) },
                            )
                            if (index < state.tarjetas.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            if (state.formato == FormatoExportacion.XLSX) {
                item {
                    SectionTitle("Hojas XLSX")
                    SelectionCard {
                        SeccionExportacionXlsx.entries.forEachIndexed { index, seccion ->
                            SelectionRow(
                                title = when (seccion) {
                                    SeccionExportacionXlsx.RESUMEN_GENERAL -> "Resumen general"
                                    SeccionExportacionXlsx.TRANSACCIONES -> "Transacciones"
                                    SeccionExportacionXlsx.RESUMEN_POR_CATEGORIA -> "Resumen por categoria"
                                    SeccionExportacionXlsx.RESUMEN_POR_BANCO -> "Resumen por banco"
                                    SeccionExportacionXlsx.RESUMEN_POR_CUENTA -> "Resumen por cuenta"
                                    SeccionExportacionXlsx.TARJETAS_Y_CORTES -> "Tarjetas y cortes"
                                    SeccionExportacionXlsx.MOVIMIENTOS_TARJETA -> "Movimientos de tarjeta"
                                },
                                subtitle = null,
                                selected = seccion in state.seccionesXlsx,
                                onToggle = { viewModel.toggleSeccion(seccion) },
                            )
                            if (index < SeccionExportacionXlsx.entries.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.xs))
                Button(
                    onClick = { viewModel.exportar() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !state.isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (state.isExporting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Exportar", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            if (state.error != null) {
                item { StatusBanner(text = state.error!!, background = MaterialTheme.colorScheme.errorContainer, color = MaterialTheme.colorScheme.error) }
            }
            if (state.exito != null) {
                item { StatusBanner(text = state.exito!!, background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
}

@Composable
private fun SelectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = Radii.lg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SelectionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onToggle: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun RowScope.DateButton(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.outline)
            Column {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, background: Color, color: Color) {
    Surface(color = background, shape = Radii.md) {
        Text(text, modifier = Modifier.padding(Spacing.md), color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DatePicker(
    fecha: LocalDate,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth -> onSelect(LocalDate.of(year, month + 1, dayOfMonth)) },
            fecha.year,
            fecha.monthValue - 1,
            fecha.dayOfMonth,
        )
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}
