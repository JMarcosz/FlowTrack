package com.example.flowtrack.presentation.screens.transacciones

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatDate
import com.example.flowtrack.core.extensions.formatDateRelative
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.presentation.components.CategoriaUI
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.presentation.components.categoriaRegistry
import com.example.flowtrack.presentation.components.TransactionShimmerItem
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransaccionesScreen(
    navController: NavController,
    onMenuClick: () -> Unit = {},
    viewModel: TransaccionesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val zona = remember { ZoneId.of("America/Santo_Domingo") }
    val grouped: Map<LocalDate, List<Transaccion>> = remember(state.transacciones) {
        state.transacciones
            .filter { !it.esDerivada }
            .groupBy { it.fecha.atZone(zona).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }
    val derivadasPorPadre: Map<String, List<Transaccion>> = remember(state.transacciones) {
        state.transacciones
            .filter { it.esDerivada }
            .groupBy { it.transaccionPadreId ?: "" }
    }

    var selectedTx by remember { mutableStateOf<Transaccion?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var aplicarATodas by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Crossfade(targetState = selectedTx, label = "tx_nav") { tx ->
            if (tx == null) {
                TransaccionesLista(
                    state = state,
                    grouped = grouped,
                    derivadasPorPadre = derivadasPorPadre,
                    onImport = { navController.navigate(Screen.Upload.route) },
                    onFiltroTipoChange = { viewModel.setFiltroTipo(it) },
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onPeriodo = { viewModel.seleccionarPeriodo(it) },
                    onAplicarFiltros = { banco, montoMin, montoMax, cats, soloSinCat ->
                        viewModel.aplicarFiltros(banco, montoMin, montoMax, cats, soloSinCat)
                    },
                    onLimpiarFiltros = { viewModel.limpiarFiltrosAvanzados() },
                    onLoadMore = { viewModel.cargarMas() },
                    onTxClick = { selectedTx = it },
                    onMenuClick = onMenuClick,
                )
            } else {
                TransaccionDetalle(
                    tx = tx,
                    derivadas = derivadasPorPadre[tx.id] ?: emptyList(),
                    onBack = { selectedTx = null },
                    onCambiarCategoria = { showCategorySheet = true },
                    onEliminar = { showDeleteDialog = true },
                )
            }
        }

        // ── Category bottom sheet ──────────────────────────────────────────────
        if (showCategorySheet && selectedTx != null) {
            val txForSheet = selectedTx!!
            ModalBottomSheet(
                onDismissRequest = { showCategorySheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(
                        "Cambiar categoría",
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { aplicarATodas = !aplicarATodas }
                            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = aplicarATodas,
                            onCheckedChange = { aplicarATodas = it },
                            colors = CheckboxDefaults.colors(checkedColor = Primary),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Aplicar a todas las compras similares",
                            fontSize = 14.sp,
                            color = TextBody,
                        )
                    }
                    HorizontalDivider(color = Line2, modifier = Modifier.padding(vertical = Spacing.xxs))
                    categoriaRegistry.values.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.recategorizar(txForSheet, cat.id, aplicarATodas)
                                    showCategorySheet = false
                                }
                                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            CategoriaCirculo(cat = cat, size = 36.dp)
                            Text(
                                cat.nombre,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (txForSheet.categoriaId == cat.id) {
                                Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Delete dialog ──────────────────────────────────────────────────────
        if (showDeleteDialog && selectedTx != null) {
            val txForDel = selectedTx!!
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Eliminar transacción", fontWeight = FontWeight.SemiBold, color = Ink) },
                text = { Text("Esta acción no se puede deshacer.", color = Muted, fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.eliminarTransaccion(txForDel)
                        showDeleteDialog = false
                        selectedTx = null
                    }) {
                        Text("Eliminar", color = Expense, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar", color = Muted)
                    }
                },
            )
        }
    }
}

// ─── Vista de lista ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TransaccionesLista(
    state: TransaccionesState,
    grouped: Map<LocalDate, List<Transaccion>>,
    derivadasPorPadre: Map<String, List<Transaccion>>,
    onImport: () -> Unit,
    onFiltroTipoChange: (TipoTransaccionFiltro) -> Unit,
    onSearchChange: (String) -> Unit,
    onPeriodo: (String) -> Unit,
    onAplicarFiltros: (String?, BigDecimal?, BigDecimal?, Set<String>, Boolean) -> Unit,
    onLimpiarFiltros: () -> Unit,
    onLoadMore: () -> Unit,
    onTxClick: (Transaccion) -> Unit,
    onMenuClick: () -> Unit,
) {
    var showFiltrosSheet by remember { mutableStateOf(false) }
    val filtrosSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(56.dp)) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Outlined.Menu, contentDescription = null, tint = Ink)
                }
            }
            Text(
                "Transacciones",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = Ink,
            )
            Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = onImport) {
                    Icon(Icons.Outlined.Upload, contentDescription = "Importar", tint = Ink)
                }
            }
        }

        // ── Selector de período ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Período",
                fontSize = 13.sp,
                color = Muted,
                fontWeight = FontWeight.Medium,
            )
            PeriodoDropdown(
                selected = state.periodo,
                options = PERIODOS_TRANSACCIONES,
                onSelect = onPeriodo,
            )
        }

        // ── Barra de búsqueda ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.md)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(1.dp, Line2, RoundedCornerShape(12.dp))
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                cursorBrush = SolidColor(Primary),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Ink),
                decorationBox = { inner ->
                    if (state.searchQuery.isEmpty()) {
                        Text("Buscar transacciones", fontSize = 14.sp, color = Muted2)
                    }
                    inner()
                },
            )
            if (state.searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchChange("") },
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Limpiar", tint = Muted, modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Pills de tipo + Filtros ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DesignPill("Todas", state.filtroTipo == TipoTransaccionFiltro.TODAS) { onFiltroTipoChange(TipoTransaccionFiltro.TODAS) }
            DesignPill("Ingresos", state.filtroTipo == TipoTransaccionFiltro.CREDITO) { onFiltroTipoChange(TipoTransaccionFiltro.CREDITO) }
            DesignPill("Gastos", state.filtroTipo == TipoTransaccionFiltro.DEBITO) { onFiltroTipoChange(TipoTransaccionFiltro.DEBITO) }
            Spacer(Modifier.weight(1f))
            val filtrosActivos = state.filtrosActivos
            val filtroLabel = if (filtrosActivos > 0) "$filtrosActivos filtro${if (filtrosActivos == 1) "" else "s"}" else "Filtros"
            DesignPill(
                label = filtroLabel,
                active = filtrosActivos > 0,
                trailingIcon = true,
                onClick = { showFiltrosSheet = true },
            )
        }

        // ── Bottom sheet de filtros avanzados ─────────────────────────────────
        if (showFiltrosSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFiltrosSheet = false },
                sheetState = filtrosSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                FiltrosSheet(
                    state = state,
                    onAplicar = { banco, montoMin, montoMax, cats, soloSinCat ->
                        onAplicarFiltros(banco, montoMin, montoMax, cats, soloSinCat)
                        showFiltrosSheet = false
                    },
                    onLimpiar = {
                        onLimpiarFiltros()
                        showFiltrosSheet = false
                    },
                    onDismiss = { showFiltrosSheet = false },
                )
            }
        }

        // ── Lista de transacciones ────────────────────────────────────────────
        when {
            state.isLoading && state.transacciones.isEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
                ) {
                    items(8) { TransactionShimmerItem() }
                }
            }
            state.error != null && grouped.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(Spacing.xl),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = Muted2,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            state.error,
                            fontSize = 14.sp,
                            color = Muted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        TextButton(onClick = onLoadMore) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            grouped.isEmpty() && !state.hasMore -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = Muted2,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text("Sin resultados", fontSize = 14.sp, color = Muted)
                    }
                }
            }
            else -> {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val layout = listState.layoutInfo
                        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                        layout.totalItemsCount > 0 &&
                            lastVisible >= layout.totalItemsCount - 4
                    }
                }

                LaunchedEffect(
                    shouldLoadMore,
                    state.isLoadingMore,
                    state.hasMore,
                    state.transacciones.size,
                    state.error,
                ) {
                    if (
                        shouldLoadMore &&
                        state.hasMore &&
                        !state.isLoadingMore &&
                        state.error == null
                    ) {
                        onLoadMore()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
                ) {
                    grouped.forEach { (fecha, txs) ->
                        item(key = "head_$fecha") {
                            Text(
                                text = formatDateRelative(fecha),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Muted,
                                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm, start = Spacing.xxs),
                            )
                        }
                        itemsIndexed(
                            items = txs,
                            key = { _, tx -> tx.id },
                        ) { index, tx ->
                            val shape = when {
                                txs.size == 1 -> RoundedCornerShape(16.dp)
                                index == 0 -> RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                )
                                index == txs.lastIndex -> RoundedCornerShape(
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp,
                                )
                                else -> RoundedCornerShape(0.dp)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, Line2, shape),
                            ) {
                                TransaccionFila(
                                    tx = tx,
                                    derivadas = derivadasPorPadre[tx.id] ?: emptyList(),
                                    onClick = { onTxClick(tx) },
                                )
                                if (index < txs.lastIndex) {
                                    HorizontalDivider(
                                        color = Line2,
                                        modifier = Modifier.padding(start = 68.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (state.hasMore || state.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.error != null) {
                                    TextButton(onClick = onLoadMore) {
                                        Text("Reintentar carga")
                                    }
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = Primary,
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

// ─── Fila de transacción ──────────────────────────────────────────────────────

@Composable
private fun TransaccionFila(
    tx: Transaccion,
    derivadas: List<Transaccion>,
    onClick: () -> Unit,
) {
    val cat = tx.categoriaId?.let { categoriaRegistry[it] }
    val isIncome = tx.tipo == TipoTransaccion.CREDITO

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CategoriaCirculo(cat = cat, size = 40.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.descripcionCorta.ifBlank { tx.descripcionOriginal }.take(40),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = cat?.nombre ?: "Sin categorizar",
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                text = if (isIncome) "+ ${formatMoney(tx.monto)}" else "- ${formatMoney(tx.monto)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isIncome) Income else Expense,
            )

            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Muted2,
                modifier = Modifier.size(16.dp),
            )
        }

        // Retenciones DGII
        if (derivadas.isNotEmpty()) {
            derivadas.forEach { derivada ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Line2.copy(alpha = 0.5f))
                        .padding(start = 68.dp, end = Spacing.xl, top = Spacing.xxs, bottom = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(Icons.Outlined.AccountBalance, null, tint = Muted2, modifier = Modifier.size(12.dp))
                    Text(
                        text = derivada.descripcionCorta.ifBlank { "Retención DGII" },
                        fontSize = 12.sp,
                        color = Muted2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "- ${formatMoney(derivada.monto)}",
                        fontSize = 12.sp,
                        color = Expense,
                    )
                }
            }
        }
    }
}

// ─── Vista de detalle ─────────────────────────────────────────────────────────

@Composable
private fun TransaccionDetalle(
    tx: Transaccion,
    derivadas: List<Transaccion>,
    onBack: () -> Unit,
    onCambiarCategoria: () -> Unit,
    onEliminar: () -> Unit,
) {
    val cat = tx.categoriaId?.let { categoriaRegistry[it] }
    val banco = bancoPorCodigo(tx.bancoCodigo)
    val isIncome = tx.tipo == TipoTransaccion.CREDITO
    val zona = remember { ZoneId.of("America/Santo_Domingo") }
    val fechaLocal = remember(tx.fecha) { tx.fecha.atZone(zona).toLocalDate() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
                .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(56.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = Ink)
                }
            }
            Text(
                "Detalle de transacción",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = Ink,
            )
            Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.MoreVert, null, tint = Ink)
                }
            }
        }

        // ── Contenido scrollable ──────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Card de encabezado
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Line2, RoundedCornerShape(16.dp))
                        .padding(Spacing.xxl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    CategoriaCirculo(cat = cat, size = 52.dp, fontSize = 18)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.descripcionOriginal,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = cat?.nombre ?: "Sin categorizar",
                            fontSize = 14.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = if (isIncome) "+ ${formatMoney(tx.monto)}" else "- ${formatMoney(tx.monto)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp,
                            color = if (isIncome) Income else Expense,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
            }

            // Card de filas de detalle
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Line2, RoundedCornerShape(16.dp)),
                ) {
                    DetalleRow("Fecha", formatDate(fechaLocal))
                    tx.fechaPosteo?.let { posteo ->
                        val fechaPosteoLocal = posteo.atZone(zona).toLocalDate()
                        if (fechaPosteoLocal != fechaLocal) {
                            HorizontalDivider(color = Line2)
                            DetalleRow("Fecha contable", formatDate(fechaPosteoLocal))
                        }
                    }
                    HorizontalDivider(color = Line2)
                    DetalleRow("Banco", banco.nombre)
                    if (tx.referencia != null) {
                        HorizontalDivider(color = Line2)
                        DetalleRow("Referencia", tx.referencia)
                    }
                    HorizontalDivider(color = Line2)
                    // Categoría como pill coloreada
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Categoría", fontSize = 14.sp, color = Muted)
                        val catColor = cat?.color ?: CatSinCategorizar
                        Box(
                            modifier = Modifier
                                .background(catColor.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                        ) {
                            Text(
                                cat?.nombre ?: "Sin categorizar",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = catColor,
                            )
                        }
                    }
                    if (tx.balanceDespues != null) {
                        HorizontalDivider(color = Line2)
                        DetalleRow("Balance posterior", "RD$ ${tx.balanceDespues.toPlainString()}")
                    }
                    if (derivadas.isNotEmpty()) {
                        derivadas.forEach { d ->
                            HorizontalDivider(color = Line2)
                            DetalleRow(
                                label = d.descripcionCorta.ifBlank { "Retención DGII" },
                                value = "- ${formatMoney(d.monto)}",
                                valueColor = Expense,
                            )
                        }
                    }
                }
            }

            // Botones de acción
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedButton(
                        onClick = onCambiarCategoria,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary100),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Primary,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Cambiar categoría", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onEliminar,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Expense50),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Expense,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Eliminar transacción", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Text(
                    "Esta acción eliminará la transacción permanentemente.",
                    fontSize = 12.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xxs),
                )
            }
        }
    }
}

