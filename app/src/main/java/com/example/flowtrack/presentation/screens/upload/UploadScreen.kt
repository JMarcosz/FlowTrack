package com.example.flowtrack.presentation.screens.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.ui.theme.Expense
import com.example.flowtrack.ui.theme.Expense50
import com.example.flowtrack.ui.theme.Ink
import com.example.flowtrack.ui.theme.Line
import com.example.flowtrack.ui.theme.Line2
import com.example.flowtrack.ui.theme.Muted
import com.example.flowtrack.ui.theme.Primary
import com.example.flowtrack.ui.theme.Primary50
import com.example.flowtrack.ui.theme.Radii
import com.example.flowtrack.ui.theme.Spacing
import com.example.flowtrack.ui.theme.Success
import com.example.flowtrack.ui.theme.Success50
import com.example.flowtrack.ui.theme.TextBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    navController: NavController,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val bancoSeleccionado by viewModel.bancoSeleccionado.collectAsState()
    val dialogoClave by viewModel.dialogoClave.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.procesarArchivo(it) }
    }

    dialogoClave?.let { dialogo ->
        DocumentoProtegidoDialog(
            estado = dialogo,
            onDesbloquear = viewModel::desbloquearDocumento,
            onCancelar = viewModel::cancelarDesbloqueo,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
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
                            onFechaCorteChange = { viewModel.setFechaCorte(it) },
                            onFechaLimitePagoChange = { viewModel.setFechaLimitePago(it) },
                            onSeleccionarArchivo = { filePicker.launch("*/*") },
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

@Composable
private fun DocumentoProtegidoDialog(
    estado: DialogoClaveEstado,
    onDesbloquear: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    var clave by remember { mutableStateOf("") }
    var mostrarClave by remember { mutableStateOf(false) }

    fun confirmar() {
        if (estado.procesando) return
        val claveActual = clave
        clave = ""
        onDesbloquear(claveActual)
    }

    AlertDialog(
        onDismissRequest = {
            clave = ""
            if (!estado.procesando) onCancelar()
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = Primary,
            )
        },
        title = {
            Text(
                text = "Documento protegido",
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "Este archivo está cifrado. Ingresa la clave para continuar con la importación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !estado.procesando,
                    singleLine = true,
                    label = { Text("Clave del documento") },
                    isError = estado.errorMensaje != null,
                    supportingText = estado.errorMensaje?.let { mensaje ->
                        { Text(mensaje) }
                    },
                    visualTransformation = if (mostrarClave) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirmar() }),
                    trailingIcon = {
                        IconButton(
                            onClick = { mostrarClave = !mostrarClave },
                            enabled = !estado.procesando,
                        ) {
                            Icon(
                                imageVector = if (mostrarClave) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (mostrarClave) {
                                    "Ocultar clave"
                                } else {
                                    "Mostrar clave"
                                },
                            )
                        }
                    },
                )
                if (estado.procesando) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Desbloqueando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::confirmar,
                enabled = clave.isNotBlank() && !estado.procesando,
            ) {
                Text("Desbloquear")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    clave = ""
                    onCancelar()
                },
                enabled = !estado.procesando,
            ) {
                Text("Cancelar")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

// ─── Formulario de selección ──────────────────────────────────────────────────

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun parseFecha(texto: String): LocalDate? = try {
    LocalDate.parse(texto.trim(), DATE_FORMATTER)
} catch (_: DateTimeParseException) { null }

@Composable
private fun UploadFormContent(
    bancoSeleccionado: BancoOpcion?,
    errorMensaje: String?,
    onSeleccionarBanco: (BancoOpcion) -> Unit,
    onFechaCorteChange: (LocalDate?) -> Unit,
    onFechaLimitePagoChange: (LocalDate?) -> Unit,
    onSeleccionarArchivo: () -> Unit,
) {
    val esTarjeta = bancoSeleccionado?.productoTipo == ProductoTipo.TARJETA
    val pasoArchivo = if (esTarjeta) "3" else "2"

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

        // Sección de fechas — solo visible cuando el banco es de tarjeta de crédito
        AnimatedVisibility(
            visible = esTarjeta,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TarjetaFechasSection(
                onFechaCorteChange = onFechaCorteChange,
                onFechaLimitePagoChange = onFechaLimitePagoChange,
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            "$pasoArchivo. Selecciona el archivo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (bancoSeleccionado != null) Primary50 else Line2)
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
private fun TarjetaFechasSection(
    onFechaCorteChange: (LocalDate?) -> Unit,
    onFechaLimitePagoChange: (LocalDate?) -> Unit,
) {
    var fechaCorteText by remember { mutableStateOf("") }
    var fechaLimitePagoText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "2. Información de la tarjeta (opcional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Text(
            "Si el estado de cuenta no incluye estas fechas, ingrésalas manualmente.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )

        OutlinedTextField(
            value = fechaCorteText,
            onValueChange = { v ->
                fechaCorteText = v
                onFechaCorteChange(parseFecha(v))
            },
            label = { Text("Fecha de corte") },
            placeholder = { Text("dd/mm/aaaa", color = Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = fechaCorteText.isNotBlank() && parseFecha(fechaCorteText) == null,
            supportingText = {
                if (fechaCorteText.isNotBlank() && parseFecha(fechaCorteText) == null)
                    Text("Formato inválido. Usa dd/mm/aaaa", color = Expense)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = fechaLimitePagoText,
            onValueChange = { v ->
                fechaLimitePagoText = v
                onFechaLimitePagoChange(parseFecha(v))
            },
            label = { Text("Fecha límite de pago") },
            placeholder = { Text("dd/mm/aaaa", color = Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = fechaLimitePagoText.isNotBlank() && parseFecha(fechaLimitePagoText) == null,
            supportingText = {
                if (fechaLimitePagoText.isNotBlank() && parseFecha(fechaLimitePagoText) == null)
                    Text("Formato inválido. Usa dd/mm/aaaa", color = Expense)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
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
        disponible   -> MaterialTheme.colorScheme.surface
        else         -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }
    val bancoInfo  = bancoPorCodigo(banco.codigo)
    val bancoColor = bancoInfo.color.let { if (disponible) it else it.copy(alpha = 0.4f) }
    val textoColor = bancoInfo.fgColor
    val abbr       = bancoInfo.abbr

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
            modifier = Modifier.size(72.dp).background(Success50, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(36.dp))
        }
        Text("¡Importación exitosa!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
        Text("$transaccionesInsertadas transacciones importadas desde $banco", style = MaterialTheme.typography.bodyLarge, color = TextBody, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onVerHistorial, colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = Radii.md, modifier = Modifier.fillMaxWidth()) {
            Text("Ver historial", fontWeight = FontWeight.SemiBold)
        }
        Button(onClick = onNuevoArchivo, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface), shape = Radii.md, modifier = Modifier.fillMaxWidth()) {
            Text("Importar otro archivo", color = TextBody, fontWeight = FontWeight.SemiBold)
        }
    }
}
