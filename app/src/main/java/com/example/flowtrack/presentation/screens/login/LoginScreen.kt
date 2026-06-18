package com.example.flowtrack.presentation.screens.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.flowtrack.R

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

// ── Colores exclusivos de la pantalla de login ───────────────────

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isLoading = uiState is LoginUiState.Loading

    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        onDispose {
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ),
                navigationBarStyle = SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            )
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(90.dp))

        // ── Logo box ──────────────────────────────────────────────
        LogoBox()

        // ── Título ────────────────────────────────────────────────
        Spacer(Modifier.height(34.dp))
        Text(
            text = "FlowTrack",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        // ── Subtítulo ─────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tus finanzas, claras y\nbajo control",
            fontSize = 17.sp,
            color = colorScheme.onBackground.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
            lineHeight = (17 * 1.4).sp,
            fontWeight = FontWeight.Normal,
        )

        // Empuja el botón al fondo
        Spacer(Modifier.weight(1f))

        // ── Mensaje de error ──────────────────────────────────────
        if (uiState is LoginUiState.Error) {
            Text(
                text = (uiState as LoginUiState.Error).message,
                color = Color(0xFFFF7070),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        // ── Botón Google ──────────────────────────────────────────
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colorScheme.surface)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = colorScheme.primary.copy(alpha = 0.08f)),
                    enabled = !isLoading,
                ) {
                    val clientId = context.getString(R.string.default_web_client_id)
                    viewModel.signInWithGoogle(context, clientId)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = colorScheme.onSurface.copy(alpha = 0.65f),
                        strokeWidth = 2.5.dp,
                        trackColor = colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Conectando…",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google),
                        contentDescription = "Google",
                        modifier = Modifier.size(22.dp),
                        tint = Color.Unspecified,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Continuar con Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colorScheme.onBackground.copy(alpha = 0.5f))) {
                    append("Al continuar, aceptas nuestros\n")
                }
                withStyle(
                    SpanStyle(
                        color = colorScheme.onBackground.copy(alpha = 0.85f),
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append("Términos y Condiciones")
                }
            },
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(22.dp))
    }
}

// ── Contenedor del logo con glow exterior ───────────────────────
//
// El truco: graphicsLayer(clip=false) permite que drawBehind pinte fuera de los
// 140 dp del Box sin afectar el layout (el espacio reservado sigue siendo 140 dp).
// Los Spacer() del padre se calculan desde el borde del Box, no desde el glow.
@Composable
private fun LogoBox() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(140.dp)
            .graphicsLayer { clip = false }
            // Glow — se dibuja fuera del área de 140 dp
            .drawBehind {
                val cx = size.width  / 2f
                val cy = size.height / 2f + 18.dp.toPx()
                val r  = 96.dp.toPx()
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to colorScheme.primary.copy(alpha = 0.60f),
                            0.38f to colorScheme.primary.copy(alpha = 0.28f),
                            0.65f to colorScheme.primary.copy(alpha = 0.09f),
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    topLeft = Offset(cx - r, cy - r),
                    size   = Size(r * 2f, r * 2f),
                )
            }
            // A partir de aquí todo se recorta al rectángulo redondeado
            .clip(RoundedCornerShape(32.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to colorScheme.primary.copy(alpha = 0.38f),
                            0.60f to colorScheme.primaryContainer.copy(alpha = 0.60f),
                            1.00f to colorScheme.background,
                        ),
                        center = Offset(size.width * 0.20f, size.height * 0.20f),
                        radius = size.width * 1.20f,
                    ),
                )
            }
            .border(1.dp, colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center,
    ) {
        ChartIcon(colorScheme)
    }
}

// ── Ícono de tendencia financiera (logo de la app) ───────────────
//
// SVG original viewBox 0 0 92 92:
//   circle cx=46 cy=46 r=32, stroke #5E8BFF, opacity 0.5
//   line  (28,60)→(40,46)→(52,52)→(66,30), stroke #7DA5FF
//   dots: r=3 at (28,60),(40,46),(52,52); r=4 at (66,30)
//   arrow: (60,30)→(66,24)→(72,30), stroke #5E8BFF
@Composable
private fun ChartIcon(colorScheme: ColorScheme) {
    Canvas(modifier = Modifier.size(92.dp)) {
        val s = size.width / 92f

        // Círculo exterior semitransparente
        drawCircle(
            color = colorScheme.primary.copy(alpha = 0.5f),
            radius = 32f * s,
            center = Offset(46f * s, 46f * s),
            style = Stroke(width = 2.4f * s),
        )

        // Línea de tendencia
        drawPath(
            path = Path().apply {
                moveTo(28f * s, 60f * s)
                lineTo(40f * s, 46f * s)
                lineTo(52f * s, 52f * s)
                lineTo(66f * s, 30f * s)
            },
            color = colorScheme.primaryContainer,
            style = Stroke(width = 3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Puntos en cada vértice
        drawCircle(color = colorScheme.primaryContainer, radius = 3f * s, center = Offset(28f * s, 60f * s))
        drawCircle(color = colorScheme.primaryContainer, radius = 3f * s, center = Offset(40f * s, 46f * s))
        drawCircle(color = colorScheme.primaryContainer, radius = 3f * s, center = Offset(52f * s, 52f * s))
        drawCircle(color = colorScheme.primary,      radius = 4f * s, center = Offset(66f * s, 30f * s))

        // Flecha hacia arriba
        drawPath(
            path = Path().apply {
                moveTo(60f * s, 30f * s)
                lineTo(66f * s, 24f * s)
                lineTo(72f * s, 30f * s)
            },
            color = colorScheme.primary,
            style = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
