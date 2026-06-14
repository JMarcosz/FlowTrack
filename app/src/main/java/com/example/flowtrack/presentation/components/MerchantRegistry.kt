package com.example.flowtrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.ui.theme.*
import kotlin.math.abs

data class MerchantUI(
    val patron: String,
    val nombre: String,
    val abbr: String,
    val bg: Color,
    val fg: Color,
)

private data class MerchantSwatch(val bg: Color, val fg: Color)

private val merchantSwatches = listOf(
    MerchantSwatch(Color(0xFFEAF1FE), Color(0xFF2960DA)),
    MerchantSwatch(CatCompras, Color.White),
    MerchantSwatch(CatServicios, Color.White),
    MerchantSwatch(CatTransporte, BgDark),
    MerchantSwatch(CatAlimentacion, Color.White),
    MerchantSwatch(CatSalud, Color.White),
    MerchantSwatch(CatPagos, Color.White),
    MerchantSwatch(CatOtros, BgDark),
    MerchantSwatch(CatIngresos, Color.White),
    MerchantSwatch(CatAtm, Color.White),
    MerchantSwatch(CatSuscripciones, BgDark),
    MerchantSwatch(BancoBanReservas, Color.White),
    MerchantSwatch(BancoPopular, Color.White),
    MerchantSwatch(BancoQik, BgDark),
    MerchantSwatch(BancoBhd, Color.White),
    MerchantSwatch(BancoCibao, Color.White),
)

private val merchantRegistry = listOf(
    MerchantUI("AMAZON PRIME", "Amazon Prime", "AP", CatCompras, Color.White),
    MerchantUI("LARAVEL CLOUD", "Laravel Cloud", "LC", Color(0xFF2960DA), Color.White),
    MerchantUI("ALTICE", "Altice", "AL", CatServicios, Color.White),
    MerchantUI("CLARO", "Claro", "CL", CatServicios, Color.White),
    MerchantUI("UBER EATS", "Uber Eats", "UE", CatCompras, Color.White),
    MerchantUI("UBER", "Uber", "UB", CatTransporte, BgDark),
    MerchantUI("NETFLIX", "Netflix", "NF", CatPagos, Color.White),
    MerchantUI("SPOTIFY", "Spotify", "SP", CatPagos, Color.White),
    MerchantUI("PAYPAL", "PayPal", "PP", Color(0xFF2960DA), Color.White),
    MerchantUI("CHUCK E CHEESE", "Chuck E. Cheese", "CC", CatPagos, Color.White),
    MerchantUI("MEGAPLEX", "Megaplex", "MP", CatPagos, Color.White),
    MerchantUI("BATH", "Bath", "BT", CatPagos, Color.White),
).sortedByDescending { it.patron.length }

fun merchantPorDescripcion(descripcionNormalizada: String): MerchantUI {
    val desc = descripcionNormalizada.normalizarDescripcion()
    val exacta = merchantRegistry.firstOrNull { desc.contains(it.patron) }
    if (exacta != null) return exacta

    val tokens = desc.split(" ").filter { it.isNotBlank() }
    val abbr = when {
        tokens.size >= 2 -> tokens.take(2).joinToString("") { it.take(1) }
        tokens.size == 1 -> tokens.first().take(2)
        else -> "TX"
    }.take(3)

    val swatch = merchantSwatches[abs(desc.hashCode()) % merchantSwatches.size]
    return MerchantUI(
        patron = desc,
        nombre = desc,
        abbr = abbr.ifBlank { "TX" },
        bg = swatch.bg,
        fg = swatch.fg,
    )
}

@Composable
fun MerchantLogo(
    descripcionNormalizada: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fontSize: Int = 13,
) {
    val merchant = remember(descripcionNormalizada) { merchantPorDescripcion(descripcionNormalizada) }
    
    // Si el merchant usa los colores primarios base, los hacemos dinámicos
    val isDefaultPrimary = merchant.bg == Color(0xFFEAF1FE) || merchant.bg == Color(0xFF2960DA)
    val backgroundColor = if (isDefaultPrimary) {
        if (merchant.bg == Color(0xFFEAF1FE)) MaterialTheme.colorScheme.primaryContainer 
        else MaterialTheme.colorScheme.primary
    } else merchant.bg
    
    val foregroundColor = if (isDefaultPrimary) {
        if (merchant.fg == Color(0xFF2960DA)) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onPrimary
    } else merchant.fg

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = merchant.abbr,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = foregroundColor,
            letterSpacing = (-0.3).sp,
        )
    }
}
