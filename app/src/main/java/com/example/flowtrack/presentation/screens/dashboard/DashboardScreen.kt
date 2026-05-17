package com.example.flowtrack.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.presentation.components.DonutChart
import com.example.flowtrack.presentation.components.DonutSlice
import com.example.flowtrack.presentation.components.StatCard
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.presentation.components.DashboardStatShimmerCard
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Income
import com.example.flowtrack.ui.theme.Spacing

private val TIPOS_GASTO_DONUT = setOf(
    TipoMovimientoTarjeta.COMPRA,
    TipoMovimientoTarjeta.AVANCE_EFECTIVO,
    TipoMovimientoTarjeta.INTERES,
    TipoMovimientoTarjeta.COMISION,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController? = null,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(Unit) { viewModel.cargarDatos() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController?.navigate(Screen.Upload.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Importar")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val st = estado) {
                is DashboardEstado.Cargando -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            DashboardStatShimmerCard()
                            DashboardStatShimmerCard()
                        }
                    }
                }
                is DashboardEstado.Error -> {
                    com.example.flowtrack.presentation.components.ErrorState(
                        mensaje = st.mensaje,
                        onRetry = { viewModel.cargarDatos() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is DashboardEstado.Exito -> {
                    DashboardContent(st, navController)
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    estado: DashboardEstado.Exito,
    navController: NavController?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text("Resumen del Mes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(Spacing.sm))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard(
                    label = "Gastos Actuales",
                    value = formatMoney(estado.comparativa.gastoActual),
                    modifier = Modifier.weight(1f)
                )
                
                val perc = estado.comparativa.porcentaje?.let { "%.1f%%".format(it) } ?: "N/A"
                val percStr = if (estado.comparativa.esIncremento) "+$perc" else "-$perc"
                val percColor = if (estado.comparativa.esIncremento) Expense else Income
                
                StatCard(
                    label = "vs Mes Anterior",
                    value = percStr,
                    valueColor = if (estado.comparativa.porcentaje != null) percColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text("Categorías", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(Spacing.sm))
            
            val gastosCuentaPorCat = estado.transaccionesMes
                .filter { it.tipo == TipoTransaccion.DEBITO && !it.esDerivada }
                .groupBy { it.categoriaId ?: "Sin Categorizar" }
                .mapValues { (_, txs) -> txs.sumOf { it.monto } }

            val gastosTarjetaPorCat = estado.movimientosMes
                .filter { it.tipoMovimiento in TIPOS_GASTO_DONUT }
                .groupBy { it.categoriaId ?: "Sin Categorizar" }
                .mapValues { (_, movs) -> movs.sumOf { it.monto } }

            val gastosPorCategoria = (gastosCuentaPorCat.keys + gastosTarjetaPorCat.keys)
                .distinct()
                .map { cat ->
                    val total = (gastosCuentaPorCat[cat] ?: java.math.BigDecimal.ZERO) +
                        (gastosTarjetaPorCat[cat] ?: java.math.BigDecimal.ZERO)
                    cat to total
                }
                .sortedByDescending { it.second }
                .take(5)
            
            val colors = listOf(Color(0xFF2F6FED), Color(0xFFE91E63), Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF9C27B0))
            val slices = gastosPorCategoria.mapIndexed { index, pair ->
                DonutSlice(pair.second.toFloat(), colors[index % colors.size], pair.first)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = slices,
                    modifier = Modifier.size(140.dp),
                    centerText = "Total",
                    strokeWidth = 30f
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    slices.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(slice.color))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(slice.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        item {
            Text("Tus Cuentas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (estado.cuentas.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "Sin Cuentas",
                    description = "Aún no tienes cuentas sincronizadas. Usa el botón '+' para importar."
                )
            }
        } else {
            items(estado.cuentas) { cuenta ->
                CuentaItem(cuenta)
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
fun CuentaItem(cuenta: Cuenta) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(cuenta.alias, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(cuenta.bancoCodigo, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                cuenta.balanceActual?.let { formatMoney(it) } ?: "RD$ 0.00",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
