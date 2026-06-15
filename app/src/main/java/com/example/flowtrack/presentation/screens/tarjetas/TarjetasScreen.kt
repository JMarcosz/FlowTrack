package com.example.flowtrack.presentation.screens.tarjetas

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.presentation.components.BankBadge
import com.example.flowtrack.presentation.components.CreditCardShimmerItem
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.presentation.components.bankBadge
import com.example.flowtrack.ui.theme.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val BANCOS_TARJETA = listOf("BANRESERVAS", "POPULAR", "QIK", "CIBAO", "BHD", "OTRO")
private val ZONA = ZoneId.of("America/Santo_Domingo")

// â”€â”€ Screen â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TarjetasScreen(
    onMenuClick: () -> Unit = {},
    viewModel: TarjetasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var mostrarSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
                    .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Outlined.Menu, contentDescription = "MenÃº", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(
                        "Tarjetas",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        "CrÃ©dito y pagos",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { mostrarSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar tarjeta", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // â”€â”€ Content â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            when {
                state.isLoading && state.tarjetas.isEmpty() -> CreditCardShimmerItem()

                state.tarjetas.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.CreditCardOff,
                    title = "Sin tarjetas",
                    description = "Importa un estado de cuenta o agrega una tarjeta manual.",
                    modifier = Modifier.fillMaxSize(),
                )

                else -> {
                    val pagerState = rememberPagerState(pageCount = { state.tarjetas.size })
                    val activeIdx = pagerState.currentPage
                    val tarjetaActual = state.tarjetas[activeIdx]
                    val historial = (state.estadosPorTarjeta[tarjetaActual.id] ?: emptyList())
                        .sortedByDescending { it.fechaCorte }
                    val movimientosRecientes = (state.movimientosPorTarjeta[tarjetaActual.id] ?: emptyList())
                        .sortedByDescending { it.fechaTransaccion }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Spacing.xxl),
                    ) {
                        // â”€â”€ Card carousel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        item {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = Spacing.xxl),
                                pageSpacing = Spacing.md,
                            ) { page ->
                                val tarjeta = state.tarjetas[page]
                                val snap = (state.estadosPorTarjeta[tarjeta.id] ?: emptyList())
                                    .maxByOrNull { it.fechaCorte }
                                WhiteCreditCard(
                                    tarjeta = tarjeta,
                                    snap = snap,
                                    onEliminar = { viewModel.eliminarTarjeta(tarjeta.id) },
                                )
                            }
                        }

                        // â”€â”€ Page dots â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.lg),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                repeat(state.tarjetas.size) { i ->
                                    val isActive = activeIdx == i
                                    val width by animateDpAsState(
                                        targetValue = if (isActive) 18.dp else 6.dp,
                                        label = "dot",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                            .size(width = width, height = 6.dp),
                                    )
                                }
                            }
                        }

                        // â”€â”€ PrÃ³ximos pagos â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        if (movimientosRecientes.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    "Movimientos recientes",
                                    modifier = Modifier.padding(
                                        start = Spacing.xxl, end = Spacing.xxl,
                                        top = Spacing.sm, bottom = Spacing.sm,
                                    ),
                                )
                            }
                            item {
                                WhiteCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.xxl),
                                ) {
                                    movimientosRecientes.take(8).forEachIndexed { idx, mov ->
                                        MovimientoTarjetaRow(mov)
                                        if (idx < movimientosRecientes.take(8).lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                        }

                        val ahora = Instant.now()
                        val pagosProximos = state.tarjetas.mapNotNull { t ->
                            val snap = (state.estadosPorTarjeta[t.id] ?: emptyList())
                                .maxByOrNull { it.fechaCorte }
                                ?.takeIf { it.fechaLimitePago.isAfter(ahora) && ChronoUnit.DAYS.between(ahora, it.fechaLimitePago) <= 60 }
                            snap?.let { t to it }
                        }.sortedBy { it.second.fechaLimitePago }

                        if (pagosProximos.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    "PrÃ³ximos pagos",
                                    modifier = Modifier.padding(
                                        start = Spacing.xxl, end = Spacing.xxl,
                                        top = Spacing.xxl, bottom = Spacing.sm,
                                    ),
                                )
                            }
                            item {
                                WhiteCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.xxl),
                                ) {
                                    pagosProximos.forEachIndexed { idx, (tarjeta, snap) ->
                                        PagoProximoRow(tarjeta = tarjeta, snap = snap, ahora = ahora)
                                        if (idx < pagosProximos.lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                        }

                        // â”€â”€ Otras tarjetas â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        val otras = state.tarjetas.filterIndexed { i, _ -> i != activeIdx }
                        if (otras.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    "Otras tarjetas",
                                    modifier = Modifier.padding(
                                        start = Spacing.xxl, end = Spacing.xxl,
                                        top = Spacing.sm, bottom = Spacing.sm,
                                    ),
                                )
                            }
                            item {
                                WhiteCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.xxl),
                                ) {
                                    otras.forEachIndexed { idx, t ->
                                        val snap2 = (state.estadosPorTarjeta[t.id] ?: emptyList())
                                            .maxByOrNull { it.fechaCorte }
                                        OtraTarjetaRow(tarjeta = t, snap = snap2)
                                        if (idx < otras.lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                        }

                        // â”€â”€ Historial â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = Spacing.xxl, end = Spacing.xxl,
                                        top = Spacing.xxl, bottom = Spacing.sm,
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Historial de estados", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        if (historial.isEmpty()) {
                            item {
                                Text(
                                    "No hay cortes registrados.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.xxl),
                                )
                            }
                        } else {
                            item {
                                WhiteCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.xxl),
                                ) {
                                    historial.forEachIndexed { idx, snap ->
                                        HistorialRow(snap)
                                        if (idx < historial.lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarSheet) {
        ModalBottomSheet(
            onDismissRequest = { mostrarSheet = false },
            sheetState = sheetState,
        ) {
            NuevaTarjetaSheet(
                onGuardar = { banco, ultimos4, alias, limite, diaCorte ->
                    viewModel.guardarTarjeta(banco, ultimos4, alias, limite, diaCorte)
                    mostrarSheet = false
                },
                onCancelar = { mostrarSheet = false },
            )
        }
    }
}

// â”€â”€ White credit card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun WhiteCreditCard(
    tarjeta: Tarjeta,
    snap: EstadoTarjetaSnap?,
    onEliminar: (() -> Unit)? = null,
) {
    val banco  = bancoPorCodigo(tarjeta.bancoCodigo)
    val badge  = bankBadge(tarjeta.bancoCodigo)

    val pagoPendiente    = snap?.balanceAlCorte ?: BigDecimal.ZERO
    val pagoMinimo       = snap?.pagoMinimo     ?: BigDecimal.ZERO
    val utilizacionFrac  = if (tarjeta.limiteCredito > BigDecimal.ZERO)
        (pagoPendiente.toFloat() / tarjeta.limiteCredito.toFloat()).coerceIn(0f, 1f) else 0f
    val limiteDisponible = (tarjeta.limiteCredito - pagoPendiente).coerceAtLeast(BigDecimal.ZERO)

    var menuExpandido by remember { mutableStateOf(false) }

    WhiteCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {

            // â”€â”€ Top: badge Â· name Â· status Â· menu â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankBadgeCircle(badge, size = 40.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tarjeta.alias.ifBlank { banco.nombre },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "â€¢â€¢â€¢â€¢ ${tarjeta.ultimos4}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                }
                // Status badge
                StatusBadge("Al dÃ­a")
                Spacer(Modifier.width(4.dp))
                // More menu
                if (onEliminar != null) {
                    Box {
                        IconButton(
                            onClick = { menuExpandido = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpandido = false; onEliminar() },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // â”€â”€ Dates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
                CardInfoItem("Corte", "dÃ­a ${tarjeta.diaCorte}")
                CardInfoItem("Pago", "dÃ­a ${tarjeta.diaPago}")
            }

            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.lg))

            // â”€â”€ Balance row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Pago total pendiente", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatMoney(pagoPendiente, tarjeta.moneda),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = (-0.4).sp,
                        style = TabularNumber,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Pago mÃ­nimo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatMoney(pagoMinimo, tarjeta.moneda),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = TabularNumber,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // â”€â”€ UtilizaciÃ³n â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("UtilizaciÃ³n", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${"%.0f".format(utilizacionFrac * 100)}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(Radii.pill)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(utilizacionFrac)
                        .fillMaxHeight()
                        .clip(Radii.pill)
                        .background(if (utilizacionFrac > 0.6f) MaterialTheme.colorScheme.error else ExtendedTheme.colors.success),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LÃ­mite disponible", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatMoney(limiteDisponible, tarjeta.moneda),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TabularNumber,
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            // â”€â”€ Ver detalle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Text(
                "Ver detalle â€º",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// â”€â”€ Pago prÃ³ximo row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun PagoProximoRow(tarjeta: Tarjeta, snap: EstadoTarjetaSnap, ahora: Instant) {
    val badge = bankBadge(tarjeta.bancoCodigo)
    val banco = bancoPorCodigo(tarjeta.bancoCodigo)
    val diasRestantes = ChronoUnit.DAYS.between(ahora, snap.fechaLimitePago).toInt()
    val fechaLocal = snap.fechaLimitePago.atZone(ZONA).toLocalDate()

    val (urgenciaColor, urgenciaLabel) = when {
        diasRestantes <= 2  -> MaterialTheme.colorScheme.error to "Vence pronto"
        diasRestantes <= 6  -> ExtendedTheme.colors.warning to "Esta semana"
        else                -> ExtendedTheme.colors.success to "A tiempo"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BankBadgeCircle(badge, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tarjeta.alias.ifBlank { banco.nombre },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Vence ${formatDate(fechaLocal)} â€¢ $diasRestantes dÃ­a${if (diasRestantes == 1) "" else "s"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(snap.pagoTotal), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, style = TabularNumber)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(urgenciaColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(urgenciaLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = urgenciaColor)
            }
        }
    }
}

// â”€â”€ Otra tarjeta compact row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun OtraTarjetaRow(tarjeta: Tarjeta, snap: EstadoTarjetaSnap?) {
    val badge = bankBadge(tarjeta.bancoCodigo)
    val banco = bancoPorCodigo(tarjeta.bancoCodigo)
    val pagoPendiente = snap?.balanceAlCorte ?: BigDecimal.ZERO

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Mini card thumbnail
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 26.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(badge.bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge.abbr,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                color = badge.fg,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tarjeta.alias.ifBlank { banco.nombre },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "â€¢â€¢â€¢â€¢ ${tarjeta.ultimos4} Â· Corte dÃ­a ${tarjeta.diaCorte}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatMoney(pagoPendiente, tarjeta.moneda),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            style = TabularNumber,
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

// â”€â”€ Historial row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun HistorialRow(snap: EstadoTarjetaSnap) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatDate(snap.fechaCorte.atZone(ZONA).toLocalDate()),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMoney(snap.balanceAlCorte, snap.moneda),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            style = TabularNumber,
        )
    }
}


