package com.example.flowtrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Design system §4.1
object Spacing {
    val xxs  =  4.dp
    val xs   =  6.dp
    val sm   =  8.dp
    val md   = 12.dp
    val lg   = 14.dp
    val xl   = 16.dp   // padding lateral estándar de pantalla
    val xxl  = 20.dp
    val xxxl = 24.dp
    val xxxxl= 32.dp
}

// Design system §4.2
object Radii {
    val sm   = RoundedCornerShape(8.dp)
    val md   = RoundedCornerShape(12.dp)
    val lg   = RoundedCornerShape(16.dp)
    val xl   = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}
