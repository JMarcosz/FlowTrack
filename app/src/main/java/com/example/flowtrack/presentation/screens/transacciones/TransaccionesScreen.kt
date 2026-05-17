package com.example.flowtrack.presentation.screens.transacciones

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.core.extensions.formatDateRelative
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.presentation.components.categoriaRegistry
import com.example.flowtrack.presentation.components.EmptyState
import com.example.flowtrack.presentation.components.TransactionShimmerItem
import com.example.flowtrack.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransaccionesScreen(viewModel: TransaccionesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.cargarTransacciones() }

    // Agrupar derivadas de state.transacciones para que el cambio de filtro recomponga el mapa
    val zona = remember { ZoneId.of("America/Santo_Domingo") }
    val grouped: Map<LocalDate, List<Transaccion>> = remember(state.transacciones) {
        state.transacciones
            .filter { !it.esDerivada }
            .groupBy { it.fecha.atZone(zona).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }

    // Índice de la pestaña de banco activa (0 = Todos)
    val allBancos = remember(state.bancosDisponibles) { listOf<String?>(null) + state.bancosDisponibles }
    val selectedBancoIdx = remember(state.bancoFiltro, allBancos) {
        allBancos.indexOf(state.bancoFiltro).coerceAtLeast(0)
    }

    var showDeleteDialogFor by remember { mutableStateOf<Transaccion?>(null) }
    var showCategorySheetFor by remember { mutableStateOf<Transaccion?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var aplicarATodas by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text("Transacciones", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )

                // ── Pestañas de banco ──────────────────────────────────────────
                if (state.bancosDisponibles.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedBancoIdx,
                        containerColor = MaterialTheme.colorScheme.surface,
                        edgePadding = Spacing.md,
                        divider = {},
                    ) {
                        Tab(
                            selected = selectedBancoIdx == 0,
                            onClick = { viewModel.setBancoFiltro(null) },
                            text = { Text("Todos", style = MaterialTheme.typography.labelLarge) },
                        )
                        state.bancosDisponibles.forEachIndexed { idx, banco ->
                            Tab(
                                selected = selectedBancoIdx == idx + 1,
                                onClick = { viewModel.setBancoFiltro(banco) },
                                text = {
                                    Text(
                                        banco.lowercase().replaceFirstChar { it.uppercaseChar() },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                            )
                        }
                    }
                }

                // ── Campo de búsqueda ──────────────────────────────────────────
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    placeholder = { Text("Buscar por descripción, banco…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )

                // ── Sub-filtro de tipo ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = state.filtroTipo == TipoTransaccionFiltro.TODAS,
                        onClick = { viewModel.setFiltroTipo(TipoTransaccionFiltro.TODAS) },
                        label = { Text("Todas") },
                    )
                    FilterChip(
                        selected = state.filtroTipo == TipoTransaccionFiltro.DEBITO,
                        onClick = { viewModel.setFiltroTipo(TipoTransaccionFiltro.DEBITO) },
                        label = { Text("Débito") },
                    )
                    FilterChip(
                        selected = state.filtroTipo == TipoTransaccionFiltro.CREDITO,
                        onClick = { viewModel.setFiltroTipo(TipoTransaccionFiltro.CREDITO) },
                        label = { Text("Crédito") },
                    )
                }

                HorizontalDivider()
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && state.transacciones.isEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
                    items(10) { TransactionShimmerItem() }
                }
            } else if (grouped.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "Sin transacciones",
                    description = "Aún no tienes transacciones o ninguna coincide con tu búsqueda.",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (fecha, txs) ->
                        stickyHeader(key = fecha) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            ) {
                                Text(
                                    formatDateRelative(fecha),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(txs, key = { it.id }) { tx ->
                            val derivadas = viewModel.getDerivadasParaPadre(tx.id)
                            TransaccionItem(
                                tx = tx,
                                derivadas = derivadas,
                                onDeleteClick = { showDeleteDialogFor = tx },
                                onChangeCategoryClick = { showCategorySheetFor = tx },
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }

    // ── Diálogo de eliminación ─────────────────────────────────────────────────
    if (showDeleteDialogFor != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("Eliminar transacción") },
            text = { Text("¿Estás seguro de eliminar esta transacción? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eliminarTransaccion(showDeleteDialogFor!!)
                    showDeleteDialogFor = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) { Text("Cancelar") }
            },
        )
    }

    // ── Bottom sheet de recategorización ──────────────────────────────────────
    if (showCategorySheetFor != null) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheetFor = null },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Text("Cambiar Categoría", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = aplicarATodas, onCheckedChange = { aplicarATodas = it })
                    Text(
                        "Aplicar regla a todas las compras de '${showCategorySheetFor!!.descripcionOriginal}'",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(categoriaRegistry.values.toList()) { cat ->
                        Surface(
                            onClick = {
                                viewModel.recategorizar(showCategorySheetFor!!, cat.id, aplicarATodas)
                                showCategorySheetFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.size(16.dp).background(cat.color, CircleShape))
                                Spacer(Modifier.width(Spacing.md))
                                Text(cat.nombre, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Card de transacción ──────────────────────────────────────────────────────

@Composable
fun TransaccionItem(
    tx: Transaccion,
    derivadas: List<Transaccion> = emptyList(),
    onDeleteClick: () -> Unit,
    onChangeCategoryClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val cat = categoriaRegistry[tx.categoriaId]

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = !expanded },
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Icono de categoría
                    Surface(
                        shape = CircleShape,
                        color = cat?.color?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Category,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = cat?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))

                    Column(modifier = Modifier.weight(1f)) {
                        // Descripción
                        Text(
                            text = tx.descripcionOriginal,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))

                        // Badges: banco + categoría
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Badge banco
                            Surface(
                                color = bancoBadgeColor(tx.bancoCodigo),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = tx.bancoCodigo.take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = bancoTextColor(tx.bancoCodigo),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }

                            // Badge categoría
                            if (cat != null) {
                                Surface(
                                    color = cat.color.copy(alpha = 0.13f),
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(cat.color, CircleShape),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = cat.nombre,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cat.color,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Sin categorizar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(Spacing.sm))

                    // Monto
                    val sign = if (tx.tipo == TipoTransaccion.CREDITO) "+" else "-"
                    val color = if (tx.tipo == TipoTransaccion.CREDITO) Income else Expense
                    Text(
                        text = "$sign ${formatMoney(tx.monto)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }

                // Sección expandida
                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = Spacing.md, start = 56.dp)) {
                        Text("Banco: ${tx.bancoCodigo}", style = MaterialTheme.typography.bodySmall)
                        Text("Referencia: ${tx.referencia ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedButton(onClick = onChangeCategoryClick, shape = RoundedCornerShape(8.dp)) {
                                Text("Cambiar categoría")
                            }
                            IconButton(onClick = onDeleteClick) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Retenciones DGII
        if (derivadas.isNotEmpty()) {
            derivadas.forEach { derivada ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, end = Spacing.md, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = derivada.descripcionCorta.ifBlank { "Retención DGII" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "- ${formatMoney(derivada.monto)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Expense,
                    )
                }
            }
        }
    }
}

// ─── Colores de badges de banco ───────────────────────────────────────────────

private fun bancoBadgeColor(codigo: String): Color = when (codigo.uppercase()) {
    "BANRESERVAS" -> BancoBanReservas
    "POPULAR"     -> BancoPopular
    "QIK"         -> BancoQik
    "CIBAO"       -> BancoCibao
    else          -> Color(0xFF64748B)
}

private fun bancoTextColor(codigo: String): Color = when (codigo.uppercase()) {
    "QIK" -> Color(0xFF0B1220)
    else  -> Color.White
}
