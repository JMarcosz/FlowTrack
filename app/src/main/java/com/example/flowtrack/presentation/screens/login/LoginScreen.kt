package com.example.flowtrack.presentation.screens.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import androidx.navigation.NavController
import com.example.flowtrack.R
import com.example.flowtrack.presentation.navigation.Screen

// ── Colores exclusivos de la pantalla de login ───────────────────
private val BgLogin       = Color(0xFF06090F)
private val BtnDark       = Color(0xFF0B1220)
private val GlowBlue      = Color(0xFF2F6FED)
private val LogoBlue      = Color(0xFF5E8BFF)
private val LogoBlueLight = Color(0xFF7DA5FF)

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isLoading = uiState is LoginUiState.Loading

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLogin)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(90.dp))

        // ── Logo box ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(140.dp)
                // Glow azul exterior (simula boxShadow: 0 18px 60px rgba(47,111,237,0.35))
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlowBlue.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height / 2f + 18.dp.toPx()),
                            radius = 60.dp.toPx(),
                        ),
                        radius = 60.dp.toPx(),
                        center = Offset(size.width / 2f, size.height / 2f + 18.dp.toPx()),
                    )
                }
                .clip(RoundedCornerShape(32.dp))
                // Gradiente radial con centro al 20%/20% usando drawBehind para
                // que los cálculos sean en píxeles reales, no en dp
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.00f to Color(0x52508AFF),
                                0.65f to Color(0x8C14285A),
                                1.00f to Color(0x000B1220),
                            ),
                            center = Offset(size.width * 0.20f, size.height * 0.20f),
                            radius = size.width * 1.20f,
                        )
                    )
                }
                // Borde interior sutil (inset 0 0 0 1px rgba(255,255,255,0.06))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ChartIcon()
        }

        // ── Título ────────────────────────────────────────────────
        Spacer(Modifier.height(34.dp))
        Text(
            text = "FlowTrack",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        // ── Subtítulo ─────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tus finanzas, claras y\nbajo control",
            fontSize = 17.sp,
            color = Color.White.copy(alpha = 0.65f),
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
                color = Color(0xFFFF5C5C),
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
                .background(Color.White)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = BtnDark.copy(alpha = 0.08f)),
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
                        color = BtnDark.copy(alpha = 0.55f),
                        strokeWidth = 2.5.dp,
                        trackColor = BtnDark.copy(alpha = 0.12f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Conectando…",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
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
                        color = BtnDark,
                    )
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f))) {
                    append("Al continuar, aceptas nuestros\n")
                }
                withStyle(
                    SpanStyle(
                        color = Color.White.copy(alpha = 0.85f),
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

// ── Ícono de tendencia financiera (logo de la app) ───────────────
//
// SVG original viewBox 0 0 92 92:
//   circle cx=46 cy=46 r=32, stroke #5E8BFF, opacity 0.5
//   line  (28,60)→(40,46)→(52,52)→(66,30), stroke #7DA5FF
//   dots: r=3 at (28,60),(40,46),(52,52); r=4 at (66,30)
//   arrow: (60,30)→(66,24)→(72,30), stroke #5E8BFF
@Composable
private fun ChartIcon() {
    Canvas(modifier = Modifier.size(92.dp)) {
        val s = size.width / 92f

        // Círculo exterior semitransparente
        drawCircle(
            color = LogoBlue.copy(alpha = 0.5f),
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
            color = LogoBlueLight,
            style = Stroke(width = 3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Puntos en cada vértice
        drawCircle(color = LogoBlueLight, radius = 3f * s, center = Offset(28f * s, 60f * s))
        drawCircle(color = LogoBlueLight, radius = 3f * s, center = Offset(40f * s, 46f * s))
        drawCircle(color = LogoBlueLight, radius = 3f * s, center = Offset(52f * s, 52f * s))
        drawCircle(color = LogoBlue,      radius = 4f * s, center = Offset(66f * s, 30f * s))

        // Flecha hacia arriba
        drawPath(
            path = Path().apply {
                moveTo(60f * s, 30f * s)
                lineTo(66f * s, 24f * s)
                lineTo(72f * s, 30f * s)
            },
            color = LogoBlue,
            style = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
