package com.example.flowtrack.presentation.screens.tarjetas

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.presentation.components.BankBadge
import com.example.flowtrack.presentation.components.CreditCardShimmerItem
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.presentation.components.bankBadge
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.ui.theme.*
import java.math.BigDecimal
import java.time.ZoneId

private val BANCOS_TARJETA = listOf("BANRESERVAS", "POPULAR", "QIK", "CIBAO", "BHD", "OTRO")
private val ZONA = ZoneId.of("America/Santo_Domingo")

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TarjetasScreen(viewModel: TarjetasViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var mostrarSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // ── Header ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xxl, bottom = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Tarjetas",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        "Crédito y pagos",
                        fontSize = 13.sp,
                        color = Muted,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Primary50)
                        .clickable { mostrarSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar tarjeta", tint = Primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // ── Content ───────────────────────────────────────────
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

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Spacing.xxl),
                    ) {
                        // ── Card carousel ──────────────────────────
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

                        // ── Page dots ──────────────────────────────
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
                                            .background(if (isActive) Primary else Line)
                                            .size(width = width, height = 6.dp),
                                    )
                                }
                            }
                        }

                        // ── Otras tarjetas ─────────────────────────
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
                                        if (idx < otras.lastIndex) HorizontalDivider(color = Line2)
                                    }
                                }
                            }
                        }

                        // ── Historial ──────────────────────────────
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
                                Text("Historial de estados", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            }
                        }

                        if (historial.isEmpty()) {
                            item {
                                Text(
                                    "No hay cortes registrados.",
                                    fontSize = 14.sp,
                                    color = Muted,
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
                                        if (idx < historial.lastIndex) HorizontalDivider(color = Line2)
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

// ── White credit card ─────────────────────────────────────────────────────────

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

            // ── Top: badge · name · status · menu ────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankBadgeCircle(badge, size = 40.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tarjeta.alias.ifBlank { banco.nombre },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "•••• ${tarjeta.ultimos4}",
                        fontSize = 13.sp,
                        color = Muted,
                        letterSpacing = 0.8.sp,
                    )
                }
                // Status badge
                StatusBadge("Al día")
                Spacer(Modifier.width(4.dp))
                // More menu
                if (onEliminar != null) {
                    Box {
                        IconButton(
                            onClick = { menuExpandido = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.MoreVert, null, tint = Muted2, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = Expense) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Expense) },
                                onClick = { menuExpandido = false; onEliminar() },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // ── Dates ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
                CardInfoItem("Corte", "día ${tarjeta.diaCorte}")
                CardInfoItem("Pago", "día ${tarjeta.diaPago}")
            }

            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = Line2)
            Spacer(Modifier.height(Spacing.lg))

            // ── Balance row ───────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Pago total pendiente", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatMoney(pagoPendiente, tarjeta.moneda),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Expense,
                        letterSpacing = (-0.4).sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Pago mínimo", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatMoney(pagoMinimo, tarjeta.moneda),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // ── Utilización ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Utilización", fontSize = 12.sp, color = Muted)
                Text("${"%.0f".format(utilizacionFrac * 100)}%", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(Radii.pill)
                    .background(Line2),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(utilizacionFrac)
                        .fillMaxHeight()
                        .clip(Radii.pill)
                        .background(if (utilizacionFrac > 0.6f) Expense else Income),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Límite disponible", fontSize = 11.sp, color = Muted)
                Text(
                    formatMoney(limiteDisponible, tarjeta.moneda),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            // ── Ver detalle ───────────────────────────────────────
            Text(
                "Ver detalle ›",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// ── Otra tarjeta compact row ──────────────────────────────────────────────────

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
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "•••• ${tarjeta.ultimos4} · Corte día ${tarjeta.diaCorte}",
                fontSize = 12.sp,
                color = Muted,
            )
        }
        Text(
            formatMoney(pagoPendiente, tarjeta.moneda),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted2, modifier = Modifier.size(16.dp))
    }
}

// ── Historial row ─────────────────────────────────────────────────────────────

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
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMoney(snap.balanceAlCorte, snap.moneda),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Expense,
        )
    }
}

// ── Reusable sub-components ───────────────────────────────────────────────────

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
            .background(Income50)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Income)
    }
}

@Composable
private fun CardInfoItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = Muted,
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
            .shadow(elevation, Radii.lg, ambientColor = Ink.copy(alpha = 0.04f), spotColor = Ink.copy(alpha = 0.04f))
            .clip(Radii.lg)
            .background(BgCard)
            .border(1.dp, Line2, Radii.lg),
        content = content,
    )
}

// ── Nueva tarjeta sheet ───────────────────────────────────────────────────────

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
        Text("Nueva tarjeta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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
            label = { Text("Últimos 4 dígitos") },
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
            label = { Text("Límite de crédito (RD\$)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        OutlinedTextField(
            value = diaCorte,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) diaCorte = it },
            label = { Text("Día de corte (1-31)") },
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
            ) { Text("Guardar") }
        }
    }
}
