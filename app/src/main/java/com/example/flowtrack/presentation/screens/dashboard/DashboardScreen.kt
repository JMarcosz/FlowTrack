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
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.R
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.presentation.components.BankLogo
import com.example.flowtrack.presentation.components.DonutChart
import com.example.flowtrack.presentation.components.DonutSlice
import com.example.flowtrack.presentation.components.ErrorState
import com.example.flowtrack.presentation.components.PeriodoDropdown
import com.example.flowtrack.presentation.components.Sparkline
import com.example.flowtrack.presentation.components.shimmerEffect
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.model.PeriodoState
import com.example.flowtrack.ui.theme.ExtendedTheme
import com.example.flowtrack.ui.theme.Spacing
import com.example.flowtrack.ui.theme.TabularNumber
import java.math.BigDecimal
import kotlin.text.toBigDecimalOrNull

@Composable
fun DashboardScreen(
    onMenuClick: () -> Unit = {},
    onNavigateToUpload: () -> Unit = {},
    onNavigateToNotificaciones: () -> Unit = {},
    onNavigateToResumen: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val periodo by viewModel.periodo.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val current = estado) {
            is DashboardEstado.Cargando -> LoadingContent()
            is DashboardEstado.Error -> ErrorState(
                mensaje = current.mensaje,
                onRetry = viewModel::cargarDatos,
                modifier = Modifier.align(Alignment.Center),
            )
            is DashboardEstado.Vacio -> DashboardEmptyState(
                onImportarClick = onNavigateToUpload,
                modifier = Modifier.align(Alignment.Center),
            )
            is DashboardEstado.Exito -> DashboardContent(
                estado = current,
                periodo = periodo,
                onPeriodo = viewModel::seleccionarPeriodo,
                onMenuClick = onMenuClick,
                onNavigateToNotificaciones = onNavigateToNotificaciones,
                onNavigateToResumen = onNavigateToResumen,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(52.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmerEffect(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .shimmerEffect(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .shimmerEffect(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .shimmerEffect(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .shimmerEffect(),
        )
    }
}

@Composable
private fun DashboardContent(
    estado: DashboardEstado.Exito,
    periodo: PeriodoState,
    onPeriodo: (FiltroPeriodo) -> Unit,
    onMenuClick: () -> Unit,
    onNavigateToNotificaciones: () -> Unit,
    onNavigateToResumen: () -> Unit,
) {
    val uiState = estado.data
    val trendGasto = uiState.grafica.gastos
    val trendIngreso = uiState.grafica.ingresos
    val trendBalance = uiState.grafica.balances
    val slices = uiState.grafica.donutSlices

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
                    .padding(horizontal = 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.cd_menu),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.dashboard_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onNavigateToNotificaciones) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.cd_notifications),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

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
                        text = stringResource(R.string.dashboard_greeting, uiState.nombreUsuario),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_subtitle),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                PeriodoDropdown(
                    state = periodo,
                    onPeriodoSelected = onPeriodo,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DashStatCard(
                    label = stringResource(R.string.dashboard_expenses_total),
                    value = uiState.totalGastos,
                    color = MaterialTheme.colorScheme.error,
                    bgColor = MaterialTheme.colorScheme.errorContainer,
                    isExpense = true,
                    porcentaje = uiState.comparacion.expenseChangePercentage?.toBigDecimalOrNull(),
                    esIncremento = uiState.comparacion.expenseIsIncrement,
                    comparisonAvailable = uiState.comparacion.comparisonAvailable,
                    coverageWarning = uiState.comparacion.coverageWarning,
                    deltaInverse = true,
                    trendData = trendGasto,
                    modifier = Modifier.weight(1f),
                )
                DashStatCard(
                    label = stringResource(R.string.dashboard_income_total),
                    value = uiState.totalIngresos,
                    color = ExtendedTheme.colors.success,
                    bgColor = ExtendedTheme.colors.successContainer,
                    isExpense = false,
                    porcentaje = uiState.comparacion.incomeChangePercentage?.toBigDecimalOrNull(),
                    esIncremento = uiState.comparacion.incomeIsIncrement,
                    comparisonAvailable = uiState.comparacion.comparisonAvailable,
                    coverageWarning = uiState.comparacion.coverageWarning,
                    trendData = trendIngreso,
                    smooth = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (uiState.coverageWarning) {
            item {
                CoverageWarningBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        item {
            BalanceNetoCard(
                balanceNeto = uiState.balanceNeto,
                delta = uiState.deltaBalance,
                trendData = trendBalance,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

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
                        text = stringResource(R.string.dashboard_expenses_by_category),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = onNavigateToResumen,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_view_detail),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (slices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dashboard_no_expenses_period),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                slices = slices,
                                modifier = Modifier.size(140.dp),
                                strokeWidth = 28f,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.categorias.forEachIndexed { index, categoria ->
                                    val slice = slices.getOrNull(index)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(slice?.color ?: categoria.color),
                                        )
                                        Text(
                                            text = categoria.nombre,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = categoria.montoTexto,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
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

        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_by_bank),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (uiState.bancos.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_no_bank_activity),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        uiState.bancos.forEachIndexed { index, banco ->
                            BancoRow(banco = banco)
                            if (index < uiState.bancos.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
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
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            )
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        tint = color,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TabularNumber,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeltaBadge(
                    porcentaje = porcentaje,
                    esIncremento = esIncremento,
                    comparisonAvailable = comparisonAvailable,
                    coverageWarning = coverageWarning,
                    inverse = deltaInverse,
                )
                if (trendData.size >= 2) {
                    Sparkline(
                        data = trendData,
                        color = color,
                        modifier = Modifier.size(72.dp, 22.dp),
                        strokeWidth = 1.6.dp,
                        smooth = smooth,
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceNetoCard(
    balanceNeto: String,
    delta: DashboardDeltaUiModel,
    trendData: List<Float>,
    modifier: Modifier = Modifier,
) {
    val color = if (delta.isIncrement) ExtendedTheme.colors.success else MaterialTheme.colorScheme.error
    val bgColor = if (delta.isIncrement) ExtendedTheme.colors.successContainer else MaterialTheme.colorScheme.errorContainer

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
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
                    Text(
                        text = stringResource(R.string.dashboard_balance_net),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (delta.isIncrement) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    text = balanceNeto,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TabularNumber,
                )
                Spacer(modifier = Modifier.height(4.dp))
                DeltaBadge(
                    porcentaje = delta.changePercentage?.toBigDecimalOrNull(),
                    esIncremento = delta.isIncrement,
                    comparisonAvailable = delta.comparisonAvailable,
                    coverageWarning = delta.coverageWarning,
                    inverse = false,
                )
            }

            if (trendData.size >= 2) {
                Sparkline(
                    data = trendData,
                    color = color,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    strokeWidth = 2.dp,
                    area = true,
                    smooth = true,
                )
            }
        }
    }
}

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
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(11.dp),
                )
            }
            Text(
                text = stringResource(R.string.dashboard_no_variation),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (porcentaje == null) {
        Text(
            text = stringResource(R.string.dashboard_no_variation),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val good = if (inverse) !esIncremento else esIncremento
    val dColor = if (good) ExtendedTheme.colors.success else MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MiniArrow(up = esIncremento, color = dColor)
        Text(
            text = "${"%.1f".format(porcentaje.toFloat())}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = dColor,
        )
    }
}

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
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.dashboard_coverage_warning),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BancoRow(banco: BancoResumenUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BankLogo(bancoCodigo = banco.codigo, size = 34.dp)
        Text(
            text = banco.nombre,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (banco.gastosTexto.isNotBlank()) {
                Text(
                    text = banco.gastosTexto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    style = TabularNumber,
                )
            }
            if (banco.ingresosTexto.isNotBlank()) {
                Text(
                    text = banco.ingresosTexto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ExtendedTheme.colors.success,
                    style = TabularNumber,
                )
            }
        }
    }
}

@Composable
private fun MiniArrow(up: Boolean, color: Color, size: Dp = 10.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width / 10f
        val path = Path().apply {
            if (up) {
                moveTo(2f * s, 7f * s)
                lineTo(5f * s, 3.5f * s)
                lineTo(8f * s, 7f * s)
            } else {
                moveTo(2f * s, 3.5f * s)
                lineTo(5f * s, 7f * s)
                lineTo(8f * s, 3.5f * s)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun DashboardEmptyState(
    onImportarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dashboard_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dashboard_empty_description),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onImportarClick) {
            Text(text = stringResource(R.string.dashboard_empty_cta))
        }
    }
}
