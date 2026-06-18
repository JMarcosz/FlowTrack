package com.example.flowtrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.flowtrack.R
import com.example.flowtrack.presentation.navigation.BancosYCuentasRoute
import com.example.flowtrack.presentation.navigation.ConversorRoute
import com.example.flowtrack.presentation.navigation.ExportarRoute
import com.example.flowtrack.presentation.navigation.HistorialRoute
import com.example.flowtrack.presentation.navigation.MetasRoute
import com.example.flowtrack.presentation.navigation.PresupuestosRoute
import com.example.flowtrack.presentation.navigation.SugerenciasRoute
import com.example.flowtrack.ui.theme.Radii
import com.example.flowtrack.ui.theme.Spacing

private data class DrawerItem(
    val labelResId: Int,
    val icon: ImageVector,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun FlowTrackDrawer(
    currentDestination: NavDestination?,
    onNavigateToMetas: () -> Unit,
    onNavigateToPresupuestos: () -> Unit,
    onNavigateToBancosYCuentas: () -> Unit,
    onNavigateToHistorial: () -> Unit,
    onNavigateToExportar: () -> Unit,
    onNavigateToSugerencias: () -> Unit,
    onNavigateToConversor: () -> Unit,
) {
    val drawerItems = listOf(
        DrawerItem(
            labelResId = R.string.drawer_metas_ahorro,
            icon = Icons.Outlined.Flag,
            isSelected = currentDestination?.hasRoute<MetasRoute>() == true,
            onClick = onNavigateToMetas,
        ),
        DrawerItem(
            labelResId = R.string.drawer_presupuestos,
            icon = Icons.Outlined.Savings,
            isSelected = currentDestination?.hasRoute<PresupuestosRoute>() == true,
            onClick = onNavigateToPresupuestos,
        ),
        DrawerItem(
            labelResId = R.string.drawer_bancos_cuentas,
            icon = Icons.Outlined.AccountBalance,
            isSelected = currentDestination?.hasRoute<BancosYCuentasRoute>() == true,
            onClick = onNavigateToBancosYCuentas,
        ),
        DrawerItem(
            labelResId = R.string.drawer_historial_importaciones,
            icon = Icons.Outlined.History,
            isSelected = currentDestination?.hasRoute<HistorialRoute>() == true,
            onClick = onNavigateToHistorial,
        ),
        DrawerItem(
            labelResId = R.string.drawer_exportar_estados,
            icon = Icons.Outlined.IosShare,
            isSelected = currentDestination?.hasRoute<ExportarRoute>() == true,
            onClick = onNavigateToExportar,
        ),
        DrawerItem(
            labelResId = R.string.drawer_sugerencias_limpieza,
            icon = Icons.Outlined.AutoAwesome,
            isSelected = currentDestination?.hasRoute<SugerenciasRoute>() == true,
            onClick = onNavigateToSugerencias,
        ),
        DrawerItem(
            labelResId = R.string.drawer_tasas_cambio,
            icon = Icons.Outlined.CurrencyExchange,
            isSelected = currentDestination?.hasRoute<ConversorRoute>() == true,
            onClick = onNavigateToConversor,
        ),
    )

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = Spacing.xl,
                    end = Spacing.xl,
                    top = Spacing.xxl,
                    bottom = Spacing.md,
                ),
            )

            Spacer(Modifier.height(Spacing.xl))

            SectionLabel(stringResource(R.string.drawer_section_rapidos))
            Spacer(Modifier.height(Spacing.sm))

            Card(
                shape = Radii.lg,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, Radii.lg),
            ) {
                Column {
                    drawerItems.forEachIndexed { index, item ->
                        DrawerRow(
                            item = item,
                            onClick = item.onClick,
                        )
                        if (index < drawerItems.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = 2.dp),
    )
}

@Composable
private fun DrawerRow(
    item: DrawerItem,
    onClick: () -> Unit,
) {
    val label = stringResource(item.labelResId)
    val iconColor = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (item.isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(item.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}
