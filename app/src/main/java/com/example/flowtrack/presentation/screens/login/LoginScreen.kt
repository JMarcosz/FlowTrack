package com.example.flowtrack.presentation.screens.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val LoginBg       = Color(0xFF06090F)
private val LoginDark     = Color(0xFF0B1220)
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
            .background(LoginBg)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(90.dp))

        // ── Logo ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f  to Color(0x52508AFF),
                            0.65f to Color(0x8C14285A),
                            1.0f  to Color(0x000B1220),
                        ),
                        center = Offset(140f * 0.2f, 140f * 0.2f),
                        radius = 140f * 1.2f,
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            ChartIcon()
        }

        Spacer(Modifier.height(34.dp))

        Text(
            text = "FlowTrack",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Tus finanzas, claras y\nbajo control",
            fontSize = 17.sp,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.weight(1f))

        // ── Error ────────────────────────────────────────────────
        if (uiState is LoginUiState.Error) {
            Text(
                text = (uiState as LoginUiState.Error).message,
                color = Color(0xFFFF5C5C),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        // ── Botón Google ─────────────────────────────────────────
        Button(
            onClick = {
                val clientId = context.getString(R.string.default_web_client_id)
                viewModel.signInWithGoogle(context, clientId)
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.85f),
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = LoginDark.copy(alpha = 0.55f),
                    strokeWidth = 2.5.dp,
                    trackColor = LoginDark.copy(alpha = 0.12f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Conectando…",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B),
                )
            } else {
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
                    color = LoginDark,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Footer ───────────────────────────────────────────────
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

// ── Ícono de gráfico de tendencia (logo de la app) ───────────────
@Composable
private fun ChartIcon() {
    Canvas(modifier = Modifier.size(92.dp)) {
        val w = size.width
        val s = w / 92f   // escala respecto al viewBox 92×92

        // Círculo exterior semitransparente
        drawCircle(
            color = LogoBlue.copy(alpha = 0.5f),
            radius = 32f * s,
            center = Offset(46f * s, 46f * s),
            style = Stroke(width = 2.4f * s),
        )

        // Línea de tendencia (28,60)→(40,46)→(52,52)→(66,30)
        val linePath = Path().apply {
            moveTo(28f * s, 60f * s)
            lineTo(40f * s, 46f * s)
            lineTo(52f * s, 52f * s)
            lineTo(66f * s, 30f * s)
        }
        drawPath(
            path = linePath,
            color = LogoBlueLight,
            style = Stroke(width = 3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Puntos en los vértices
        drawCircle(color = LogoBlueLight, radius = 3f * s, center = Offset(28f * s, 60f * s))
        drawCircle(color = LogoBlueLight, radius = 3f * s, center = Offset(40f * s, 46f * s))
        drawCircle(color = LogoBlueLight, radius = 3f * s, center = Offset(52f * s, 52f * s))
        drawCircle(color = LogoBlue,      radius = 4f * s, center = Offset(66f * s, 30f * s))

        // Flecha hacia arriba sobre el último punto
        val arrowPath = Path().apply {
            moveTo(60f * s, 30f * s)
            lineTo(66f * s, 24f * s)
            lineTo(72f * s, 30f * s)
        }
        drawPath(
            path = arrowPath,
            color = LogoBlue,
            style = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
