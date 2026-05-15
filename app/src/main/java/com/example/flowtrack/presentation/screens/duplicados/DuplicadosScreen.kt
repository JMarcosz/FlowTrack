package com.example.flowtrack.presentation.screens.duplicados

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.domain.model.TipoTransaccion

/**
 * Pantalla de detalle de duplicados detectados.
 * Muestra los pares de transacciones que podrían ser duplicados.
 * Sprint 3 — implementación básica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicadosScreen(
    navController: NavController,
    viewModel: DuplicadosViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()

    Scaffold(
        containerColor = Color(0xFFF4F6FA),
        topBar = {
            TopAppBar(
                title = { Text("Duplicados detectados", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F6FA)),
            )
        }
    ) { padding ->
        when (val s = estado) {
            is DuplicadosEstado.Vacio -> EmptyDuplicados(Modifier.padding(padding))
            is DuplicadosEstado.ConDatos -> DuplicadosContent(s.pares, Modifier.padding(padding))
        }
    }
}

@Composable
private fun EmptyDuplicados(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
            Text("No hay duplicados detectados", color = Color(0xFF64748B), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DuplicadosContent(pares: List<ParDuplicado>, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF7ED)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    Text(
                        "Estas transacciones ya existen en Firestore con el mismo ID determinístico. Se sobreescribirán de forma idempotente.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF92400E),
                    )
                }
            }
        }
        items(pares, key = { it.id }) { par ->
            DuplicadoCard(par)
        }
    }
}

@Composable
private fun DuplicadoCard(par: ParDuplicado) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Par duplicado", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), fontSize = 12.sp)
            HorizontalDivider(color = Color(0xFFE2E8F0))

            TransaccionMiniRow("Nueva", par.nueva)
            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
            TransaccionMiniRow("Existente", par.existente)
        }
    }
}

@Composable
private fun TransaccionMiniRow(etiqueta: String, tx: Transaccion) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.background(Color(0xFFEEF1F5), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        ) { Text(etiqueta, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold) }
        Column(Modifier.weight(1f)) {
            Text(tx.descripcionCorta, fontSize = 12.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
            Text(tx.fecha.toString(), fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
        val esCredito = tx.tipo == TipoTransaccion.CREDITO
        Text(
            "${if (esCredito) "+" else "-"} ${formatMoney(tx.monto)}",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (esCredito) Color(0xFF16A34A) else Color(0xFF0F172A),
        )
    }
}

data class ParDuplicado(val id: String, val nueva: Transaccion, val existente: Transaccion)
