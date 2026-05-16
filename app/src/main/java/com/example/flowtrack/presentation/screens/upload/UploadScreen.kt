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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.flowtrack.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    navController: NavController,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val bancoSeleccionado by viewModel.bancoSeleccionado.collectAsState()

    val mimeTypes = arrayOf("application/pdf", "text/csv", "application/vnd.ms-excel", "*/*")
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.procesarArchivo(it) }
    }

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = {
                    Text("Importar estado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xl))

            AnimatedContent(
                targetState = estado,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(240)) },
                label = "upload_estado",
            ) { estadoActual ->
                when (estadoActual) {
                    is UploadEstado.Idle, is UploadEstado.Error -> {
                        UploadFormContent(
                            bancoSeleccionado = bancoSeleccionado,
                            errorMensaje = (estadoActual as? UploadEstado.Error)?.mensaje,
                            onSeleccionarBanco = { viewModel.seleccionarBanco(it) },
                            onSeleccionarArchivo = {
                                filePicker.launch("*/*")
                            },
                        )
                    }
                    is UploadEstado.Procesando -> UploadProcesandoContent(estadoActual.mensaje)
                    is UploadEstado.Exito -> UploadExitoContent(
                        transaccionesInsertadas = estadoActual.transaccionesInsertadas,
                        banco = estadoActual.banco,
                        onNuevoArchivo = { viewModel.resetear() },
                        onVerHistorial = { navController.navigate("historial") },
                    )
                }
            }
        }
    }
}

// ─── Formulario de selección ──────────────────────────────────────────────────

@Composable
private fun UploadFormContent(
    bancoSeleccionado: BancoOpcion?,
    errorMensaje: String?,
    onSeleccionarBanco: (BancoOpcion) -> Unit,
    onSeleccionarArchivo: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            "1. Selecciona tu banco",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )

        BANCOS_DISPONIBLES.forEach { banco ->
            val seleccionado = bancoSeleccionado?.codigo == banco.codigo
            BancoCard(
                banco = banco,
                seleccionado = seleccionado,
                onClick = { onSeleccionarBanco(banco) },
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            "2. Selecciona el archivo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (bancoSeleccionado != null) Primary50 else Color(0xFFEEF1F5))
                .border(
                    width = 2.dp,
                    color = if (bancoSeleccionado != null) Primary.copy(alpha = 0.4f) else Line,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(enabled = bancoSeleccionado != null, onClick = onSeleccionarArchivo),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    tint = if (bancoSeleccionado != null) Primary else Muted,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    if (bancoSeleccionado != null)
                        "Toca para seleccionar archivo ${bancoSeleccionado.formatoLabel}"
                    else
                        "Selecciona un banco primero",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bancoSeleccionado != null) Primary else Muted,
                    textAlign = TextAlign.Center,
                )
                if (bancoSeleccionado != null) {
                    Text(
                        "Máx. 10 MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }

        if (errorMensaje != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Expense50, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Error, contentDescription = null, tint = Expense, modifier = Modifier.size(20.dp))
                Text(errorMensaje, style = MaterialTheme.typography.bodySmall, color = Expense)
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun BancoCard(banco: BancoOpcion, seleccionado: Boolean, onClick: () -> Unit) {
    val disponible = banco.disponible
    val borderColor = when {
        seleccionado -> Primary
        disponible   -> Line
        else         -> Line.copy(alpha = 0.5f)
    }
    val bgColor = when {
        seleccionado -> Primary50
        disponible   -> BgCard
        else         -> BgCard.copy(alpha = 0.6f)
    }
    val bancoColor = when (banco.codigo) {
        "BANRESERVAS" -> BancoBanReservas
        "POPULAR"     -> BancoPopular
        "QIK"         -> BancoQik
        "CIBAO"       -> BancoCibao
        else          -> Color(0xFF94A3B8)
    }.let { if (disponible) it else it.copy(alpha = 0.4f) }
    val textoColor = if (banco.codigo == "QIK") Color(0xFF0B1220) else Color.White
    val abbr = when (banco.codigo) {
        "BANRESERVAS" -> "BR"
        "POPULAR"     -> "BP"
        "QIK"         -> "QIK"
        "CIBAO"       -> "AC"
        "BHD"         -> "BHD"
        else          -> banco.codigo.take(3)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.lg)
            .background(bgColor)
            .border(
                width = if (seleccionado) 2.dp else 1.dp,
                color = borderColor,
                shape = Radii.lg,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(bancoColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(abbr, color = textoColor, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                banco.nombre,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (disponible) Ink else Muted,
            )
            Text(
                if (disponible) banco.formatoLabel else "Próximamente",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        if (seleccionado) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun UploadProcesandoContent(mensaje: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = Primary, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
        Text(mensaje, style = MaterialTheme.typography.bodyLarge, color = TextBody)
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
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(Income50, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Income, modifier = Modifier.size(36.dp))
        }
        Text("¡Importación exitosa!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
        Text("$transaccionesInsertadas transacciones importadas desde $banco", style = MaterialTheme.typography.bodyLarge, color = TextBody, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onVerHistorial, colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = Radii.md, modifier = Modifier.fillMaxWidth()) {
            Text("Ver historial", fontWeight = FontWeight.SemiBold)
        }
        Button(onClick = onNuevoArchivo, colors = ButtonDefaults.buttonColors(containerColor = BgCard), shape = Radii.md, modifier = Modifier.fillMaxWidth()) {
            Text("Importar otro archivo", color = TextBody, fontWeight = FontWeight.SemiBold)
        }
    }
}
