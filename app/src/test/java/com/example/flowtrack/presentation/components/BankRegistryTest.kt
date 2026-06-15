package com.example.flowtrack.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BankRegistryTest {

    @Test
    fun bancosConLogoUsanAssetLocal() {
        assertNotNull(bancoPorCodigo("BANRESERVAS").logoResId)
        assertNotNull(bancoPorCodigo("POPULAR").logoResId)
        assertNotNull(bancoPorCodigo("QIK").logoResId)
        assertNotNull(bancoPorCodigo("CIBAO").logoResId)
        assertNotNull(bancoPorCodigo("BHD").logoResId)
    }

    @Test
    fun bancoDesconocidoNoTieneLogo() {
        assertEquals(null, bancoPorCodigo("OTRO").logoResId)
    }
}
