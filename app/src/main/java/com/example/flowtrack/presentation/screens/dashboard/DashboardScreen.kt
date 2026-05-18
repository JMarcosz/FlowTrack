package com.example.flowtrack.presentation.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.presentation.components.*
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.*
import java.math.BigDecimal

private val TIPOS_GASTO = setOf(
    TipoMovimientoTarjeta.COMPRA,
    TipoMovimientoTarjeta.AVANCE_EFECTIVO,
    TipoMovimientoTarjeta.INTERES,
    TipoMovimientoTarjeta.COMISION,
)

private val TIPOS_INGRESO_TARJETA = setOf(
    TipoMovimientoTarjeta.PAGO,
    TipoMovimientoTarjeta.CASHBACK,
    TipoMovimientoTarjeta.DEVOLUCION,
)

private data class DatosBanco(
    val cod: String,
    val gastos: BigDecimal,
    val ingresos: BigDecimal,
)

@Composable
fun DashboardScreen(
    navController: NavController? = null,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val estado  by viewModel.estado.collectAsState()
    val periodo by viewModel.periodo.collectAsState()

    // Sin Scaffold interno para no interferir con los window insets del Scaffold externo.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen),
    ) {
        when (val st = estado) {
            is DashboardEstado.Cargando -> LoadingContent()
            is DashboardEstado.Error    -> ErrorState(
                mensaje = st.mensaje,
                onRetry = { viewModel.cargarDatos() },
                modifier = Modifier.align(Alignment.Center),
            )
            is DashboardEstado.Exito    -> DashboardContent(
                estado     = st,
                periodo    = periodo,
                onPeriodo  = { viewModel.seleccionarPeriodo(it) },
                navController = navController,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // TopBar placeholder
        Spacer(modifier = Modifier.height(52.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
            Box(modifier = Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
        }
        Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
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
) {
    // ── Bank data (fuente única de verdad) ───────────────────────────────────
    // Recoge TODOS los códigos de banco con cualquier actividad (gastos o ingresos).
    // Así QIK (solo tarjeta) y CIBAO (solo créditos) aparecen aunque no tengan gastos.
    val todosCodigos = (
        estado.transaccionesMes.map { it.bancoCodigo } +
        estado.movimientosMes.map { it.bancoCodigo }
    ).distinct()

    val datosPorBanco: List<DatosBanco> = todosCodigos.map { cod ->
        val gastoCuenta = estado.transaccionesMes
            .filter { it.bancoCodigo == cod && it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { a, tx -> a + tx.monto }
        val gastoTarj = estado.movimientosMes
            .filter { it.bancoCodigo == cod && it.tipoMovimiento in TIPOS_GASTO }
            .fold(BigDecimal.ZERO) { a, mv -> a + mv.monto }
        val ingrCuenta = estado.transaccionesMes
            .filter { it.bancoCodigo == cod && it.tipo == TipoTransaccion.CREDITO && !it.esDerivada }
            .fold(BigDecimal.ZERO) { a, tx -> a + tx.monto }
        val ingrTarj = estado.movimientosMes
            .filter { it.bancoCodigo == cod && it.tipoMovimiento in TIPOS_INGRESO_TARJETA }
            .fold(BigDecimal.ZERO) { a, mv -> a + mv.monto }
        DatosBanco(
            cod      = cod,
            gastos   = gastoCuenta + gastoTarj,
            ingresos = ingrCuenta + ingrTarj,
        )
    }.sortedByDescending { it.gastos + it.ingresos }

    // Los totales del header son la suma exacta de todos los bancos → siempre coinciden.
    val gastoTotal      = datosPorBanco.fold(BigDecimal.ZERO) { a, b -> a + b.gastos }
    val ingresosTotales = datosPorBanco.fold(BigDecimal.ZERO) { a, b -> a + b.ingresos }
    val balanceNeto     = ingresosTotales - gastoTotal

    // ── Category breakdown (top 5 por gastos) ────────────────────────────────
    val catCuenta = estado.transaccionesMes
        .filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
        .groupBy { it.categoriaId ?: "sin_categorizar" }
        .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, tx -> a + tx.monto } }

    val catTarjeta = estado.movimientosMes
        .filter { it.tipoMovimiento in TIPOS_GASTO }
        .groupBy { it.categoriaId ?: "sin_categorizar" }
        .mapValues { (_, mvs) -> mvs.fold(BigDecimal.ZERO) { a, mv -> a + mv.monto } }

    val gastosPorCat = (catCuenta.keys + catTarjeta.keys).distinct()
        .map { id ->
            id to ((catCuenta[id] ?: BigDecimal.ZERO) + (catTarjeta[id] ?: BigDecimal.ZERO))
        }
        .sortedByDescending { it.second }
        .take(5)

    val totalCat = gastosPorCat.fold(BigDecimal.ZERO) { a, p -> a + p.second }
        .coerceAtLeast(BigDecimal.ONE)

    val slices = gastosPorCat.map { (catId, monto) ->
        val cat = categoriaPorId(catId)
        DonutSlice(monto.toFloat(), cat.color, cat.nombre)
    }

    // Delta pill: solo para "Este mes" y cuando la comparativa ya cargó
    val mostrarDelta = periodo == "Este mes"
    val deltaPct     = estado.comparativa?.porcentaje
    val esIncremento = estado.comparativa?.esIncremento ?: false

    // ── UI ────────────────────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // TopBar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Menú", tint = Ink)
                }
                Text(
                    "Dashboard",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp, color = Ink,
                )
                IconButton(onClick = { navController?.navigate(Screen.Notificaciones.route) }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones", tint = Ink)
                }
            }
        }

        // ── Resumen general ──────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Título + selector período
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Resumen general",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp, color = Ink,
                    )
                    PeriodoDropdown(
                        selected = periodo,
                        options  = PERIODOS_DASHBOARD,
                        onSelect = onPeriodo,
                    )
                }

                // StatCards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DashStatCard(
                        label      = "Gasto total",
                        value      = formatMoney(gastoTotal),
                        valueColor = Expense,
                        bgColor    = Expense50,
                        modifier   = Modifier.weight(1f),
                    )
                    DashStatCard(
                        label      = "Ingresos totales",
                        value      = formatMoney(ingresosTotales),
                        valueColor = Income,
                        bgColor    = Income50,
                        modifier   = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Balance neto card
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, Line2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Balance neto", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Muted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatMoney(balanceNeto),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp, color = Ink,
                            )
                        }
                        if (mostrarDelta && deltaPct != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                val pctStr  = "%.1f%%".format(deltaPct.toDouble())
                                val signo   = if (esIncremento) "+" else "-"
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Income50)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.TrendingUp, contentDescription = null,
                                        tint = Income, modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        "$signo$pctStr",
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Income,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("vs mes anterior", fontSize = 11.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }

        // ── Gastos por categoría ─────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, Line2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(Screen.Resumen.route) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Gastos por categoría",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink,
                            )
                            Icon(
                                Icons.Outlined.ChevronRight, contentDescription = null,
                                tint = Muted2, modifier = Modifier.size(18.dp),
                            )
                        }
                        if (slices.isEmpty()) {
                            Text(
                                "Sin gastos en este período",
                                fontSize = 14.sp, color = Muted,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    gastosPorCat.forEachIndexed { i, (_, monto) ->
                                        val slice = slices[i]
                                        val pct   = ((monto / totalCat) * BigDecimal(100)).toInt()
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
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "$pct%",
                                                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Muted,
                                            )
                                        }
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
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp, color = Ink,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (datosPorBanco.isEmpty()) {
                    Text("Sin actividad bancaria en este período", fontSize = 14.sp, color = Muted)
                } else {
                    Card(
                        shape  = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        elevation = CardDefaults.cardElevation(0.dp),
                        border = BorderStroke(1.dp, Line2),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        datosPorBanco.forEachIndexed { i, datos ->
                            val banco = bancoPorCodigo(datos.cod)
                            BancoRow(banco = banco, datos = datos)
                            if (i < datosPorBanco.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Line2, thickness = 1.dp,
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
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashStatCard(
    label: String, value: String,
    valueColor: Color, bgColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(14.dp),
    ) {
        Column {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Muted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp, color = valueColor,
            )
        }
    }
}

@Composable
private fun BancoRow(banco: BancoUI, datos: DatosBanco) {
    val abbr = when (banco.codigo) {
        "BANRESERVAS" -> "BR"
        "POPULAR"     -> "P"
        "QIK"         -> "QIK"
        "CIBAO"       -> "CI"
        "BHD"         -> "BHD"
        else          -> banco.codigo.take(2)
    }
    val isLight = banco.codigo == "QIK"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Badge del banco
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(banco.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                abbr,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = if (isLight) Ink else Color.White,
            )
        }

        // Nombre del banco
        Text(
            banco.nombre,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink,
            maxLines = 1,
        )

        // Gastos (rojo) e ingresos (verde) alineados a la derecha
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (datos.gastos > BigDecimal.ZERO) {
                Text(
                    "- ${formatMoney(datos.gastos)}",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Expense,
                )
            }
            if (datos.ingresos > BigDecimal.ZERO) {
                Text(
                    "+ ${formatMoney(datos.ingresos)}",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Income,
                )
            }
        }
    }
}

@Composable
private fun PeriodoDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgCard, contentColor = Ink),
            border = BorderStroke(1.dp, Line),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Text(selected, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                tint = Muted, modifier = Modifier.size(14.dp),
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
                            )
                            if (opt == selected) {
                                Icon(
                                    Icons.Outlined.Check, contentDescription = null,
                                    tint = Primary, modifier = Modifier.size(16.dp),
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
