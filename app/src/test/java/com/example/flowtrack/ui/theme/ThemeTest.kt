package com.example.flowtrack.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    @Test
    fun resolverTemaOscuro_usaSistemaCuandoNoHayPreferenciaGuardada() {
        assertTrue(resolverTemaOscuro(null, true))
        assertFalse(resolverTemaOscuro(null, false))
    }

    @Test
    fun resolverTemaOscuro_respetaPreferenciaGuardada() {
        assertFalse(resolverTemaOscuro(false, true))
        assertTrue(resolverTemaOscuro(true, false))
    }
}
