package com.example.flowtrack.presentation.screens.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.BgScreen
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(navController: NavController) {
    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.SemiBold) },
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
            AjustesGroup("Preferencias") {
                AjusteRow(Icons.Outlined.Tune, "Ajustes Generales", "Formato de fecha, moneda y moneda base") {
                    navController.navigate(Screen.AjustesGenerales.route)
                }
            }

            AjustesGroup("Cuenta") {
                AjusteRow(Icons.Outlined.Person, "Perfil", "Nombre y foto de perfil") {
                    navController.navigate(Screen.Perfil.route)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                AjusteRow(Icons.Outlined.Notifications, "Notificaciones", "Alertas y recordatorios") {
                    navController.navigate(Screen.Notificaciones.route)
                }
            }

            AjustesGroup("Finanzas") {
                AjusteRow(Icons.Outlined.AccountBalance, "Bancos y Cuentas", "Ver y gestionar tus cuentas") {
                    navController.navigate(Screen.BancosYCuentas.route)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                AjusteRow(Icons.Outlined.CreditCard, "Tarjetas", "Ver y gestionar tus tarjetas") {
                    navController.navigate(Screen.Tarjetas.route)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                AjusteRow(Icons.Outlined.Category, "Categorías", "Administrar categorías personales") {
                    navController.navigate(Screen.Categorias.route)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                AjusteRow(Icons.AutoMirrored.Outlined.Rule, "Reglas", "Reglas de categorización automática") {
                    navController.navigate(Screen.Reglas.route)
                }
            }

            AjustesGroup("Importación") {
                AjusteRow(Icons.Outlined.History, "Historial", "Ver importaciones anteriores") {
                    navController.navigate(Screen.Historial.route)
                }
            }

            AjustesGroup("Avanzado") {
                AjusteRow(Icons.Outlined.DeleteForever, "Borrar todos mis datos", "Elimina cuentas, transacciones e historial") {
                    navController.navigate(Screen.Avanzado.route)
                }
            }
        }
    }
}

@Composable
private fun AjustesGroup(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
    Spacer(Modifier.height(Spacing.md))
}

@Composable
private fun AjusteRow(
    icon: ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
