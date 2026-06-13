package com.example.flowtrack.presentation.screens.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatearFecha
import com.example.flowtrack.core.extensions.formatearMoneda
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Line2
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Primary50
import com.example.flowtrack.ui.theme.Radii
import com.example.flowtrack.ui.theme.Spacing
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

private val FORMATOS_FECHA = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy")
private val FORMATOS_MONEDA = listOf("RD$ 0.00", "0.00 RD$", "$0.00")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    navController: NavController,
    onMenuClick: () -> Unit = {},
    viewModel: ConfiguracionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Usuario"
    val email = user?.email ?: ""
    val config = state.config
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    LaunchedEffect(state.error, state.exito) {
        if (state.error != null || state.exito != null) {
            delay(3000.milliseconds)
            viewModel.clearMensajes()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                shape = Radii.lg,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .border(1.dp, Line2, Radii.lg),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Primary50),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            displayName.first().toString().uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl),
                    shape = Radii.md,
                ) {
                    Text(
                        state.error!!,
                        modifier = Modifier.padding(Spacing.md),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (state.exito != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl),
                    shape = Radii.md,
                ) {
                    Text(
                        state.exito!!,
                        modifier = Modifier.padding(Spacing.md),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel("Cuentas y datos")
            Spacer(Modifier.height(Spacing.sm))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Category,
                    label = "Categorías",
                    onClick = { navController.navigate(Screen.Categorias.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Outlined.History,
                    label = "Historial",
                    onClick = { navController.navigate(Screen.Historial.route) },
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
            SectionLabel("Preferencias")
            Spacer(Modifier.height(Spacing.sm))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Notifications,
                    label = "Notificaciones",
                    onClick = { navController.navigate(Screen.Notificaciones.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsSwitchRow(
                    icon = Icons.Filled.DarkMode,
                    label = "Modo oscuro",
                    checked = config.temaOscuro,
                    onCheckedChange = { viewModel.toggleTema(it) },
                )
                HorizontalDivider(color = Line2)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                ) {
                    Text(
                        "Ajustes generales",
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl),
                        shape = Radii.md,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text("Vista previa", style = MaterialTheme.typography.titleSmall, color = Primary)
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                formatearMoneda(state.balanceNeto, config.monedaPredeterminada, config.formatoMoneda),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                formatearFecha(LocalDate.now(), config.formatoFecha),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    PrefRow("Moneda base", config.monedaPredeterminada.name) { dialogo = Dialogo.MonedaBase }
                    HorizontalDivider(color = Line2, modifier = Modifier.padding(horizontal = Spacing.xl))
                    PrefRow("Formato de fecha", config.formatoFecha) { dialogo = Dialogo.FormatoFecha }
                    HorizontalDivider(color = Line2, modifier = Modifier.padding(horizontal = Spacing.xl))
                    PrefRow("Formato de moneda", config.formatoMoneda) { dialogo = Dialogo.FormatoMoneda }
                }
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Filled.FileDownload,
                    label = "Exportar estados",
                    onClick = { navController.navigate(Screen.Exportar.route) },
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
            SectionLabel("Cuenta")
            Spacer(Modifier.height(Spacing.sm))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.DeleteForever,
                    label = "Borrar todos mis datos",
                    labelColor = Expense,
                    iconColor = Expense,
                    onClick = { confirmarBorrado = true },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    label = "Cerrar sesión",
                    labelColor = Expense,
                    iconColor = Expense,
                    showChevron = false,
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    },
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
            Text(
                "FlowTrack v1.0.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl),
                textAlign = TextAlign.Center,
            )
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
            etiqueta = { formatearMoneda(state.balanceNeto, config.monedaPredeterminada, it) },
            onSeleccion = { viewModel.setFormatoMoneda(it); dialogo = null },
            onDismiss = { dialogo = null },
        )
        null -> Unit
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Borrar todos mis datos?") },
            text = {
                Text(
                    "Se eliminarán permanentemente todas tus cuentas, tarjetas, transacciones, " +
                        "movimientos, cargas e historial. Esta acción no se puede deshacer.\n\n" +
                        "Tu configuración y preferencias se conservarán.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmarBorrado = false
                        viewModel.borrarTodosMisDatos()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !state.isDeleting,
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text("Sí, borrar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrado = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private enum class Dialogo { MonedaBase, FormatoFecha, FormatoMoneda }

@Composable
private fun SectionLabel(label: String) {
    Text(
        label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = Radii.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .border(1.dp, Line2, Radii.lg),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, color = labelColor, modifier = Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.surface, checkedTrackColor = Primary),
        )
    }
}

@Composable
private fun PrefRow(titulo: String, valor: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
        title = { Text(titulo, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                opciones.forEach { opcion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeleccion(opcion) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = opcion == seleccionActual, onClick = { onSeleccion(opcion) })
                        Spacer(Modifier.width(Spacing.sm))
                        Text(etiqueta(opcion), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
