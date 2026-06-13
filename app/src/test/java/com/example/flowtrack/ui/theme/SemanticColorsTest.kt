package com.example.flowtrack.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticColorsTest {

    @Test
    fun `tokens semanticos conservan los valores del design system`() {
        assertEquals(0xFF16A34A.toInt(), Income.toArgb())
        assertEquals(0xFFE7F7EC.toInt(), Income50.toArgb())
        assertEquals(0xFFDC2626.toInt(), Expense.toArgb())
        assertEquals(0xFFFDECEC.toInt(), Expense50.toArgb())
        assertEquals(0xFF16A34A.toInt(), Success.toArgb())
        assertEquals(0xFFE7F7EC.toInt(), Success50.toArgb())
    }
}
