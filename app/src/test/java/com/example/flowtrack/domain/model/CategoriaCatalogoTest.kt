package com.example.flowtrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriaCatalogoTest {

    @Test
    fun `el catalogo resuelve nombres conocidos y el fallback`() {
        assertEquals("Alimentación", CategoriaCatalogo.nombreDe(CategoriaCatalogo.ALIMENTACION))
        assertEquals("Sin categorizar", CategoriaCatalogo.nombreDe(null))
        assertEquals("Sin categorizar", CategoriaCatalogo.nombreDe("desconocida"))
    }
}
