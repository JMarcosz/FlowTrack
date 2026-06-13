package com.example.flowtrack.presentation.screens.reglas

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.ReglaSugerida
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.presentation.components.categoriaRegistry
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReglasScreen(
    navController: NavController,
    viewModel: ReglasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCategorySheetFor by remember { mutableStateOf<ReglaSugerida?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reglas de Categorización", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tabSeleccionado) {
                Tab(
                    selected = state.tabSeleccionado == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("Mis reglas") },
                )
                Tab(
                    selected = state.tabSeleccionado == 1,
                    onClick = { viewModel.setTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Sugeridas")
                            if (state.sugerencias.isNotEmpty()) {
                                Badge { Text("${state.sugerencias.size}") }
                            }
                        }
                    },
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            when (state.tabSeleccionado) {
                0 -> MisReglasTab(
                    reglas = state.reglasPersonales,
                    onEliminar = { viewModel.eliminarRegla(it) },
                )
                else -> SugeridasTab(
                    sugerencias = state.sugerencias,
                    onAceptar = { showCategorySheetFor = it },
                    onRechazar = { viewModel.rechazarSugerencia(it) },
                )
            }
        }
    }

    if (showCategorySheetFor != null) {
        ModalBottomSheet(onDismissRequest = { showCategorySheetFor = null }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Text(
                    "Categoría para '${showCategorySheetFor!!.patronDetectado}'",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(categoriaRegistry.values.toList()) { cat ->
                        Surface(
                            onClick = {
                                viewModel.aceptarSugerencia(showCategorySheetFor!!, cat.id)
                                showCategorySheetFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.size(16.dp).background(cat.color, CircleShape))
                                Spacer(Modifier.width(Spacing.md))
                                Text(cat.nombre, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MisReglasTab(reglas: List<ReglaCategoria>, onEliminar: (String) -> Unit) {
    if (reglas.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sin reglas creadas", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Las reglas se crean al cambiar categorías en tus transacciones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reglas, key = { it.id }) { regla ->
            ReglaCard(regla, onEliminar = { onEliminar(regla.id) })
        }
    }
}

@Composable
private fun ReglaCard(regla: ReglaCategoria, onEliminar: () -> Unit) {
    val catInfo = categoriaRegistry[regla.categoriaId]
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (catInfo != null) {
                Box(Modifier.size(12.dp).background(catInfo.color, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text(regla.patron, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${tipoMatchLabel(regla.tipoMatch)} · ${catInfo?.nombre ?: regla.categoriaId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${regla.confianza}×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            IconButton(onClick = onEliminar, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SugeridasTab(
    sugerencias: List<ReglaSugerida>,
    onAceptar: (ReglaSugerida) -> Unit,
    onRechazar: (ReglaSugerida) -> Unit,
) {
    if (sugerencias.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("¡Todo al día!", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("No hay sugerencias pendientes de revisión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sugerencias, key = { it.id }) { sug ->
            SugerenciaCard(sug, onAceptar = { onAceptar(sug) }, onRechazar = { onRechazar(sug) })
        }
    }
}

@Composable
private fun SugerenciaCard(
    sugerencia: ReglaSugerida,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text(sugerencia.patronDetectado, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${sugerencia.muestras.size} transacción(es) · confianza ${"%.0f".format(sugerencia.confianzaCluster)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRechazar, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ignorar")
                }
                Spacer(Modifier.width(Spacing.sm))
                Button(onClick = onAceptar, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Asignar")
                }
            }
        }
    }
}

private fun tipoMatchLabel(tipo: TipoMatch) = when (tipo) {
    TipoMatch.EXACTO -> "Exacto"
    TipoMatch.CONTIENE -> "Contiene"
    TipoMatch.EMPIEZA_CON -> "Empieza con"
    TipoMatch.REGEX -> "Regex"
}
