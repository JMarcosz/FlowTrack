package com.example.flowtrack.presentation.screens.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ConfiguracionScreen(
    navController: NavController,
    viewModel: ConfiguracionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Usuario"
    val email = user?.email ?: ""

    LaunchedEffect(state.error, state.exito) {
        if (state.error != null || state.exito != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMensajes()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Title ─────────────────────────────────────────────
            Text(
                "Configuración",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.xl),
            )

            // ── Profile row ───────────────────────────────────────
            Card(
                shape = Radii.lg,
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .border(1.dp, Line2, Radii.lg)
                    .clickable { navController.navigate(Screen.Perfil.route) },
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
                        Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(email, fontSize = 13.sp, color = Muted)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted2)
                }
            }

            // Feedback banners
            if (state.error != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                    shape = Radii.md,
                ) {
                    Text(state.error!!, modifier = Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (state.exito != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                    shape = Radii.md,
                ) {
                    Text(state.exito!!, modifier = Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(Modifier.height(Spacing.xxl))

            // ── Section: Cuentas y datos ──────────────────────────
            SectionLabel("Cuentas y datos")
            Spacer(Modifier.height(Spacing.sm))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.AccountBalance,
                    label = "Bancos y cuentas",
                    onClick = { navController.navigate(Screen.BancosYCuentas.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Outlined.Category,
                    label = "Categorías",
                    onClick = { navController.navigate(Screen.Categorias.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Outlined.Upload,
                    label = "Importaciones",
                    onClick = { navController.navigate(Screen.Historial.route) },
                )
            }

            Spacer(Modifier.height(Spacing.xxl))

            // ── Section: Preferencias ─────────────────────────────
            SectionLabel("Preferencias")
            Spacer(Modifier.height(Spacing.sm))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Notifications,
                    label = "Notificaciones",
                    onClick = { navController.navigate(Screen.Notificaciones.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Outlined.CurrencyExchange,
                    label = "Tasas de cambio",
                    onClick = { navController.navigate(Screen.Conversor.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsSwitchRow(
                    icon = Icons.Default.DarkMode,
                    label = "Modo oscuro",
                    checked = state.config.temaOscuro,
                    onCheckedChange = { viewModel.toggleTema(it) },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Outlined.Savings,
                    label = "Presupuestos",
                    onClick = { navController.navigate(Screen.Presupuestos.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Outlined.Flag,
                    label = "Metas de ahorro",
                    onClick = { navController.navigate(Screen.Metas.route) },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Default.FileDownload,
                    label = "Exportar a Excel",
                    onClick = { viewModel.exportarDatosCsv() },
                    trailing = {
                        if (state.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted2)
                        }
                    },
                )
                HorizontalDivider(color = Line2)
                SettingsRow(
                    icon = Icons.Outlined.PictureAsPdf,
                    label = "Exportar a PDF",
                    onClick = { viewModel.exportarPdf() },
                    trailing = {
                        if (state.isExportingPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted2)
                        }
                    },
                )
            }

            Spacer(Modifier.height(Spacing.xxl))

            // ── Section: Cuenta ───────────────────────────────────
            SectionLabel("Cuenta")
            Spacer(Modifier.height(Spacing.sm))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Settings,
                    label = "Ajustes avanzados",
                    onClick = { navController.navigate(Screen.Ajustes.route) },
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

            // ── Footer ────────────────────────────────────────────
            Text(
                "FlowTrack v1.0.0",
                fontSize = 12.sp,
                color = Muted2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(label: String) {
    Text(
        label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Muted,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = 2.dp),
    )
}

// ── Settings card wrapper ─────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = Radii.lg,
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .border(1.dp, Line2, Radii.lg),
    ) {
        Column(content = content)
    }
}

// ── Settings row ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = Ink,
    iconColor: Color = Muted,
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
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted2)
        }
    }
}

// ── Settings switch row ───────────────────────────────────────────────────────

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
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = BgCard, checkedTrackColor = Primary),
        )
    }
}