// ─── Sheet de filtros avanzados ───────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FiltrosSheet(
    state: TransaccionesState,
    onAplicar: (String?, BigDecimal?, BigDecimal?, Set<String>, Boolean) -> Unit,
    onLimpiar: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draftBanco by remember { mutableStateOf(state.bancoFiltro) }
    var draftMontoMin by remember { mutableStateOf(state.montoMin?.toPlainString() ?: "") }
    var draftMontoMax by remember { mutableStateOf(state.montoMax?.toPlainString() ?: "") }
    var draftCategorias by remember { mutableStateOf(state.categoriasFiltro) }
    var draftSoloSinCat by remember { mutableStateOf(state.soloSinCategorizar) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabecera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.md, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Filtros avanzados",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLimpiar) {
                Text("Limpiar todo", color = Expense, fontSize = 13.sp)
            }
        }
        HorizontalDivider(color = Line2)

        // Contenido scrollable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Banco ─────────────────────────────────────────────────────────
            Text(
                "Banco",
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
            )
            BancoFiltroRow(
                nombre = "Todos los bancos",
                seleccionado = draftBanco == null,
                onClick = { draftBanco = null },
            )
            state.bancosDisponibles.forEach { banco ->
                HorizontalDivider(color = Line2, modifier = Modifier.padding(start = Spacing.xl))
                BancoFiltroRow(
                    nombre = bancoPorCodigo(banco).nombre,
                    seleccionado = draftBanco == banco,
                    bancoColor = bancoPorCodigo(banco).color,
                    onClick = { draftBanco = banco },
                )
            }

            HorizontalDivider(color = Line2, modifier = Modifier.padding(top = Spacing.sm))

            // ── Monto ─────────────────────────────────────────────────────────
            Text(
                "Rango de monto (DOP)",
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = draftMontoMin,
                    onValueChange = { draftMontoMin = it },
                    label = { Text("Desde", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Line,
                    ),
                )
                OutlinedTextField(
                    value = draftMontoMax,
                    onValueChange = { draftMontoMax = it },
                    label = { Text("Hasta", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Line,
                    ),
                )
            }

            HorizontalDivider(color = Line2)

            // ── Categorías ────────────────────────────────────────────────────
            Text(
                "Categorías",
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                categoriaRegistry.values.forEach { cat ->
                    val selected = cat.id in draftCategorias
                    FilterChip(
                        selected = selected,
                        onClick = {
                            draftSoloSinCat = false
                            draftCategorias = if (selected) draftCategorias - cat.id else draftCategorias + cat.id
                        },
                        label = { Text(cat.nombre, fontSize = 12.sp) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(alpha = 0.12f),
                            selectedLabelColor = Primary,
                            selectedLeadingIconColor = Primary,
                        ),
                    )
                }
            }

            HorizontalDivider(color = Line2)

            // ── Solo sin categorizar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { draftSoloSinCat = !draftSoloSinCat; if (draftSoloSinCat) draftCategorias = emptySet() }
                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Solo sin categorizar", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text("Mostrar solo transacciones sin categoría asignada", fontSize = 12.sp, color = Muted2)
                }
                Switch(
                    checked = draftSoloSinCat,
                    onCheckedChange = { draftSoloSinCat = it; if (it) draftCategorias = emptySet() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary),
                )
            }

            Spacer(Modifier.height(Spacing.sm))
        }

        // Botón Aplicar (fuera del scroll)
        HorizontalDivider(color = Line2)
        Button(
            onClick = {
                onAplicar(
                    draftBanco,
                    draftMontoMin.trim().toBigDecimalOrNull(),
                    draftMontoMax.trim().toBigDecimalOrNull(),
                    draftCategorias,
                    draftSoloSinCat,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text("Aplicar filtros", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Componentes internos ─────────────────────────────────────────────────────

@Composable
private fun DetalleRow(
    label: String,
    value: String,
    valueColor: Color = Ink,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = Muted)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
internal fun CategoriaCirculo(cat: CategoriaUI?, size: Dp, fontSize: Int = 14) {
    val color = cat?.color ?: CatSinCategorizar
    val initial = cat?.nombre?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
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
            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = Ink),
            border = BorderStroke(1.dp, Line),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Text(selected, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.width(4.dp))
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

@Composable
private fun DesignPill(
    label: String,
    active: Boolean,
    trailingIcon: Boolean = false,
    closeIcon: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (active) Color.White else TextBody
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Primary else Line2)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = contentColor)
        when {
            closeIcon -> Icon(Icons.Outlined.Close, null, tint = contentColor, modifier = Modifier.size(12.dp))
            trailingIcon -> Icon(Icons.Outlined.KeyboardArrowDown, null, tint = contentColor, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun BancoFiltroRow(
    nombre: String,
    seleccionado: Boolean,
    bancoColor: Color = Muted2,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (seleccionado) bancoColor else Color.Transparent, CircleShape)
                .border(1.5.dp, if (seleccionado) bancoColor else Muted2, CircleShape),
        )
        Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
        if (seleccionado) {
            Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
        }
    }
}
