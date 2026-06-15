package com.example.flowtrack.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtrack.presentation.model.FiltroPeriodo
import com.example.flowtrack.presentation.model.FiltrosAvanzadosState
import com.example.flowtrack.presentation.model.PeriodoState
import com.example.flowtrack.presentation.model.RangoMonto
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FiltrosSheet(
    state: FiltrosAvanzadosState,
    onAplicar: (FiltrosAvanzadosState) -> Unit,
    onLimpiar: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draftBanco by remember { mutableStateOf(state.bancoId) }
    var draftMontoMin by remember { mutableStateOf(state.rangoMonto.minimo?.toPlainString() ?: "") }
    var draftMontoMax by remember { mutableStateOf(state.rangoMonto.maximo?.toPlainString() ?: "") }
    var draftCategorias by remember { mutableStateOf(state.categorias) }
    var draftSoloSinCat by remember { mutableStateOf(state.soloSinCategorizar) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabecera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.md, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar filtros", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "Filtros avanzados",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLimpiar) {
                Text("Limpiar todo", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Contenido scrollable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Banco ─────────────────────────────────────────────────────────
            Text(
                "Banco",
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BancoFiltroRow(
                nombre = "Todos los bancos",
                seleccionado = draftBanco == null,
                onClick = { draftBanco = null },
            )
            state.bancosDisponibles.forEach { banco ->
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = Spacing.xl))
                BancoFiltroRow(
                    nombre = bancoPorCodigo(banco).nombre,
                    seleccionado = draftBanco == banco,
                    bancoColor = bancoPorCodigo(banco).color,
                    onClick = { draftBanco = banco },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(top = Spacing.sm))

            // ── Monto ─────────────────────────────────────────────────────────
            Text(
                "Rango de monto (DOP)",
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = draftMontoMin,
                    onValueChange = { draftMontoMin = it },
                    label = { Text("Desde", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                OutlinedTextField(
                    value = draftMontoMax,
                    onValueChange = { draftMontoMax = it },
                    label = { Text("Hasta", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ── Categorías ────────────────────────────────────────────────────
            Text(
                "Categorías",
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                categoriaRegistry.values.forEach { cat ->
                    val selected = cat.id in draftCategorias
                    FilterChip(
                        selected = selected,
                        onClick = {
                            draftSoloSinCat = false
                            draftCategorias = if (selected) draftCategorias - cat.id else draftCategorias + cat.id
                        },
                        label = { Text(cat.nombre, fontSize = 12.sp) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ── Solo sin categorizar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { draftSoloSinCat = !draftSoloSinCat; if (draftSoloSinCat) draftCategorias = emptySet() }
                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Solo sin categorizar", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Mostrar solo transacciones sin categoría asignada", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = draftSoloSinCat,
                    onCheckedChange = { draftSoloSinCat = it; if (it) draftCategorias = emptySet() },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(Modifier.height(Spacing.sm))
        }

        // Botón Aplicar (fuera del scroll)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Button(
            onClick = {
                onAplicar(
                    state.copy(
                        bancoId = draftBanco,
                        rangoMonto = RangoMonto(
                            minimo = draftMontoMin.trim().toBigDecimalOrNull(),
                            maximo = draftMontoMax.trim().toBigDecimalOrNull()
                        ),
                        categorias = draftCategorias,
                        soloSinCategorizar = draftSoloSinCat
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Aplicar filtros", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PeriodoDropdown(
    state: PeriodoState,
    onPeriodoSelected: (FiltroPeriodo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val opciones = FiltroPeriodo.entries

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Text(state.seleccionado.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opciones.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                opt.label, fontSize = 14.sp,
                                fontWeight = if (opt == state.seleccionado) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (opt == state.seleccionado) {
                                Icon(
                                    Icons.Outlined.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    onClick = { onPeriodoSelected(opt); expanded = false },
                )
            }
        }
    }
}

@Composable
fun BancoFiltroRow(
    nombre: String,
    seleccionado: Boolean,
    bancoColor: Color = MaterialTheme.colorScheme.outline,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (seleccionado) bancoColor else Color.Transparent, CircleShape)
                .border(1.5.dp, if (seleccionado) bancoColor else MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (seleccionado) {
            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun DesignPill(
    label: String,
    active: Boolean,
    trailingIcon: Boolean = false,
    closeIcon: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = contentColor)
        when {
            closeIcon -> Icon(Icons.Outlined.Close, null, tint = contentColor, modifier = Modifier.size(12.dp))
            trailingIcon -> Icon(Icons.Outlined.KeyboardArrowDown, null, tint = contentColor, modifier = Modifier.size(12.dp))
        }
    }
}
