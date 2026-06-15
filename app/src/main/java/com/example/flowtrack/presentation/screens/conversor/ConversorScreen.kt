package com.example.flowtrack.presentation.screens.conversor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.navigation.NavController
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.usecase.PuntoHistoricoTasa
import com.example.flowtrack.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversorScreen(
    navController: NavController,
    fromSidebar: Boolean = false,
    onMenuClick: () -> Unit = {},
    viewModel: ConversorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val resultado = viewModel.calcularResultado()
    val volver = {
        navController.popBackStack()
        if (fromSidebar) {
            onMenuClick()
        }
    }

    BackHandler(onBack = volver)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Conversor de Divisas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = volver) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(Modifier.padding(Spacing.xl)) {
                    // Moneda origen
                    MonedaRow(
                        label = "De",
                        moneda = if (state.direccionDopAUsd) "DOP" else "USD",
                        onMonedaClick = { viewModel.invertirDireccion() }
                    )

                    Spacer(Modifier.height(Spacing.md))

                    OutlinedTextField(
                        value = state.montoEntrada,
                        onValueChange = { viewModel.setMonto(it) },
                        label = { Text("Monto") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(Spacing.lg))

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { viewModel.invertirDireccion() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Outlined.SwapVert, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(Modifier.height(Spacing.lg))

                    // Moneda destino
                    MonedaRow(
                        label = "A",
                        moneda = if (state.direccionDopAUsd) "USD" else "DOP",
                        onMonedaClick = { viewModel.invertirDireccion() }
                    )

                    Spacer(Modifier.height(Spacing.md))

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(Spacing.md)
                    ) {
                        Text(
                            "%.2f %s".format(Locale.US, resultado.toDouble(), if (state.direccionDopAUsd) "USD" else "DOP"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                state.tasa?.let { tasa ->
                    TasaInfo(tasa, state.direccionDopAUsd)
                }
            }

            if (state.serieHistorico.isNotEmpty()) {
                HistoricoSection(state.serieHistorico)
            }
        }
    }
}

@Composable
fun MonedaRow(label: String, moneda: String, onMonedaClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.clickable { onMonedaClick() }, verticalAlignment = Alignment.CenterVertically) {
            Text(moneda, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(Spacing.xs))
            Icon(Icons.Outlined.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TasaInfo(tasa: TasaCambio, dopAUsd: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Tasa de cambio actual", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(
            if (dopAUsd) "Venta: RD$ ${tasa.venta}" else "Compra: RD$ ${tasa.compra}",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HistoricoSection(serie: List<PuntoHistoricoTasa>) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xxl)) {
        Text("Variación 7 días", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val base = serie.firstOrNull()?.venta?.toDouble() ?: 0.0
            serie.forEach { h ->
                val actual = h.venta.toDouble()
                val diff = actual - base
                val color = if (diff >= 0) ExtendedTheme.colors.success else MaterialTheme.colorScheme.error
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height((40 + diff * 10).coerceAtLeast(10.0).dp)
                            .background(color, RoundedCornerShape(4.dp))
                    )
                    Text(h.fecha.dayOfMonth.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    "Última actualización: %s".format(serie.last().fecha.toString()),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