@Composable
private fun MovimientoTarjetaRow(mov: MovimientoTarjeta) {
    val esIngreso = mov.tipoMovimiento == TipoMovimientoTarjeta.PAGO ||
        mov.tipoMovimiento == TipoMovimientoTarjeta.CASHBACK
    val signo = if (esIngreso) "+" else "-"
    val color = if (esIngreso) ExtendedTheme.colors.success else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mov.descripcionOriginal.ifBlank { mov.descripcionNormalizada },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatDate(mov.fechaTransaccion.atZone(ZONA).toLocalDate()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "$signo${formatMoney(mov.monto, mov.moneda)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            style = TabularNumber,
        )
    }
}

// â”€â”€ Reusable sub-components â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun BankBadgeCircle(badge: BankBadge, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(badge.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            badge.abbr,
            fontSize = if (badge.abbr.length <= 2) 13.sp else 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = badge.fg,
            letterSpacing = (-0.3).sp,
        )
    }
}

@Composable
fun StatusBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(Radii.pill)
            .background(ExtendedTheme.colors.successContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ExtendedTheme.colors.onSuccessContainer)
    }
}

@Composable
private fun CardInfoItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(elevation, Radii.lg, ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .clip(Radii.lg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, Radii.lg),
        content = content,
    )
}

