package com.example.flowtrack.presentation.screens.resumen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.usecase.ResumenBanco
import com.example.flowtrack.domain.usecase.ResumenCategoria
import com.example.flowtrack.presentation.components.DonutChart
import com.example.flowtrack.presentation.components.DonutSlice
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.presentation.components.BankLogo
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.ui.theme.*
import androidx.compose.material3.MaterialTheme
import com.example.flowtrack.presentation.components.categoriaPorId

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.flowtrack.presentation.components.FiltrosSheet
import com.example.flowtrack.presentation.components.PeriodoDropdown
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.components.DesignPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenScreen(
    viewModel: ResumenViewModel = hiltViewModel(),
    onVerPorPeriodo: () -> Unit = {},
    onMenuClick: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showFiltrosSheet by remember { mutableStateOf(false) }
    val filtrosSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // ── Header ──────────────────────────────────────────
            val tabLabel = if (state.tabSeleccionado == 0) "Resumen por banco" else "Resumen por categoría"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
                    .padding(start = 4.dp, end = Spacing.md, top = Spacing.xl, bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(56.dp)) {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    text = tabLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onVerPorPeriodo) {
                    Icon(Icons.Outlined.BarChart, contentDescription = "Resumen por período", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ── Balance del período ──────────────────────────────
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl)
                        .padding(bottom = Spacing.md)
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else if (state.resumen != null) {
                BalancePeriodoCard(
                    ingresos = state.resumen!!.ingresosTotales,
                    gastos = state.resumen!!.gastosTotales,
                    balanceNeto = state.resumen!!.balanceNeto,
                    modifier = Modifier
                        .padding(horizontal = Spacing.xl)
                        .padding(bottom = Spacing.md),
                )
            }

            // ── Segmented control ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .clip(Radii.md)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
            ) {
                Row {
                    SegmentedTab(label = "Por banco",      active = state.tabSeleccionado == 0) { viewModel.setTab(0) }
                    SegmentedTab(label = "Por categoría",  active = state.tabSeleccionado == 1) { viewModel.setTab(1) }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // ── Controles de Filtro ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PeriodoDropdown(
                    state = state.periodo,
                    onPeriodoSelected = { viewModel.seleccionarPeriodo(it) },
                )
                
                val filtrosActivos = state.filtros.cantidadActivos
                val filtroLabel = if (filtrosActivos > 0) "$filtrosActivos filtro${if (filtrosActivos == 1) "" else "s"}" else "Filtros"
                DesignPill(
                    label = filtroLabel,
                    active = filtrosActivos > 0,
                    trailingIcon = true,
                    onClick = { showFiltrosSheet = true },
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            // ── Content ──────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.resumen == null) {
                EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "Sin datos",
                    description = "No hay transacciones en este período.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                if (state.tabSeleccionado == 0) {
                    BancoTab(
                        bancos = state.resumen!!.gastosPorBanco,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CategoriaTab(
                        categorias = state.resumen!!.porCategoria,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        
        if (showFiltrosSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFiltrosSheet = false },
                sheetState = filtrosSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                FiltrosSheet(
                    state = state.filtros,
                    onAplicar = { filtros ->
                        viewModel.aplicarFiltros(filtros)
                        showFiltrosSheet = false
                    },
                    onLimpiar = {
                        viewModel.limpiarFiltrosAvanzados()
                        showFiltrosSheet = false
                    },
                    onDismiss = { showFiltrosSheet = false },
                )
            }
        }
    }
}

@Composable
private fun RowScope.SegmentedTab(label: String, active: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(160),
        label = "tab_bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "tab_text",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(Radii.sm)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
private fun BancoTab(bancos: List<ResumenBanco>, modifier: Modifier = Modifier) {
    if (bancos.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.BarChart,
            title = "Sin datos",
            description = "No hay transacciones en este período.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(bancos) { b -> BancoCard(b) }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun BancoCard(b: ResumenBanco) {
    val banco = bancoPorCodigo(b.bancoCodigo)
    Card(
        shape = Radii.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, Radii.lg),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankLogo(bancoCodigo = b.bancoCodigo, size = 36.dp)
                Spacer(Modifier.width(Spacing.md))
                Text(banco.nombre, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BancoStat(label = "Gastos",   value = formatMoney(b.gastos),   color = MaterialTheme.colorScheme.error)
                BancoStat(label = "Ingresos", value = formatMoney(b.ingresos), color = ExtendedTheme.colors.success)
                BancoStat(
                    label = "Balance",
                    value = formatMoney(b.balance.abs()),
                    color = if (b.balance >= java.math.BigDecimal.ZERO) ExtendedTheme.colors.success else MaterialTheme.colorScheme.error,
                    prefix = if (b.balance < java.math.BigDecimal.ZERO) "-" else "",
                    align = Alignment.End,
                )
            }
        }
    }
}

@Composable
private fun BancoStat(
    label: String,
    value: String,
    color: Color,
    prefix: String = "",
    align: Alignment.Horizontal = Alignment.Start,
) {
    Column(horizontalAlignment = align) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$prefix$value",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            style = TabularNumber,
        )
    }
}

@Composable
private fun CategoriaTab(categorias: List<ResumenCategoria>, modifier: Modifier = Modifier) {
    if (categorias.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.BarChart,
            title = "Sin datos",
            description = "No hay transacciones en este período.",
            modifier = modifier,
        )
        return
    }

    val slices = categorias.map { c ->
        val cat = categoriaPorId(c.categoriaId)
        DonutSlice(c.total.toFloat(), cat.color, cat.nombre)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 0.dp),
    ) {
        item {
            Card(
                shape = Radii.lg,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, Radii.lg),
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DonutChart(
                            slices = slices,
                            modifier = Modifier.size(200.dp),
                            centerText = "Gastos",
                        )
                    }

                    Spacer(Modifier.height(Spacing.xl))

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        categorias.forEach { c ->
                            val cat = categoriaPorId(c.categoriaId)
                            CategoriaRow(
                                nombre = cat.nombre,
                                color  = cat.color,
                                monto  = formatMoney(c.total),
                                pct    = c.porcentaje,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun BalancePeriodoCard(
    ingresos: java.math.BigDecimal, 
    gastos: java.math.BigDecimal, 
    balanceNeto: java.math.BigDecimal,
    modifier: Modifier = Modifier
) {
    val positivo = balanceNeto >= java.math.BigDecimal.ZERO
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Balance del período",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    (if (positivo) "" else "-") + formatMoney(balanceNeto.abs()),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (positivo) ExtendedTheme.colors.success else MaterialTheme.colorScheme.error,
                    style = TabularNumber,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.md))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Ingresos
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ingresos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(formatMoney(ingresos), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ExtendedTheme.colors.success, style = TabularNumber)
                }

                // Divisor vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .align(Alignment.CenterVertically),
                )

                // Gastos
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Gastos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(formatMoney(gastos), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, style = TabularNumber)
                }
            }
        }
    }
}

@Composable
private fun CategoriaRow(nombre: String, color: Color, monto: String, pct: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(nombre, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(monto, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = TabularNumber)
        Spacer(Modifier.width(Spacing.md))
        Text(
            "${"%.1f".format(pct)}%",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp),
        )
    }
}
