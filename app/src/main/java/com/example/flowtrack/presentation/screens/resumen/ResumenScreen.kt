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
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Income
import com.example.flowtrack.ui.theme.Ink
import com.example.flowtrack.ui.theme.Line
import com.example.flowtrack.ui.theme.Line2
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Radii
import com.example.flowtrack.ui.theme.Spacing
import com.example.flowtrack.ui.theme.TabularNumber
import androidx.compose.material3.MaterialTheme
import com.example.flowtrack.presentation.components.categoriaRegistry
@Composable
fun ResumenScreen(
    viewModel: ResumenViewModel = hiltViewModel(),
    onVerPorPeriodo: () -> Unit = {},
    onMenuClick: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var periodoActivo by remember { mutableStateOf(RangoFecha.ESTE_MES) }

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
                        Icon(Icons.Outlined.Menu, contentDescription = "Menú", tint = Ink)
                    }
                }
                Text(
                    text = tabLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onVerPorPeriodo) {
                    Icon(Icons.Outlined.BarChart, contentDescription = "Resumen por período", tint = Primary)
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
                        .background(Line2),
                )
            } else if (state.resumen != null) {
                BalancePeriodoCard(
                    ingresos = state.resumen!!.ingresosTotales,
                    gastos = state.resumen!!.gastosTotales,
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
                    .background(Line2)
                    .padding(4.dp),
            ) {
                Row {
                    SegmentedTab(label = "Por banco",      active = state.tabSeleccionado == 0) { viewModel.setTab(0) }
                    SegmentedTab(label = "Por categoría",  active = state.tabSeleccionado == 1) { viewModel.setTab(1) }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // ── Period pills ─────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PeriodPill(label = "Este mes",    active = periodoActivo == RangoFecha.ESTE_MES) {
                    periodoActivo = RangoFecha.ESTE_MES
                    viewModel.setRangoPredefinido(RangoFecha.ESTE_MES)
                }
                PeriodPill(label = "Mes pasado",  active = periodoActivo == RangoFecha.MES_PASADO) {
                    periodoActivo = RangoFecha.MES_PASADO
                    viewModel.setRangoPredefinido(RangoFecha.MES_PASADO)
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // ── Content ──────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
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
                        bancos = state.resumen!!.porBanco,
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
    }
}

// ── Segmented tab button ─────────────────────────────────────────────────────

@Composable
private fun RowScope.SegmentedTab(label: String, active: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(160),
        label = "tab_bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (active) Ink else Muted,
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

// ── Period pill ──────────────────────────────────────────────────────────────

@Composable
private fun PeriodPill(label: String, active: Boolean, onClick: () -> Unit) {
    val bg  = if (active) MaterialTheme.colorScheme.primary   else MaterialTheme.colorScheme.surface
    val fg  = if (active) Color.White else Muted
    val border = if (active) MaterialTheme.colorScheme.primary else Line
    Box(
        modifier = Modifier
            .clip(Radii.pill)
            .background(bg)
            .border(1.dp, border, Radii.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

// ── Por banco tab ────────────────────────────────────────────────────────────

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
            .border(1.dp, Line2, Radii.lg),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(banco.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        banco.nombre.first().toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = banco.color,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Text(banco.nombre, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BancoStat(label = "Gastos",   value = formatMoney(b.gastos),   color = Expense)
                BancoStat(label = "Ingresos", value = formatMoney(b.ingresos), color = Income)
                BancoStat(
                    label = "Balance",
                    value = formatMoney(b.balance.abs()),
                    color = if (b.balance >= java.math.BigDecimal.ZERO) Income else Expense,
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
        Text(label, fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
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

// ── Por categoría tab ────────────────────────────────────────────────────────

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
        val cat = categoriaRegistry[c.categoriaId] ?: categoriaRegistry["sin_categorizar"]!!
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
                    .border(1.dp, Line2, Radii.lg),
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
                            val cat = categoriaRegistry[c.categoriaId] ?: categoriaRegistry["sin_categorizar"]!!
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

// ── Balance del período card ──────────────────────────────────────────────────

@Composable
private fun BalancePeriodoCard(ingresos: java.math.BigDecimal, gastos: java.math.BigDecimal, modifier: Modifier = Modifier) {
    val balance = ingresos - gastos
    val positivo = balance >= java.math.BigDecimal.ZERO
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Line2, RoundedCornerShape(16.dp)),
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
                    color = Muted,
                )
                Text(
                    (if (positivo) "" else "-") + formatMoney(balance.abs()),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (positivo) Income else Expense,
                    style = TabularNumber,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = Line2)
            Spacer(Modifier.height(Spacing.md))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Ingresos
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ingresos", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(formatMoney(ingresos), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Income, style = TabularNumber)
                }

                // Divisor vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(Line2)
                        .align(Alignment.CenterVertically),
                )

                // Gastos
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Gastos", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(formatMoney(gastos), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Expense, style = TabularNumber)
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
        Text(nombre, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(monto, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.SemiBold, style = TabularNumber)
        Spacer(Modifier.width(Spacing.md))
        Text(
            "${"%.1f".format(pct)}%",
            fontSize = 13.sp,
            color = Muted,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp),
        )
    }
}