// â”€â”€ Nueva tarjeta sheet â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun NuevaTarjetaSheet(
    onGuardar: (banco: String, ultimos4: String, alias: String, limite: BigDecimal, diaCorte: Int) -> Unit,
    onCancelar: () -> Unit,
) {
    var bancoSeleccionado by remember { mutableStateOf(BANCOS_TARJETA.first()) }
    var bancoExpandido    by remember { mutableStateOf(false) }
    var ultimos4          by remember { mutableStateOf("") }
    var alias             by remember { mutableStateOf("") }
    var limite            by remember { mutableStateOf("") }
    var diaCorte          by remember { mutableStateOf("") }

    val limiteValido   = limite.toBigDecimalSafe()
    val diaCortValido  = diaCorte.toIntOrNull()?.takeIf { it in 1..31 }
    val puedeGuardar   = ultimos4.length == 4 && alias.isNotBlank() && limiteValido != null && diaCortValido != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Nueva tarjeta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = bancoSeleccionado,
                onValueChange = {},
                readOnly = true,
                label = { Text("Banco") },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { bancoExpandido = true },
            )
            DropdownMenu(expanded = bancoExpandido, onDismissRequest = { bancoExpandido = false }) {
                BANCOS_TARJETA.forEach { banco ->
                    DropdownMenuItem(
                        text = { Text(banco) },
                        onClick = { bancoSeleccionado = banco; bancoExpandido = false },
                    )
                }
            }
        }

        OutlinedTextField(
            value = ultimos4,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) ultimos4 = it },
            label = { Text("Ãšltimos 4 dÃ­gitos") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text("Alias (ej: Visa Cibao)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )

        OutlinedTextField(
            value = limite,
            onValueChange = { limite = it },
            label = { Text("LÃ­mite de crÃ©dito (RD\$)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        OutlinedTextField(
            value = diaCorte,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) diaCorte = it },
            label = { Text("DÃ­a de corte (1-31)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                onClick = { onGuardar(bancoSeleccionado, ultimos4, alias.trim(), limiteValido!!, diaCortValido!!) },
                modifier = Modifier.weight(1f),
                enabled = puedeGuardar,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("Guardar", color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}
