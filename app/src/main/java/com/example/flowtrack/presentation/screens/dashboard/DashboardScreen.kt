package com.example.flowtrack.presentation.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.usecase.DatosBancoResumen
import com.example.flowtrack.domain.usecase.ResumenDashboard
import com.example.flowtrack.presentation.components.BancoUI
import com.example.flowtrack.presentation.components.DonutChart
import com.example.flowtrack.presentation.components.DonutSlice
import com.example.flowtrack.presentation.components.ErrorState
import com.example.flowtrack.presentation.components.Sparkline
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.presentation.components.categoriaPorId
import com.example.flowtrack.presentation.components.shimmerEffect
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Expense50
import com.example.flowtrack.ui.theme.Income
import com.example.flowtrack.ui.theme.Income50
import com.example.flowtrack.ui.theme.Line
import com.example.flowtrack.ui.theme.Line2
import com.example.flowtrack.ui.theme.Success
import com.example.flowtrack.ui.theme.TabularNumber
import com.example.flowtrack.ui.theme.TextBody
import java.math.BigDecimal

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    navController: NavController? = null,
    onMenuClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val estado  by viewModel.estado.collectAsState()
    val periodo by viewModel.periodo.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val st = estado) {
            is DashboardEstado.Cargando -> LoadingContent()
            is DashboardEstado.Error    -> ErrorState(
                mensaje  = st.mensaje,
                onRetry  = { viewModel.cargarDatos() },
                modifier = Modifier.align(Alignment.Center),
            )
            is DashboardEstado.Exito    -> DashboardContent(
                estado        = st,
                periodo       = periodo,
                onPeriodo     = { viewModel.seleccionarPeriodo(it) },
                navController = navController,
                onMenuClick   = onMenuClick,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading skeleton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(52.dp))
        Box(modifier = Modifier.fillMaxWidth(0.55f).height(28.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
            Box(modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
        }
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardContent(
    estado: DashboardEstado.Exito,
    periodo: String,
    onPeriodo: (String) -> Unit,
    navController: NavController?,
    onMenuClick: () -> Unit,
) {
    val resumen = estado.resumen

    // Sparkline data — Float solo para renderizado, cálculo queda en BigDecimal en el UseCase
    val trendGasto   = remember(resumen.serie) { resumen.serie.map { it.gasto.toFloat() } }
    val trendIngreso = remember(resumen.serie) { resumen.serie.map { it.ingreso.toFloat() } }
    val trendBalance = remember(resumen.serie) { resumen.serie.map { it.balanceAcumulado.toFloat() } }

    // DonutChart slices desde el breakdown de categorías del UseCase
    val slices = remember(resumen.gastosPorCategoria) {
        resumen.gastosPorCategoria.map { dc ->
            val cat = categoriaPorId(dc.categoriaId)
            DonutSlice(dc.monto.toFloat(), cat.color, cat.nombre)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // ── TopBar ────────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets(0, 0, 0, 0)) // No duplicar insets si NavHost ya los tiene
                    .padding(horizontal = 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {

                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "Dashboard",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { navController?.navigate(Screen.Notificaciones.route) }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ── Saludo + selector de período ─────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hola, ${estado.nombreUsuario}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Aquí está tu resumen financiero",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                PeriodoDropdown(
                    selected = periodo,
                    options  = PERIODOS_DASHBOARD,
                    onSelect = onPeriodo,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ── Stat cards ────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DashStatCard(
                    label               = "Gastos totales",
                    value               = formatMoney(resumen.gastoTotal),
                    color               = Expense,
                    bgColor             = Expense50,
                    isExpense           = true,
                    porcentaje          = resumen.comparacion.expenseChangePercentage,
                    esIncremento        = resumen.comparacion.expenseIsIncrement,
                    comparisonAvailable = resumen.comparacion.comparisonAvailable,
                    coverageWarning     = resumen.comparacion.coverageWarning,
                    deltaInverse        = true,
                    trendData           = trendGasto,
                    modifier            = Modifier.weight(1f),
                )
                DashStatCard(
                    label               = "Ingresos totales",
                    value               = formatMoney(resumen.ingresoTotal),
                    color               = Income,
                    bgColor             = Income50,
                    isExpense           = false,
                    porcentaje          = resumen.comparacion.incomeChangePercentage,
                    esIncremento        = resumen.comparacion.incomeIsIncrement,
                    comparisonAvailable = resumen.comparacion.comparisonAvailable,
                    coverageWarning     = resumen.comparacion.coverageWarning,
                    trendData           = trendIngreso,
                    smooth              = true,
                    modifier            = Modifier.weight(1f),
                )
            }
        }

        // ── Aviso de cobertura insuficiente ───────────────────────────────────
        if (resumen.comparacion.coverageWarning) {
            item { CoverageWarningBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        }

        // ── Balance neto ─────────────────────────────────────────────────────
        item {
            BalanceNetoCard(
                resumen  = resumen,
                trendData = trendBalance,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        // ── Gastos por categoría ─────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Gastos por categoría",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp, color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = { navController?.navigate(Screen.Resumen.route) },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            "Ver detalle",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.Outlined.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Card(
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border    = BorderStroke(1.dp, Line2),
                    modifier  = Modifier.fillMaxWidth(),
                ) {
                    if (slices.isEmpty()) {
                        Text(
                            "Sin gastos en este período",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            DonutChart(
                                slices      = slices,
                                modifier    = Modifier.size(140.dp),
                                strokeWidth = 28f,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                resumen.gastosPorCategoria.forEachIndexed { i, dc ->
                                    val slice = slices[i]
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(slice.color),
                                        )
                                        Text(
                                            slice.label,
                                            fontSize = 13.sp, color = TextBody,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            formatMoney(dc.monto),
                                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = TabularNumber,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Por banco ────────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                Text(
                    "Por banco",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (resumen.gastosPorBanco.isEmpty()) {
                    Text("Sin actividad bancaria en este período", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Card(
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(0.dp),
                        border    = BorderStroke(1.dp, Line2),
                        modifier  = Modifier.fillMaxWidth(),
                    ) {
                        resumen.gastosPorBanco.forEachIndexed { i, datos ->
                            BancoRow(banco = bancoPorCodigo(datos.bancoCodigo), datos = datos)
                            if (i < resumen.gastosPorBanco.size - 1) {
                                HorizontalDivider(
                                    modifier  = Modifier.padding(horizontal = 16.dp),
                                    color     = Line2,
                                    thickness = 1.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashStatCard(
    label: String,
    value: String,
    color: Color,
    bgColor: Color,
    isExpense: Boolean,
    porcentaje: BigDecimal?,
    esIncremento: Boolean,
    comparisonAvailable: Boolean,
    coverageWarning: Boolean,
    deltaInverse: Boolean = false,
    trendData: List<Float> = emptyList(),
    smooth: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation    = 4.dp,
                shape        = RoundedCornerShape(18.dp),
                ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                spotColor    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            )
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = label,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isExpense) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        tint     = color,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // Valor
            Text(
                text          = value,
                fontSize      = 19.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color         = MaterialTheme.colorScheme.onSurface,
                maxLines      = 1,
                overflow      = TextOverflow.Ellipsis,
                style        = TabularNumber,
            )

            // Footer: delta % + sparkline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeltaBadge(
                    porcentaje          = porcentaje,
                    esIncremento        = esIncremento,
                    comparisonAvailable = comparisonAvailable,
                    coverageWarning     = coverageWarning,
                    inverse             = deltaInverse,
                )
                if (trendData.size >= 2) {
                    Sparkline(
                        data        = trendData,
                        color       = color,
                        modifier    = Modifier.size(72.dp, 22.dp),
                        strokeWidth = 1.6.dp,
                        smooth      = smooth,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Balance neto card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BalanceNetoCard(
    resumen: ResumenDashboard,
    trendData: List<Float>,
    modifier: Modifier = Modifier,
) {
    val delta   = resumen.deltaBalance
    val color   = if (delta.esIncremento) Income else Expense
    val bgColor = if (delta.esIncremento) Income50 else Expense50

    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border    = BorderStroke(1.dp, Line2),
        modifier  = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.wrapContentWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text("Balance neto", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (delta.esIncremento) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            tint     = color,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    text          = formatMoney(resumen.balanceNeto),
                    fontSize      = 22.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    color         = MaterialTheme.colorScheme.onSurface,
                    style         = TabularNumber,
                )
                Spacer(modifier = Modifier.height(4.dp))
                DeltaBadge(
                    porcentaje          = delta.porcentaje,
                    esIncremento        = delta.esIncremento,
                    comparisonAvailable = true,
                    coverageWarning     = false,
                    inverse             = false,
                )
            }

            if (trendData.size >= 2) {
                Sparkline(
                    data        = trendData,
                    color       = color,
                    modifier    = Modifier
                        .weight(1f)
                        .height(48.dp),
                    strokeWidth = 2.dp,
                    area        = true,
                    smooth      = true,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DeltaBadge — muestra % con flecha, "—" o icono de advertencia de cobertura
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeltaBadge(
    porcentaje: BigDecimal?,
    esIncremento: Boolean,
    comparisonAvailable: Boolean,
    coverageWarning: Boolean,
    inverse: Boolean,
) {
    if (!comparisonAvailable) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (coverageWarning) {
                Icon(
                    Icons.Outlined.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(11.dp),
                )
            }
            Text("—", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (porcentaje == null) {
        Text("—", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val good   = if (inverse) !esIncremento else esIncremento
    val dColor = if (good) Success else Expense
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MiniArrow(up = esIncremento, color = dColor)
        Text(
            "${"%.1f".format(porcentaje.toFloat())}%",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = dColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Banner de cobertura insuficiente
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CoverageWarningBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Warning, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
        )
        Text(
            "Sin datos suficientes del período anterior para calcular la variación",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fila de banco
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BancoRow(banco: BancoUI, datos: DatosBancoResumen) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(banco.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                banco.abbr,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = banco.fgColor,
            )
        }
        Text(
            banco.nombre,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (datos.gastos > BigDecimal.ZERO) {
                Text(
                    "- ${formatMoney(datos.gastos)}",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Expense,
                    style = TabularNumber,
                )
            }
            if (datos.ingresos > BigDecimal.ZERO) {
                Text(
                    "+ ${formatMoney(datos.ingresos)}",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Income,
                    style = TabularNumber,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Período dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeriodoDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(1.dp, Line),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Text(selected, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                opt, fontSize = 14.sp,
                                fontWeight = if (opt == selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (opt == selected) {
                                Icon(
                                    Icons.Outlined.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini flecha para deltas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniArrow(up: Boolean, color: Color, size: Dp = 10.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width / 10f
        val path = Path().apply {
            if (up) {
                moveTo(2f * s, 7f * s); lineTo(5f * s, 3.5f * s); lineTo(8f * s, 7f * s)
            } else {
                moveTo(2f * s, 3.5f * s); lineTo(5f * s, 7f * s); lineTo(8f * s, 3.5f * s)
            }
        }
        drawPath(
            path  = path,
            color = color,
            style = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
