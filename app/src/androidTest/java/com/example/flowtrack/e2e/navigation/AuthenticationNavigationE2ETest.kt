package com.example.flowtrack.e2e.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowtrack.MainActivity
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationNavigationE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun sesionExistente_permiteRecorrerNavegacionPrincipalYDrawer() {
        assertNotNull(
            "El Pixel debe conservar una sesion Firebase antes de ejecutar este E2E",
            FirebaseAuth.getInstance().currentUser,
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)

            esperarTexto("Dashboard")
            composeRule.onNodeWithText("Dashboard", useUnmergedTree = true).assertIsDisplayed()

            navegarPorBottomNav(
                destino = "Transacciones",
                descripcionEsperada = "Importar",
            )
            navegarPorBottomNav(
                destino = "Resumen",
                textoEsperado = "Resumen por banco",
            )
            navegarPorBottomNav(
                destino = "Tarjetas",
                descripcionEsperada = "Agregar tarjeta",
            )
            navegarPorBottomNav(
                destino = "Más",
                textoEsperado = "Configuración",
            )
            navegarPorBottomNav(
                destino = "Inicio",
                textoEsperado = "Dashboard",
            )

            esperarDescripcion("Menú")
            composeRule
                .onNodeWithContentDescription("Menú", useUnmergedTree = true)
                .performClick()

            listOf(
                "FlowTrack",
                "Metas de ahorro",
                "Presupuestos",
                "Bancos y cuentas",
                "Tasas de cambio",
            ).forEach { texto ->
                esperarTexto(texto)
                composeRule.onNodeWithText(texto, useUnmergedTree = true).assertIsDisplayed()
            }
        }
    }

    private fun navegarPorBottomNav(
        destino: String,
        textoEsperado: String? = null,
        descripcionEsperada: String? = null,
    ) {
        esperarDescripcion(destino)
        composeRule
            .onNodeWithContentDescription(destino, useUnmergedTree = true)
            .performClick()
        textoEsperado?.let {
            esperarTexto(it)
            composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        descripcionEsperada?.let {
            esperarDescripcion(it)
            composeRule
                .onNodeWithContentDescription(it, useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(texto, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun esperarDescripcion(descripcion: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithContentDescription(descripcion, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
    }
}
