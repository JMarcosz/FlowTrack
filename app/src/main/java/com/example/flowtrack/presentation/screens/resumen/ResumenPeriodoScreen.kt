package com.example.flowtrack.presentation.screens.resumen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.usecase.BucketResumen
import com.example.flowtrack.domain.usecase.TipoPeriodo
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.ui.theme.*
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenPeriodoScreen(
    navController: NavController,
    viewModel: ResumenPeriodoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val opciones = listOf(TipoPeriodo.DIA to "Día", TipoPeriodo.SEMANA to "Semana", TipoPeriodo.MES to "Mes")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Resumen por período", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) {
                opciones.forEachIndexed { idx, (tipo, label) ->
                    SegmentedButton(
                        selected = state.tipo == tipo,
                        onClick = { viewModel.setTipo(tipo) },
                        shape = SegmentedButtonDefaults.itemShape(idx, opciones.size),
                    ) { Text(label) }
                }
            }

            val resumen = state.resumen
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }

                resumen == null || resumen.buckets.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "Sin datos en el período",
                    description = "Importa estados de cuenta para ver tus resúmenes.",
                )

                else -> {
                    TotalesHeader(resumen.totalIngresos, resumen.totalGastos)
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(resumen.buckets.asReversed()) { bucket -> BucketRow(bucket) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalesHeader(ingresos: BigDecimal, gastos: BigDecimal) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TotalCard("Ingresos", ingresos, ExtendedTheme.colors.success, Modifier.weight(1f))
        TotalCard("Gastos", gastos, MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable
private fun TotalCard(titulo: String, monto: BigDecimal, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
        modifier = modifier,
        shape = Radii.md,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMoney(monto, withSign = false),
                style = MaterialTheme.typography.titleMedium.merge(TabularNumber),
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun BucketRow(bucket: BucketResumen) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
        shape = Radii.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(bucket.etiqueta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(bucket.balance, withSign = true),
                    style = MaterialTheme.typography.bodyLarge.merge(TabularNumber), fontWeight = FontWeight.SemiBold,
                    color = if (bucket.balance >= BigDecimal.ZERO) ExtendedTheme.colors.success else MaterialTheme.colorScheme.error)
                Text("+${formatMoney(bucket.ingresos)} · -${formatMoney(bucket.gastos)}",
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumber), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
