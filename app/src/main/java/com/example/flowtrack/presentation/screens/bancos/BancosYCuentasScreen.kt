package com.example.flowtrack.presentation.screens.bancos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.presentation.components.BankLogo
import com.example.flowtrack.ui.theme.Spacing
import com.example.flowtrack.ui.theme.TabularNumber

private val BANCOS_DISPONIBLES_CRUD = listOf("BANRESERVAS", "POPULAR", "QIK", "CIBAO", "BHD", "OTRO")

@Composable
private fun StableDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BancosYCuentasScreen(
    onNavigateBack: () -> Unit,
    fromSidebar: Boolean = false,
    onDrawerReopen: () -> Unit = {},
    viewModel: BancosYCuentasViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    var mostrarSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val volver = {
        onNavigateBack()
        if (fromSidebar) {
            onDrawerReopen()
        }
    }

    BackHandler(onBack = volver)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Bancos y Cuentas", fontWeight = FontWeight.SemiBold) },
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
            ) { Icon(Icons.Outlined.Add, "Agregar cuenta") }
        },
    ) { padding ->
        when (val s = estado) {
            is BancosEstado.Cargando -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            is BancosEstado.Vacio -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Sin cuentas vinculadas", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Importa un estado de cuenta o agrega una cuenta manual",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is BancosEstado.ConDatos -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(s.cuentas, key = { it.id }) { cuenta ->
                    CuentaCard(cuenta, onEliminar = { viewModel.eliminarCuenta(cuenta.id) })
                }
            }

            is BancosEstado.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(s.mensaje, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (mostrarSheet) {
        ModalBottomSheet(
            onDismissRequest = { mostrarSheet = false },
            sheetState = sheetState,
        ) {
            NuevaCuentaSheet(
                onGuardar = { alias, banco, numero, tipo, moneda ->
                    viewModel.guardarCuenta(alias, banco, numero, tipo, moneda)
                    mostrarSheet = false
                },
                onCancelar = { mostrarSheet = false },
            )
        }
    }
}

@Composable
private fun NuevaCuentaSheet(
    onGuardar: (alias: String, banco: String, numero: String, tipo: TipoCuenta, moneda: Moneda) -> Unit,
    onCancelar: () -> Unit,
) {
    var alias by remember { mutableStateOf("") }
    var bancoSeleccionado by remember { mutableStateOf(BANCOS_DISPONIBLES_CRUD.first()) }
    var numero by remember { mutableStateOf("") }
    var tipoSeleccionado by remember { mutableStateOf(TipoCuenta.CORRIENTE) }
    var monedaSeleccionada by remember { mutableStateOf(Moneda.DOP) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Nueva cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text("Alias (ej: Cuenta nómina)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )

        StableDropdown(
            label = "Banco",
            selected = bancoSeleccionado,
            options = BANCOS_DISPONIBLES_CRUD,
            onSelected = { bancoSeleccionado = it },
        )

        OutlinedTextField(
            value = numero,
            onValueChange = { numero = it },
            label = { Text("Últimos dígitos (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        StableDropdown(
            label = "Tipo de cuenta",
            selected = tipoSeleccionado.name,
            options = TipoCuenta.entries.map { it.name },
            onSelected = { tipoSeleccionado = TipoCuenta.valueOf(it) },
        )

        StableDropdown(
            label = "Moneda",
            selected = monedaSeleccionada.name,
            options = Moneda.entries.map { it.name },
            onSelected = { monedaSeleccionada = Moneda.valueOf(it) },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                onClick = {
                    if (alias.isNotBlank()) {
                        onGuardar(alias.trim(), bancoSeleccionado, numero.trim(), tipoSeleccionado, monedaSeleccionada)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = alias.isNotBlank(),
            ) { Text("Guardar") }
        }
    }
}

@Composable
private fun CuentaCard(cuenta: Cuenta, onEliminar: () -> Unit) {
    var menuExpandido by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BankLogo(bancoCodigo = cuenta.bancoCodigo)
            Column(Modifier.weight(1f)) {
                Text(cuenta.alias, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (cuenta.numeroCuenta == "MANUAL") "Manual" else "****${cuenta.numeroCuenta.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cuenta.balanceActual?.let { balance ->
                Text(
                    text = formatMoney(balance),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TabularNumber,
                )
            }
            Box {
                IconButton(onClick = { menuExpandido = true }) {
                    Icon(Icons.Outlined.MoreVert, "Opciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
