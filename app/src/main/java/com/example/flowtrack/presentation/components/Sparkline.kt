package com.example.flowtrack.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mini sparkline — SVG-equivalent del componente Sparkline del prototipo HTML.
 * Soporta línea simple, área rellena y curvas suavizadas (Catmull-Rom → Bézier).
 */
@Composable
fun Sparkline(
    data: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 1.8.dp,
    area: Boolean = false,
    smooth: Boolean = false,
) {
    if (data.size < 2) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val max = data.max()
        val min = data.min()
        val range = (max - min).coerceAtLeast(0.001f)
        val pad = 3.dp.toPx()
        val step = w / (data.size - 1).coerceAtLeast(1)

        val points = data.mapIndexed { i, v ->
            Offset(
                x = i * step,
                y = h - ((v - min) / range) * (h - pad * 2) - pad,
            )
        }

        val linePath = buildLinePath(points, smooth)

        if (area) {
            val areaPath = Path().apply {
                addPath(linePath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = h,
                ),
            )
        }

        drawPath(
            path = linePath,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun buildLinePath(points: List<Offset>, smooth: Boolean): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    if (!smooth || points.size < 3) {
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        return path
    }
    // Catmull-Rom → cubic Bézier
    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else p2
        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}
