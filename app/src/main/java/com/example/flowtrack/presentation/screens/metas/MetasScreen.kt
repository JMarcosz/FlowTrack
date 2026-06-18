package com.example.flowtrack.presentation.screens.metas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.R
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.CategoriaMeta
import com.example.flowtrack.domain.model.EstadoMeta
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.usecase.CuentaMetaDisponible
import kotlinx.coroutines.flow.collectLatest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

private const val ICONO_META_DEFAULT = "\uD83C\uDFAF"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetasScreen(
    onNavigateBack: () -> Unit,
    fromSidebar: Boolean = false,
    onDrawerReopen: () -> Unit = {},
    viewModel: MetasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mostrarNuevaMeta by remember { mutableStateOf(false) }
    var metaParaDeposito by remember { mutableStateOf<Meta?>(null) }
    var metaParaRetiro by remember { mutableStateOf<Meta?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MetasEvent.MostrarMensaje -> snackbarHostState.showSnackbar(event.mensaje)
            }
        }
    }

    val volver = {
        onNavigateBack()
        if (fromSidebar) onDrawerReopen()
    }

    BackHandler(onBack = volver)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.metas_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = volver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_volver),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarNuevaMeta = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.metas_cd_nueva_meta),
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.metas.isEmpty() -> MetasEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onNuevaMeta = { mostrarNuevaMeta = true },
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MetasResumenCard(metas = state.metas, cuentas = state.cuentasDisponibles)
                }
                items(state.metas, key = { it.id }) { meta ->
                    MetaCard(
                        meta = meta,
                        onDepositar = { metaParaDeposito = meta },
                        onRetirar = { metaParaRetiro = meta },
                        onCancelar = { viewModel.cancelar(meta.id) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (mostrarNuevaMeta) {
        ModalBottomSheet(
            onDismissRequest = { mostrarNuevaMeta = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            NuevaMetaSheet(
                cuentas = state.cuentasDisponibles,
                onGuardar = { nombre, monto, categoria, cuentaId, descripcion, fechaObjetivo ->
                    viewModel.guardar(
                        nombre = nombre,
                        emoji = ICONO_META_DEFAULT,
                        montoObjetivo = monto,
                        categoria = categoria,
                        cuentaId = cuentaId,
                        descripcion = descripcion,
                        fechaObjetivo = fechaObjetivo,
                    )
                    mostrarNuevaMeta = false
                },
                onCancelar = { mostrarNuevaMeta = false },
            )
        }
    }

    metaParaDeposito?.let { meta ->
        MovimientoMetaSheet(
            titulo = stringResource(R.string.metas_depositar_titulo, meta.nombre),
            accion = stringResource(R.string.metas_depositar_accion),
            meta = meta,
            cuentas = state.cuentasDisponibles,
            sheetState = sheetState,
            onConfirmar = { cuentaId, monto ->
                viewModel.depositar(meta, cuentaId, monto)
                metaParaDeposito = null
            },
            onDismiss = { metaParaDeposito = null },
        )
    }

    metaParaRetiro?.let { meta ->
        MovimientoMetaSheet(
            titulo = stringResource(R.string.metas_retirar_titulo, meta.nombre),
            accion = stringResource(R.string.metas_retirar_accion),
            meta = meta,
            cuentas = state.cuentasDisponibles,
            sheetState = sheetState,
            onConfirmar = { cuentaId, monto ->
                viewModel.retirar(meta, cuentaId, monto)
                metaParaRetiro = null
            },
            onDismiss = { metaParaRetiro = null },
        )
    }
}

@Composable
private fun MetasEmptyState(
    modifier: Modifier,
    onNuevaMeta: () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(ICONO_META_DEFAULT, fontSize = 48.sp)
            Text(
                text = stringResource(R.string.metas_empty_title),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.metas_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onNuevaMeta) {
                Text(stringResource(R.string.metas_empty_cta))
            }
        }
    }
}

@Composable
private fun MetasResumenCard(
    metas: List<Meta>,
    cuentas: List<CuentaMetaDisponible>,
) {
    val saldoEnMetas = metas
        .filter { it.activa }
        .fold(BigDecimal.ZERO.setScale(2)) { acc, meta -> acc + meta.montoActual }
    val saldoDisponible = cuentas.fold(BigDecimal.ZERO.setScale(2)) { acc, cuenta -> acc + cuenta.saldoDisponible }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResumenMonto(
                label = stringResource(R.string.metas_resumen_en_metas),
                value = formatMoney(saldoEnMetas),
                modifier = Modifier.weight(1f),
            )
            ResumenMonto(
                label = stringResource(R.string.metas_resumen_disponible),
                value = formatMoney(saldoDisponible),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ResumenMonto(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MetaCard(
    meta: Meta,
    onDepositar: () -> Unit,
    onRetirar: () -> Unit,
    onCancelar: () -> Unit,
) {
    var confirmar by remember { mutableStateOf(false) }
    val ringColor = when (meta.estado) {
        EstadoMeta.COMPLETADA -> MaterialTheme.colorScheme.secondary
        EstadoMeta.PAUSADA -> MaterialTheme.colorScheme.tertiary
        EstadoMeta.CANCELADA -> MaterialTheme.colorScheme.error
        EstadoMeta.ACTIVA -> MaterialTheme.colorScheme.primary
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text(stringResource(R.string.metas_cancelar_dialog_title)) },
            text = { Text(stringResource(R.string.metas_cancelar_dialog_text, meta.nombre)) },
            confirmButton = {
                TextButton(onClick = { onCancelar(); confirmar = false }) {
                    Text(stringResource(R.string.action_cancelar_meta), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) {
                    Text(stringResource(R.string.action_cerrar))
                }
            },
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 7.dp.toPx() / 2
                    drawArc(
                        color = ringColor.copy(alpha = 0.12f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * meta.porcentaje,
                        useCenter = false,
                        style = stroke,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                    )
                }
                Text(meta.emoji.ifBlank { ICONO_META_DEFAULT }, fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(meta.nombre, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatMoney(meta.montoActual)} / ${formatMoney(meta.montoObjetivo)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (meta.completada) {
                        stringResource(R.string.metas_estado_completada)
                    } else {
                        stringResource(R.string.metas_porcentaje_completado, "%.0f".format(meta.porcentaje * 100))
                    },
                    fontSize = 11.sp,
                    color = ringColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onDepositar, modifier = Modifier.size(36.dp), enabled = meta.estado != EstadoMeta.CANCELADA) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.metas_cd_depositar),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onRetirar, modifier = Modifier.size(36.dp), enabled = meta.montoActual > BigDecimal.ZERO) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = stringResource(R.string.metas_cd_retirar),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { confirmar = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_eliminar),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovimientoMetaSheet(
    titulo: String,
    accion: String,
    meta: Meta,
    cuentas: List<CuentaMetaDisponible>,
    sheetState: SheetState,
    onConfirmar: (String, BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    var monto by remember { mutableStateOf("") }
    var cuentaSeleccionada by remember(meta.id, cuentas) {
        mutableStateOf(cuentas.firstOrNull { it.cuenta.id == meta.cuentaId } ?: cuentas.firstOrNull())
    }
    val montoDecimal = monto.trim().toBigDecimalOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            CuentaDropdown(
                cuentas = cuentas,
                seleccionada = cuentaSeleccionada,
                onSeleccionar = { cuentaSeleccionada = it },
            )
            OutlinedTextField(
                value = monto,
                onValueChange = { monto = it },
                label = { Text(stringResource(R.string.metas_monto_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Button(
                onClick = {
                    val cuentaId = cuentaSeleccionada?.cuenta?.id
                    if (cuentaId != null && montoDecimal != null) onConfirmar(cuentaId, montoDecimal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = cuentaSeleccionada != null && montoDecimal != null && montoDecimal > BigDecimal.ZERO,
            ) {
                Text(accion, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuevaMetaSheet(
    cuentas: List<CuentaMetaDisponible>,
    onGuardar: (String, BigDecimal, CategoriaMeta, String?, String?, java.time.Instant?) -> Unit,
    onCancelar: () -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var fechaObjetivoTexto by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf(CategoriaMeta.OTRO) }
    var cuentaSeleccionada by remember(cuentas) { mutableStateOf(cuentas.firstOrNull()) }
    val montoDecimal = monto.trim().toBigDecimalOrNull()
    val fechaObjetivo = fechaObjetivoTexto.trim().takeIf { it.isNotBlank() }?.let {
        runCatching {
            LocalDate.parse(it)
                .atStartOfDay(ZoneId.of("America/Santo_Domingo"))
                .toInstant()
        }.getOrNull()
    }
    val fechaValida = fechaObjetivoTexto.isBlank() || fechaObjetivo != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.metas_nueva_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text(stringResource(R.string.metas_nombre_label)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = monto,
            onValueChange = { monto = it },
            label = { Text(stringResource(R.string.metas_objetivo_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
        CategoriaDropdown(seleccionada = categoria, onSeleccionar = { categoria = it })
        CuentaDropdown(
            cuentas = cuentas,
            seleccionada = cuentaSeleccionada,
            onSeleccionar = { cuentaSeleccionada = it },
        )
        OutlinedTextField(
            value = fechaObjetivoTexto,
            onValueChange = { fechaObjetivoTexto = it },
            label = { Text(stringResource(R.string.metas_fecha_objetivo_label)) },
            placeholder = { Text(stringResource(R.string.metas_fecha_objetivo_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = !fechaValida,
            singleLine = true,
        )
        OutlinedTextField(
            value = descripcion,
            onValueChange = { descripcion = it },
            label = { Text(stringResource(R.string.metas_descripcion_label)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCancelar,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.action_cancelar))
            }
            Button(
                onClick = {
                    if (montoDecimal != null) {
                        onGuardar(
                            nombre,
                            montoDecimal,
                            categoria,
                            cuentaSeleccionada?.cuenta?.id,
                            descripcion,
                            fechaObjetivo,
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = nombre.isNotBlank() &&
                    montoDecimal != null &&
                    montoDecimal > BigDecimal.ZERO &&
                    fechaValida,
            ) {
                Text(stringResource(R.string.metas_crear_accion), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriaDropdown(
    seleccionada: CategoriaMeta,
    onSeleccionar: (CategoriaMeta) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = seleccionada.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.metas_categoria_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CategoriaMeta.entries.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria.label()) },
                    onClick = {
                        onSeleccionar(categoria)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuentaDropdown(
    cuentas: List<CuentaMetaDisponible>,
    seleccionada: CuentaMetaDisponible?,
    onSeleccionar: (CuentaMetaDisponible) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded && cuentas.isNotEmpty() }) {
        OutlinedTextField(
            value = seleccionada?.let {
                "${it.cuenta.alias.ifBlank { it.cuenta.numeroCuenta }} - ${formatMoney(it.saldoDisponible)}"
            } ?: stringResource(R.string.metas_sin_cuentas),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.metas_cuenta_label)) },
            trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = cuentas.isNotEmpty(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cuentas.forEach { cuenta ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(cuenta.cuenta.alias.ifBlank { cuenta.cuenta.numeroCuenta })
                            Text(
                                text = stringResource(R.string.metas_saldo_disponible, formatMoney(cuenta.saldoDisponible)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSeleccionar(cuenta)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoriaMeta.label(): String = when (this) {
    CategoriaMeta.FONDO_EMERGENCIA -> stringResource(R.string.metas_categoria_emergencia)
    CategoriaMeta.VIAJES -> stringResource(R.string.metas_categoria_viajes)
    CategoriaMeta.VEHICULO -> stringResource(R.string.metas_categoria_vehiculo)
    CategoriaMeta.VIVIENDA -> stringResource(R.string.metas_categoria_vivienda)
    CategoriaMeta.EDUCACION -> stringResource(R.string.metas_categoria_educacion)
    CategoriaMeta.TECNOLOGIA -> stringResource(R.string.metas_categoria_tecnologia)
    CategoriaMeta.INVERSION -> stringResource(R.string.metas_categoria_inversion)
    CategoriaMeta.OTRO -> stringResource(R.string.metas_categoria_otro)
}
