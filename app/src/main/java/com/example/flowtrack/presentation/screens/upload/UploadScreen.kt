package com.example.flowtrack.presentation.screens.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    navController: NavController,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()

    // Launcher para seleccionar archivo (PDF, CSV, XLS, XLSX)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.procesarArchivo(it) }
    }

    Scaffold(
        containerColor = Color(0xFFF4F6FA),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Importar estado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF4F6FA),
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = estado,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(240)) },
                label = "upload_estado",
            ) { estadoActual ->
                when (estadoActual) {
                    is UploadEstado.Idle -> UploadIdleContent(
                        onSeleccionarArchivo = {
                            filePicker.launch("*/*")
                        }
                    )

                    is UploadEstado.Procesando -> UploadProcesandoContent(
                        mensaje = estadoActual.mensaje,
                    )

                    is UploadEstado.Exito -> UploadExitoContent(
                        transaccionesInsertadas = estadoActual.transaccionesInsertadas,
                        banco = estadoActual.banco,
                        onNuevoArchivo = { viewModel.resetear() },
                        onVerHistorial = { navController.navigate("historial") },
                    )

                    is UploadEstado.Error -> UploadErrorContent(
                        mensaje = estadoActual.mensaje,
                        onReintentar = { viewModel.resetear() },
                    )
                }
            }
        }
    }
}

// ─── Estados de contenido ─────────────────────────────────────────────────────

@Composable
private fun UploadIdleContent(onSeleccionarArchivo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Drop zone
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFEAF1FE))
                .border(
                    width = 2.dp,
                    color = Color(0xFF2F6FED).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onSeleccionarArchivo),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF2F6FED).copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        tint = Color(0xFF2F6FED),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    "Toca para seleccionar archivo",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                )
                Text(
                    "PDF, CSV, XLS, XLSX — máx. 10 MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }
        }

        // Formatos soportados
        Text(
            "Bancos soportados",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF64748B),
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        BancosDisponiblesCard()
    }
}

@Composable
private fun BancosDisponiblesCard() {
    val bancos = listOf(
        Triple("BR", Color(0xFF0F5DAB), "BanReservas — PDF"),
        Triple("BP", Color(0xFF005DA4), "Popular — CSV"),
        Triple("QIK", Color(0xFFFFD200), "Qik — PDF"),
        Triple("AC", Color(0xFFE30613), "Cibao — XLS"),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        bancos.forEach { (abbr, color, nombre) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        abbr,
                        color = if (color == Color(0xFFFFD200)) Color(0xFF0B1220) else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                    )
                }
                Text(nombre, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF334155))
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // BHD "Próximamente"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF94A3B8), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("BHD", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
            }
            Text("BHD — Próximamente", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun UploadProcesandoContent(mensaje: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            color = Color(0xFF2F6FED),
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
        )
        Text(mensaje, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF334155))
    }
}

@Composable
private fun UploadExitoContent(
    transaccionesInsertadas: Int,
    banco: String,
    onNuevoArchivo: () -> Unit,
    onVerHistorial: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFE7F7EC), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            "¡Importación exitosa!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center,
        )

        Text(
            "$transaccionesInsertadas transacciones importadas desde $banco",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onVerHistorial,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ver historial", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = onNuevoArchivo,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF1F5)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Importar otro archivo", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UploadErrorContent(mensaje: String, onReintentar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFFDECEC), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Error,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            "Error al importar",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
        )

        Text(
            mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onReintentar,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reintentar", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Estado de la pantalla ────────────────────────────────────────────────────

sealed class UploadEstado {
    object Idle : UploadEstado()
    data class Procesando(val mensaje: String = "Procesando archivo...") : UploadEstado()
    data class Exito(val transaccionesInsertadas: Int, val banco: String) : UploadEstado()
    data class Error(val mensaje: String) : UploadEstado()
}
