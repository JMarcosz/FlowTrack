package com.example.flowtrack.presentation.screens.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    navController: NavController,
    onMenuClick: () -> Unit = {},
    viewModel: ConfiguracionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Configuración",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
        ) {
            Spacer(Modifier.height(Spacing.lg))

            // ── Perfil ────────────────────────────────────────────────────────
            ConfigSection(title = "Cuenta") {
                ConfigItem(
                    icon = Icons.Outlined.AccountCircle,
                    label = "Perfil",
                    onClick = { navController.navigate(Screen.Perfil.route) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                ConfigItem(
                    icon = Icons.Outlined.NotificationsNone,
                    label = "Notificaciones",
                    onClick = { navController.navigate(Screen.Notificaciones.route) },
                )
            }

            // ── Preferencias ──────────────────────────────────────────────────
            ConfigSection(title = "Preferencias") {
                ConfigItem(
                    icon = if (state.config.temaOscuro) Icons.Outlined.Brightness4 else Icons.Outlined.BrightnessHigh,
                    label = "Modo oscuro",
                    trailing = {
                        Switch(
                            checked = state.config.temaOscuro,
                            onCheckedChange = { viewModel.toggleTema(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                ConfigItem(
                    icon = Icons.Outlined.GTranslate,
                    label = "Idioma",
                    onClick = { showLanguageDialog = true },
                    trailing = {
                        Text(
                            text = if (state.config.idioma.startsWith("es")) "Español" else "English",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            // ── Datos ─────────────────────────────────────────────────────────
            ConfigSection(title = "Datos y Organización") {
                ConfigItem(
                    icon = Icons.Outlined.Category,
                    label = "Categorías",
                    onClick = { navController.navigate(Screen.Categorias.route) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                ConfigItem(
                    icon = Icons.Outlined.Rule,
                    label = "Reglas de categorización",
                    onClick = { navController.navigate(Screen.Reglas.route) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                ConfigItem(
                    icon = Icons.Outlined.Shield,
                    label = "Privacidad y seguridad",
                    onClick = { },
                )
            }

            // ── Soporte ───────────────────────────────────────────────────────
            ConfigSection(title = "FlowTrack") {
                ConfigItem(
                    icon = Icons.Outlined.HelpOutline,
                    label = "Ayuda y soporte",
                    onClick = { },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                ConfigItem(
                    icon = Icons.Outlined.Code,
                    label = "Versión",
                    trailing = {
                        Text(
                            "v1.0.5",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            // ── Acciones de Peligro ───────────────────────────────────────────
            Spacer(Modifier.height(Spacing.lg))
            ConfigSection(title = null) {
                ConfigItem(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    label = "Cerrar sesión",
                    labelColor = MaterialTheme.colorScheme.error,
                    iconColor = MaterialTheme.colorScheme.error,
                    onClick = { showLogoutDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                ConfigItem(
                    icon = Icons.Outlined.DeleteForever,
                    label = "Borrar mis datos",
                    labelColor = MaterialTheme.colorScheme.error,
                    iconColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteAccountDialog = true },
                )
            }

            Spacer(Modifier.height(Spacing.xxxl))
        }
    }

    // ── Dialogos ───────────────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro que deseas cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseAuth.getInstance().signOut()
                    showLogoutDialog = false
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Borrar todos mis datos", color = MaterialTheme.colorScheme.error) },
            text = { Text("Esta acción es irreversible. Se eliminarán todas tus transacciones, cuentas y metas permanentemente.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.borrarTodosMisDatos(); showDeleteAccountDialog = false },
                    enabled = !state.isDeleting,
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Eliminar permanentemente", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (showLanguageDialog) {
        // En v1 no tenemos setIdioma implementado todavia de forma reactiva completa en VM, pero podemos mockear el diálogo
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Seleccionar idioma") },
            text = {
                Column {
                    listOf("es-DO" to "Español", "en-US" to "English").forEach { (codigo, opcion) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageDialog = false }
                                .padding(vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(opcion, color = MaterialTheme.colorScheme.onSurface)
                            if (state.config.idioma == codigo) {
                                Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun ConfigSection(
    title: String?,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.sm),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun ConfigItem(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForwardIos,
                null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
