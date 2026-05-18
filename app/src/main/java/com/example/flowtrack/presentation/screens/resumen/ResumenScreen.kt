package com.example.flowtrack.presentation.screens.resumen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.usecase.BalanceNeto
import com.example.flowtrack.domain.usecase.ResumenBanco
import com.example.flowtrack.domain.usecase.ResumenCategoria
import com.example.flowtrack.presentation.components.DonutChart
import com.example.flowtrack.presentation.components.DonutSlice
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.presentation.components.categoriaRegistry
import com.example.flowtrack.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart

@Composable
fun ResumenScreen(viewModel: ResumenViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var periodoActivo by remember { mutableStateOf(RangoFecha.ESTE_MES) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // ── Header ──────────────────────────────────────────
            val tabLabel = if (state.tabSeleccionado == 0) "Resumen por banco" else "Resumen por categoría"
            Text(
                text = tabLabel,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.md),
            )

            // ── Balance neto ─────────────────────────────────────
            if (state.isLoadingNeto) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl)
                        .padding(bottom = Spacing.md)
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Line2),
                )
            } else if (state.balanceNeto != null) {
                PatrimonioCard(
                    balanceNeto = state.balanceNeto!!,
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
        targetValue = if (active) BgCard else Color.Transparent,
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
    val bg  = if (active) Primary   else BgCard
    val fg  = if (active) Color.White else Muted
    val border = if (active) Primary else Line
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
        colors = CardDefaults.cardColors(containerColor = BgCard),
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
                colors = CardDefaults.cardColors(containerColor = BgCard),
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

// ── Patrimonio neto card ─────────────────────────────────────────────────────

@Composable
private fun PatrimonioCard(balanceNeto: BalanceNeto, modifier: Modifier = Modifier) {
    val netoPositivo = balanceNeto.neto >= java.math.BigDecimal.ZERO
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
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
                    "Patrimonio neto",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                )
                Text(
                    (if (netoPositivo) "" else "-") + formatMoney(balanceNeto.neto.abs()),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (netoPositivo) Income else Expense,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = Line2)
            Spacer(Modifier.height(Spacing.md))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Activos
                Column(modifier = Modifier.weight(1f)) {
                    Text("Activos", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(formatMoney(balanceNeto.totalActivos), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Income)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${balanceNeto.cuentasConBalance.size} cuenta${if (balanceNeto.cuentasConBalance.size == 1) "" else "s"}",
                        fontSize = 11.sp,
                        color = Muted2,
                    )
                }

                // Divisor vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(Line2)
                        .align(Alignment.CenterVertically),
                )

                // Pasivos
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Pasivos", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(formatMoney(balanceNeto.totalPasivos), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Expense)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${balanceNeto.tarjetasConDeuda.size} tarjeta${if (balanceNeto.tarjetasConDeuda.size == 1) "" else "s"}",
                        fontSize = 11.sp,
                        color = Muted2,
                    )
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
        Text(monto, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.SemiBold)
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
