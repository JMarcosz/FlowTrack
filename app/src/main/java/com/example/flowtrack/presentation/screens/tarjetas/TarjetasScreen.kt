package com.example.flowtrack.presentation.screens.tarjetas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.core.extensions.toBigDecimalSafe
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Spacing
import androidx.compose.ui.unit.sp
import com.example.flowtrack.presentation.components.CreditCardShimmerItem
import com.example.flowtrack.presentation.components.ShimmerHistoryItem
import com.example.flowtrack.presentation.components.EmptyState

private val BANCOS_TARJETA = listOf("BANRESERVAS", "POPULAR", "QIK", "CIBAO", "BHD", "OTRO")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TarjetasScreen(viewModel: TarjetasViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var mostrarSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Refresca datos cada vez que el composable entra en composición
    LaunchedEffect(Unit) { viewModel.cargarDatos() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Tarjetas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarSheet = true },
                containerColor = Primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "Agregar tarjeta") }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && state.tarjetas.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    CreditCardShimmerItem()
                    Spacer(Modifier.height(Spacing.md))
                    repeat(3) { ShimmerHistoryItem() }
                }
            } else if (state.tarjetas.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.CreditCardOff,
                    title = "Sin tarjetas",
                    description = "Importa un estado de cuenta o agrega una tarjeta manual.",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val pagerState = rememberPagerState(pageCount = { state.tarjetas.size })

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentPadding = PaddingValues(horizontal = Spacing.lg),
                    ) { page ->
                        val tarjeta = state.tarjetas[page]
                        TarjetaCardView(
                            tarjeta = tarjeta,
                            onEliminar = { viewModel.eliminarTarjeta(tarjeta.id) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(state.tarjetas.size) { iteration ->
                            val isSelected = pagerState.currentPage == iteration
                            val color by androidx.compose.animation.animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                label = "dot_color",
                            )
                            val width by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (isSelected) 24.dp else 8.dp,
                                label = "dot_width",
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(width = width, height = 8.dp),
                            )
                        }
                    }

                    HorizontalDivider()

                    val tarjetaActual = state.tarjetas[pagerState.currentPage]
                    val historial = state.estadosPorTarjeta[tarjetaActual.id] ?: emptyList()

                    Text(
                        "Historial de Estados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(Spacing.md),
                    )

                    if (historial.isEmpty()) {
                        Text(
                            "No hay cortes registrados para esta tarjeta.",
                            modifier = Modifier.padding(Spacing.md),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(historial) { snap -> EstadoTarjetaItem(snap) }
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

@Composable
private fun NuevaTarjetaSheet(
    onGuardar: (banco: String, ultimos4: String, alias: String, limite: java.math.BigDecimal, diaCorte: Int) -> Unit,
    onCancelar: () -> Unit,
) {
    var bancoSeleccionado by remember { mutableStateOf(BANCOS_TARJETA.first()) }
    var bancoExpandido by remember { mutableStateOf(false) }
    var ultimos4 by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var limite by remember { mutableStateOf("") }
    var diaCorte by remember { mutableStateOf("") }

    val limiteValido = limite.toBigDecimalSafe()
    val diaCortValido = diaCorte.toIntOrNull()?.takeIf { it in 1..31 }
    val puedeGuardar = ultimos4.length == 4 && alias.isNotBlank() && limiteValido != null && diaCortValido != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
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
                modifier = Modifier.fillMaxWidth().clickable { bancoExpandido = true },
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
                onClick = {
                    onGuardar(
                        bancoSeleccionado,
                        ultimos4,
                        alias.trim(),
                        limiteValido!!,
                        diaCortValido!!,
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = puedeGuardar,
            ) { Text("Guardar") }
        }
    }
}

@Composable
fun TarjetaCardView(tarjeta: Tarjeta, onEliminar: (() -> Unit)? = null) {
    var menuExpandido by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tarjeta.bancoCodigo, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White)
                    if (onEliminar != null) {
                        Box {
                            IconButton(onClick = { menuExpandido = true }) {
                                Icon(Icons.Outlined.MoreVert, null, tint = Color.White)
                            }
                            DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                                DropdownMenuItem(
                                    text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = { menuExpandido = false; onEliminar() },
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "**** **** **** ${tarjeta.ultimos4}",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                letterSpacing = 2.sp,
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
