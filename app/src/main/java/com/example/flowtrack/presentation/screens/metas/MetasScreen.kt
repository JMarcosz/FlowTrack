package com.example.flowtrack.presentation.screens.metas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.activity.compose.BackHandler
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.ui.theme.*

private val EMOJIS = listOf("🏠", "✈️", "🚗", "📱", "💍", "🎓", "🌴", "💻", "🏖️", "🎯", "💰", "🏋️")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetasScreen(
    navController: NavController,
    fromSidebar: Boolean = false,
    onDrawerReopen: () -> Unit = {},
    viewModel: MetasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var mostrarSheet by remember { mutableStateOf(false) }
    var metaParaDeposito by remember { mutableStateOf<Meta?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val volver = {
        navController.popBackStack()
        if (fromSidebar) {
            onDrawerReopen()
        }
    }

    BackHandler(onBack = volver)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Metas de ahorro", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = volver) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "Nueva meta") }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.metas.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🎯", fontSize = 48.sp)
                    Text("Sin metas aún", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Crea tu primera meta de ahorro",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.metas, key = { it.id }) { meta ->
                    MetaCard(
                        meta = meta,
                        onDepositar = { metaParaDeposito = meta },
                        onEliminar = { viewModel.eliminar(meta.id) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (mostrarSheet) {
        ModalBottomSheet(
            onDismissRequest = { mostrarSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            NuevaMetaSheet(
                onGuardar = { nombre, emoji, monto -> viewModel.guardar(nombre, emoji, monto); mostrarSheet = false },
                onCancelar = { mostrarSheet = false },
            )
        }
    }

    metaParaDeposito?.let { meta ->
        DepositoSheet(
            meta = meta,
            onDepositar = { monto -> viewModel.depositar(meta, monto); metaParaDeposito = null },
            onDismiss = { metaParaDeposito = null },
            sheetState = sheetState,
        )
    }
}

@Composable
private fun MetaCard(meta: Meta, onDepositar: () -> Unit, onEliminar: () -> Unit) {
    val ringColor = if (meta.completada) ExtendedTheme.colors.success else MaterialTheme.colorScheme.primary
    var confirmar by remember { mutableStateOf(false) }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Eliminar meta") },
            text = { Text("¿Eliminar la meta \"${meta.nombre}\"?") },
            confirmButton = { TextButton(onClick = { onEliminar(); confirmar = false }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
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
            // Anillo de progreso
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
                Text(meta.emoji, fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(meta.nombre, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatMoney(meta.montoActual)} / ${formatMoney(meta.montoObjetivo)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (meta.completada) {
                    Spacer(Modifier.height(4.dp))
                    Text("¡Meta completada! 🎉", fontSize = 11.sp, color = ExtendedTheme.colors.success, fontWeight = FontWeight.SemiBold)
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${"%.0f".format(meta.porcentaje * 100)}% completado",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!meta.completada) {
                    IconButton(onClick = onDepositar, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Add, "Depositar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = { confirmar = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositoSheet(
    meta: Meta,
    onDepositar: (java.math.BigDecimal) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    var monto by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Agregar a \"${meta.nombre}\"", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(
                value = monto,
                onValueChange = { monto = it },
                label = { Text("Monto a depositar (DOP)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
            )
            Button(
                onClick = { monto.trim().toBigDecimalOrNull()?.let { onDepositar(it) } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = monto.trim().toBigDecimalOrNull() != null,
            ) { Text("Agregar", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@Composable
private fun NuevaMetaSheet(
    onGuardar: (String, String, java.math.BigDecimal) -> Unit,
    onCancelar: () -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var emojiSel by remember { mutableStateOf(EMOJIS.first()) }
    var monto by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Nueva meta de ahorro", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre de la meta") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
        )

        OutlinedTextField(
            value = monto,
            onValueChange = { monto = it },
            label = { Text("Monto objetivo (DOP)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
        )

        Text("Elige un emoji", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EMOJIS) { emoji ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (emoji == emojiSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { emojiSel = emoji },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 22.sp) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Cancelar")
            }
            Button(
                onClick = { monto.trim().toBigDecimalOrNull()?.let { onGuardar(nombre, emojiSel, it) } },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = nombre.isNotBlank() && monto.trim().toBigDecimalOrNull() != null,
            ) { Text("Crear meta", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}
