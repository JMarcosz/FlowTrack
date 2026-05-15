package com.example.flowtrack.presentation.screens.conversor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversorScreen(viewModel: ConversorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversor Divisas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else if (state.tasa != null) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    
                    // Indicador de tasas del día
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text("Tasa del Día (${state.tasa!!.fuente})", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(Spacing.sm))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Compra", style = MaterialTheme.typography.bodySmall)
                                    Text("RD$ ${state.tasa!!.compra}", fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Venta", style = MaterialTheme.typography.bodySmall)
                                    Text("RD$ ${state.tasa!!.venta}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.xl))

                    val labelOrigen = if (state.direccionDopAUsd) "Monto en DOP (RD$)" else "Monto en USD (US$)"
                    val labelDestino = if (state.direccionDopAUsd) "Equivalente en USD" else "Equivalente en DOP"
                    
                    OutlinedTextField(
                        value = state.montoEntrada,
                        onValueChange = { viewModel.setMonto(it) },
                        label = { Text(labelOrigen) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(Spacing.md))

                    IconButton(
                        onClick = { viewModel.invertirDireccion() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Invertir dirección")
                    }

                    Spacer(Modifier.height(Spacing.md))

                    val result = viewModel.calcularResultado()
                    val formatter = NumberFormat.getNumberInstance(Locale("en", "US")).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }
                    val prefix = if (state.direccionDopAUsd) "US$ " else "RD$ "
                    
                    Text(
                        text = "$prefix${formatter.format(result)}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(labelDestino, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
