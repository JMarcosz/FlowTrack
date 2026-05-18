package com.example.flowtrack.presentation.screens.conversor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.data.firestore.repositories.TasaCambio
import com.example.flowtrack.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversorScreen(viewModel: ConversorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversor Divisas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else if (state.tasa != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Indicador de tasas del día
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text("Tasa del Día (${state.tasa!!.fuente})", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(Spacing.sm))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Compra", style = MaterialTheme.typography.bodySmall)
                                    Text("RD$ ${state.tasa!!.compra}", fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Venta", style = MaterialTheme.typography.bodySmall)
                                    Text("RD$ ${state.tasa!!.venta}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.xl))

                    val labelOrigen = if (state.direccionDopAUsd) "Monto en DOP (RD$)" else "Monto en USD (US$)"
                    val labelDestino = if (state.direccionDopAUsd) "Equivalente en USD" else "Equivalente en DOP"

                    OutlinedTextField(
                        value = state.montoEntrada,
                        onValueChange = { viewModel.setMonto(it) },
                        label = { Text(labelOrigen) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(Spacing.md))

                    IconButton(
                        onClick = { viewModel.invertirDireccion() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Invertir dirección")
                    }

                    Spacer(Modifier.height(Spacing.md))

                    val result = viewModel.calcularResultado()
                    val formatter = NumberFormat.getNumberInstance(Locale("en", "US")).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }
                    val prefix = if (state.direccionDopAUsd) "US$ " else "RD$ "

                    Text(
                        text = "$prefix${formatter.format(result)}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(labelDestino, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (state.historico.size >= 2) {
                        Spacer(Modifier.height(Spacing.xxl))
                        TasaHistoricoChart(historico = state.historico)
                    }

                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

@Composable
private fun TasaHistoricoChart(historico: List<TasaCambio>) {
    val ventas = historico.map { it.venta }
    val minVal = ventas.min()
    val maxVal = ventas.max()
    val rango = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
    val lineColor = Primary
    val gridColor = Line2

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Evolución tasa venta (30 días)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Mín: RD$ ${"%.2f".format(minVal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Income,
                    fontSize = 11.sp
                )
                Text(
                    "Máx: RD$ ${"%.2f".format(maxVal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Expense,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(Spacing.sm))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val w = size.width
                val h = size.height
                val padLeft = 8f
                val padRight = 8f
                val padTop = 8f
                val padBottom = 8f
                val chartW = w - padLeft - padRight
                val chartH = h - padTop - padBottom

                // Líneas de referencia horizontales
                val steps = 4
                for (i in 0..steps) {
                    val y = padTop + chartH * (1f - i.toFloat() / steps)
                    drawLine(
                        color = gridColor,
                        start = Offset(padLeft, y),
                        end = Offset(w - padRight, y),
                        strokeWidth = 1f
                    )
                }

                // Línea del gráfico
                val path = Path()
                ventas.forEachIndexed { idx, v ->
                    val x = padLeft + chartW * (idx.toFloat() / (ventas.size - 1))
                    val y = padTop + chartH * (1f - ((v - minVal) / rango).toFloat())
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 3f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Puntos en cada dato
                ventas.forEachIndexed { idx, v ->
                    val x = padLeft + chartW * (idx.toFloat() / (ventas.size - 1))
                    val y = padTop + chartH * (1f - ((v - minVal) / rango).toFloat())
                    drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    historico.first().fecha.toString(),
                    fontSize = 10.sp,
                    color = Muted2
                )
                Text(
                    historico.last().fecha.toString(),
                    fontSize = 10.sp,
                    color = Muted2
                )
            }
        }
    }
}
