package com.example.flowtrack.presentation.screens.categorias

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.presentation.components.CategoriaUI
import com.example.flowtrack.ui.theme.CatAlimentacion
import com.example.flowtrack.ui.theme.CatCompras
import com.example.flowtrack.ui.theme.CatOtros
import com.example.flowtrack.ui.theme.CatPagos
import com.example.flowtrack.ui.theme.CatServicios
import com.example.flowtrack.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasScreen(
    navController: NavController,
    viewModel: CategoriasViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Categorías", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Categoría", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    
                    if (state.categoriasPersonales.isNotEmpty()) {
                        item {
                            Text(
                                "Mis Categorías",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(Spacing.md)
                            )
                        }
                        items(state.categoriasPersonales) { cat ->
                            CategoriaItemRow(
                                categoria = cat,
                                canDelete = true,
                                onDeleteClick = { viewModel.eliminarCategoria(cat.id) }
                            )
                        }
                        item { HorizontalDivider(Modifier.padding(vertical = Spacing.md)) }
                    }

                    item {
                        Text(
                            "Categorías del Sistema",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(Spacing.md)
                        )
                    }

                    items(state.categoriasSistema) { cat ->
                        CategoriaItemRow(
                            categoria = cat,
                            canDelete = false,
                            onDeleteClick = {}
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        var nombre by remember { mutableStateOf("") }
        var colorElegido by remember { mutableStateOf(CatCompras) }
        val paleta = listOf(CatCompras, CatServicios, CatPagos, CatAlimentacion, CatOtros)

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Nueva Categoría") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text("Color:", style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        paleta.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(c, CircleShape)
                                    .clickable { colorElegido = c }
                                    .padding(4.dp)
                            ) {
                                if (colorElegido == c) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha=0.4f), CircleShape))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.crearCategoria(nombre, colorElegido)
                        showDialog = false
                    }
                ) { Text("Guardar", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}

@Composable
fun CategoriaItemRow(categoria: CategoriaUI, canDelete: Boolean, onDeleteClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(16.dp).background(categoria.color, CircleShape))
            Spacer(Modifier.width(Spacing.md))
            Text(categoria.nombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            
            if (canDelete) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
