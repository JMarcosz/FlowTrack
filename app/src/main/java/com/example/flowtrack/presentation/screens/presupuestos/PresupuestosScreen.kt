package com.example.flowtrack.presentation.screens.presupuestos

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Savings
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.PeriodoPresupuesto
import com.example.flowtrack.domain.usecase.PresupuestoConGasto
import com.example.flowtrack.presentation.components.categoriaRegistry
import com.example.flowtrack.ui.theme.CatSinCategorizar
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Ink
import com.example.flowtrack.ui.theme.Line
import com.example.flowtrack.ui.theme.Line2
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Muted2
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Success
import com.example.flowtrack.ui.theme.TextBody
import com.example.flowtrack.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresupuestosScreen(
    navController: NavController,
    viewModel: PresupuestosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var mostrarSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Presupuestos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                containerColor = Primary,
                contentColor = Color.White,
            ) { Icon(Icons.Outlined.Add, "Nuevo presupuesto") }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Primary) }

            state.presupuestos.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Savings, null, tint = Muted2, modifier = Modifier.size(56.dp))
                    Text("Sin presupuestos", fontWeight = FontWeight.SemiBold, color = TextBody)
                    Text(
                        "Crea un presupuesto para controlar tus gastos por categoría",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "${state.presupuestos.size} presupuesto${if (state.presupuestos.size == 1) "" else "s"} activo${if (state.presupuestos.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted2,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(state.presupuestos, key = { it.presupuesto.id }) { pg ->
                    PresupuestoCard(pg = pg, onEliminar = { viewModel.eliminar(pg.presupuesto.id) })
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
            NuevoPresupuestoSheet(
                onGuardar = { catId, monto, periodo ->
                    viewModel.guardar(catId, monto, periodo)
                    mostrarSheet = false
                },
                onCancelar = { mostrarSheet = false },
            )
        }
    }
}

@Composable
private fun PresupuestoCard(pg: PresupuestoConGasto, onEliminar: () -> Unit) {
    val cat = categoriaRegistry[pg.presupuesto.categoriaId]
    val catColor = cat?.color ?: CatSinCategorizar
    var confirmarEliminar by remember { mutableStateOf(false) }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            title = { Text("Eliminar presupuesto") },
            text = { Text("¿Eliminar el presupuesto de ${cat?.nombre ?: "esta categoría"}?") },
            confirmButton = {
                TextButton(onClick = { onEliminar(); confirmarEliminar = false }) {
                    Text("Eliminar", color = Expense)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") } },
        )
    }

    val barColor = when {
        pg.excedido        -> Expense
        pg.porcentaje > 0.7f -> Warning
        else               -> Success
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(catColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cat?.nombre?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = catColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(cat?.nombre ?: "Sin categorizar", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
                    Text(
                        pg.presupuesto.periodo.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = Muted2,
                    )
                }
                IconButton(onClick = { confirmarEliminar = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = Muted2, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { pg.porcentaje },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.12f),
            )

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatMoney(pg.gastoActual),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Expense,
                )
                Text(
                    "de ${formatMoney(pg.presupuesto.montoLimite)}",
                    fontSize = 13.sp,
                    color = Muted,
                )
            }

            if (pg.excedido) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Presupuesto excedido por ${formatMoney(pg.gastoActual - pg.presupuesto.montoLimite)}",
                    fontSize = 11.sp,
                    color = Expense,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuevoPresupuestoSheet(
    onGuardar: (String, java.math.BigDecimal, PeriodoPresupuesto) -> Unit,
    onCancelar: () -> Unit,
) {
    val categorias = categoriaRegistry.values.toList()
    var catSeleccionada by remember { mutableStateOf(categorias.firstOrNull()) }
    var monto by remember { mutableStateOf("") }
    var periodo by remember { mutableStateOf(PeriodoPresupuesto.MENSUAL) }
    var catExpandido by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Nuevo presupuesto", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)

        // Selector de categoría
        ExposedDropdownMenuBox(
            expanded = catExpandido,
            onExpandedChange = { catExpandido = it },
        ) {
            OutlinedTextField(
                value = catSeleccionada?.nombre ?: "Seleccionar categoría",
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoría") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpandido) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Line),
            )
            ExposedDropdownMenu(expanded = catExpandido, onDismissRequest = { catExpandido = false }) {
                categorias.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.nombre, fontSize = 14.sp) },
                        onClick = { catSeleccionada = cat; catExpandido = false },
                    )
                }
            }
        }

        // Monto límite
        OutlinedTextField(
            value = monto,
            onValueChange = { monto = it },
            label = { Text("Monto límite (DOP)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Line),
        )

        // Período
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodoPresupuesto.entries.forEach { p ->
                val sel = periodo == p
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Primary else Line2)
                        .clickable { periodo = p }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        p.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (sel) Color.White else TextBody,
                    )
                }
            }
        }

        // Botones
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCancelar,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Cancelar") }
            Button(
                onClick = {
                    val m = monto.trim().toBigDecimalOrNull() ?: return@Button
                    val cat = catSeleccionada ?: return@Button
                    onGuardar(cat.id, m, periodo)
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = monto.trim().toBigDecimalOrNull() != null && catSeleccionada != null,
            ) { Text("Guardar", fontWeight = FontWeight.SemiBold) }
        }
    }
}
