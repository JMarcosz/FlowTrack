package com.example.flowtrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
    MerchantSwatch(Primary50, Primary600),
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
    MerchantUI("LARAVEL CLOUD", "Laravel Cloud", "LC", Primary600, Color.White),
    MerchantUI("ALTICE", "Altice", "AL", CatServicios, Color.White),
    MerchantUI("CLARO", "Claro", "CL", CatServicios, Color.White),
    MerchantUI("UBER EATS", "Uber Eats", "UE", CatCompras, Color.White),
    MerchantUI("UBER", "Uber", "UB", CatTransporte, BgDark),
    MerchantUI("NETFLIX", "Netflix", "NF", CatPagos, Color.White),
    MerchantUI("SPOTIFY", "Spotify", "SP", CatPagos, Color.White),
    MerchantUI("PAYPAL", "PayPal", "PP", Primary600, Color.White),
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

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .background(merchant.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = merchant.abbr,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = merchant.fg,
            letterSpacing = (-0.3).sp,
        )
    }
}
