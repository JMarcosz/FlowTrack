package com.example.flowtrack.presentation.screens.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatearFecha
import com.example.flowtrack.core.extensions.formatearMoneda
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.presentation.screens.configuracion.ConfiguracionViewModel
import com.example.flowtrack.ui.theme.BgScreen
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Spacing
import java.math.BigDecimal
import java.time.LocalDate

private val FORMATOS_FECHA = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy")
private val FORMATOS_MONEDA = listOf("RD$ 0.00", "0.00 RD$", "$0.00")
private val MONTO_MUESTRA = BigDecimal("1234.56")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesGeneralesScreen(
    navController: NavController,
    viewModel: ConfiguracionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val config = state.config

    var dialogo by remember { mutableStateOf<Dialogo?>(null) }

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes Generales", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Preview en vivo del formato aplicado.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text("Vista previa", style = MaterialTheme.typography.titleSmall, color = Primary)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        formatearMoneda(MONTO_MUESTRA, config.monedaPredeterminada, config.formatoMoneda),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        formatearFecha(LocalDate.now(), config.formatoFecha),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }

            PrefRow("Moneda base", config.monedaPredeterminada.name) { dialogo = Dialogo.MonedaBase }
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            PrefRow("Formato de fecha", config.formatoFecha) { dialogo = Dialogo.FormatoFecha }
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            PrefRow("Formato de moneda", config.formatoMoneda) { dialogo = Dialogo.FormatoMoneda }
        }
    }

    when (dialogo) {
        Dialogo.MonedaBase -> SeleccionDialog(
            titulo = "Moneda base",
            opciones = Moneda.entries.map { it.name },
            seleccionActual = config.monedaPredeterminada.name,
            onSeleccion = { viewModel.setMonedaBase(Moneda.valueOf(it)); dialogo = null },
            onDismiss = { dialogo = null },
        )
        Dialogo.FormatoFecha -> SeleccionDialog(
            titulo = "Formato de fecha",
            opciones = FORMATOS_FECHA,
            seleccionActual = config.formatoFecha,
            etiqueta = { formatearFecha(LocalDate.now(), it) },
            onSeleccion = { viewModel.setFormatoFecha(it); dialogo = null },
            onDismiss = { dialogo = null },
        )
        Dialogo.FormatoMoneda -> SeleccionDialog(
            titulo = "Formato de moneda",
            opciones = FORMATOS_MONEDA,
            seleccionActual = config.formatoMoneda,
            etiqueta = { formatearMoneda(MONTO_MUESTRA, config.monedaPredeterminada, it) },
            onSeleccion = { viewModel.setFormatoMoneda(it); dialogo = null },
            onDismiss = { dialogo = null },
        )
        null -> Unit
    }
}

private enum class Dialogo { MonedaBase, FormatoFecha, FormatoMoneda }

@Composable
private fun PrefRow(titulo: String, valor: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Text(valor, style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SeleccionDialog(
    titulo: String,
    opciones: List<String>,
    seleccionActual: String,
    etiqueta: (String) -> String = { it },
    onSeleccion: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                opciones.forEach { opcion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = opcion == seleccionActual,
                                onClick = { onSeleccion(opcion) },
                            )
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = opcion == seleccionActual, onClick = { onSeleccion(opcion) })
                        Spacer(Modifier.width(Spacing.sm))
                        Text(etiqueta(opcion))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
