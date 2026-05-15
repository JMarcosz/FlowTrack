package com.example.flowtrack.presentation.screens.tarjetas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.ui.theme.Spacing
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TarjetasScreen(viewModel: TarjetasViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Tarjetas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && state.tarjetas.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.tarjetas.isEmpty()) {
                Text(
                    "No hay tarjetas registradas.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val pagerState = rememberPagerState(pageCount = { state.tarjetas.size })

                    // Carrusel de Tarjetas
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentPadding = PaddingValues(horizontal = Spacing.lg)
                    ) { page ->
                        val tarjeta = state.tarjetas[page]
                        TarjetaCardView(tarjeta)
                    }

                    // Dots indicator
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(state.tarjetas.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(8.dp)
                            )
                        }
                    }

                    Divider()

                    // Historial de la tarjeta seleccionada
                    val tarjetaActual = state.tarjetas[pagerState.currentPage]
                    val historial = state.estadosPorTarjeta[tarjetaActual.id] ?: emptyList()

                    Text(
                        "Historial de Estados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(Spacing.md)
                    )

                    if (historial.isEmpty()) {
                        Text(
                            "No hay cortes registrados para esta tarjeta.",
                            modifier = Modifier.padding(Spacing.md),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(historial) { snap ->
                                EstadoTarjetaItem(snap)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TarjetaCardView(tarjeta: Tarjeta) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B) // Dark modern look for card
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tarjeta.bancoCodigo, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White)
            }

            Text(
                "**** **** **** ${tarjeta.ultimos4}",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                letterSpacing = 2.sp
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("LÍMITE", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Text(formatMoney(tarjeta.limiteCredito, tarjeta.moneda), color = Color.White, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CORTE / PAGO", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Text("${tarjeta.diaCorte} / ${tarjeta.diaPago}", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun EstadoTarjetaItem(snap: EstadoTarjetaSnap) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Corte: ${formatDate(snap.fechaCorte.atZone(java.time.ZoneId.of("America/Santo_Domingo")).toLocalDate())}", fontWeight = FontWeight.Bold)
                Text(formatMoney(snap.balanceAlCorte, snap.moneda), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pago Mínimo:", style = MaterialTheme.typography.bodyMedium)
                Text(formatMoney(snap.pagoMinimo, snap.moneda), style = MaterialTheme.typography.bodyMedium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vencimiento:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(snap.fechaLimitePago.atZone(java.time.ZoneId.of("America/Santo_Domingo")).toLocalDate()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
