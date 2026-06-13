package com.example.flowtrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.BgCard
import com.example.flowtrack.ui.theme.BgScreen
import com.example.flowtrack.ui.theme.Ink
import com.example.flowtrack.ui.theme.Line2
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Muted2
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Radii
import com.example.flowtrack.ui.theme.Spacing

private data class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val drawerItems = listOf(
    DrawerItem(Screen.Metas.route, "Metas de ahorro", Icons.Outlined.Flag),
    DrawerItem(Screen.Presupuestos.route, "Presupuestos", Icons.Outlined.Savings),
    DrawerItem(Screen.BancosYCuentas.route, "Bancos y cuentas", Icons.Outlined.AccountBalance),
    DrawerItem(Screen.Conversor.route, "Tasas de cambio", Icons.Outlined.CurrencyExchange),
)

@Composable
fun FlowTrackDrawer(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = BgScreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                text = "FlowTrack",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xxl, bottom = Spacing.md),
            )
            
            Spacer(Modifier.height(Spacing.xl))

            // Section Label
            SectionLabel("ACCESOS RÁPIDOS")
            Spacer(Modifier.height(Spacing.sm))

            // Menu Card
            Card(
                shape = Radii.lg,
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .border(1.dp, Line2, Radii.lg),
            ) {
                Column {
                    drawerItems.forEachIndexed { index, item ->
                        DrawerRow(
                            item = item,
                            isSelected = currentRoute == item.route,
                            onClick = { onNavigate(item.route) }
                        )
                        if (index < drawerItems.size - 1) {
                            HorizontalDivider(color = Line2)
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
        color = Muted,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = 2.dp),
    )
}

@Composable
private fun DrawerRow(
    item: DrawerItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val iconColor = if (isSelected) Primary else Muted
    val labelColor = if (isSelected) Primary else Ink
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Primary.copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(item.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Text(
            text = item.label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = if (isSelected) Primary else Muted2)
    }
}
