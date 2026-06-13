package com.example.flowtrack.e2e.financial

import androidx.activity.compose.setContent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowtrack.MainActivity
import com.example.flowtrack.presentation.screens.duplicados.DuplicadosScreen
import com.example.flowtrack.ui.theme.FlowTrackTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialModulesE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun dashboard_muestraResumenFinancieroImportado() {
        launchDashboard().use {
            esperarTexto("Dashboard")
            assertVisible("Gastos totales")
            assertVisible("Ingresos totales")
            assertVisible("Balance neto")

            esperarYDesplazar("Gastos por categoría")
            assertVisible("Gastos por categoría")
            esperarYDesplazar("Por banco")
            assertVisible("Por banco")
        }
    }

    @Test
    fun transacciones_listaBuscaYFiltraSinModificarDatos() {
        launchDashboard().use {
            navegarBottom("Transacciones")
            esperarTexto("Transacciones")
            esperarTexto("Buscar transacciones")
            esperarFilasDeTransaccion()

            val buscador = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst()
            buscador.performTextInput(BUSQUEDA_SIN_RESULTADOS)
            esperarTexto("Sin resultados")
            buscador.performTextClearance()
            esperarFilasDeTransaccion()

            listOf("Ingresos", "Gastos", "Todas").forEach { filtro ->
                composeRule.onNodeWithText(filtro, useUnmergedTree = true).performClick()
                esperarResultadoDeFiltro()
                assertVisible("Transacciones")
            }
        }
    }

    @Test
    fun resumen_cambiaBancoCategoriaPeriodoYAbreDesglose() {
        launchDashboard().use {
            navegarBottom("Resumen")
            esperarTexto("Resumen por banco")
            assertVisible("Por banco")
            assertVisible("Por categoría")

            composeRule.onNodeWithText("Por categoría", useUnmergedTree = true).performClick()
            esperarTexto("Resumen por categoría")
            composeRule.onNodeWithText("Por banco", useUnmergedTree = true).performClick()
            esperarTexto("Resumen por banco")

            composeRule.onNodeWithText("Mes pasado", useUnmergedTree = true).performClick()
            composeRule.onNodeWithText("Este mes", useUnmergedTree = true).performClick()

            composeRule
                .onNodeWithContentDescription("Resumen por período", useUnmergedTree = true)
                .performClick()
            esperarTexto("Resumen por período")
            assertVisible("Día")
            assertVisible("Semana")
            assertVisible("Mes")
        }
    }

    @Test
    fun tarjetas_muestraProductosYEstadosImportados() {
        launchDashboard().use {
            navegarBottom("Tarjetas")
            esperarTexto("Tarjetas")
            esperarAusencia("Sin tarjetas")
            esperarTexto("Pago total pendiente")
            esperarTexto("Historial de estados")
            esperarAlguno("QIK", "AC")
        }
    }

    @Test
    fun historial_muestraCincoBancosYDetalleSinEliminar() {
        launchDashboard().use {
            navegarBottom("Más")
            esperarTexto("Historial")
            composeRule.onNodeWithText("Historial", useUnmergedTree = true).performClick()
            esperarTexto("Historial de importaciones")
            esperarAusencia("Sin importaciones aún")

            listOf("BA", "PO", "QI", "CI", "BH").forEach { codigoPublico ->
                esperarTexto(codigoPublico)
                composeRule
                    .onAllNodesWithText(codigoPublico, useUnmergedTree = true)
                    .onFirst()
                    .performScrollTo()
                    .assertIsDisplayed()
            }

            val cargaBanreservas = hasClickAction() and hasAnyDescendant(hasText("BA"))
            composeRule
                .onAllNodes(cargaBanreservas, useUnmergedTree = true)
                .onFirst()
                .performScrollTo()
                .performClick()

            esperarTexto("Insertadas")
            assertVisible("Duplicadas")
            esperarTextoQueEmpiezaPor("Parser v")
            assertVisible("Eliminar carga")
        }
    }

    @Test
    fun duplicados_abreEnModoSoloLectura() {
        launchDashboard().use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    FlowTrackTheme {
                        DuplicadosScreen(navController = rememberNavController())
                    }
                }
            }
            esperarTexto("Duplicados detectados")
            esperarAlguno("No hay duplicados detectados", "Par duplicado")
            composeRule
                .onNodeWithContentDescription("Volver", useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    private fun launchDashboard(): ActivityScenario<MainActivity> {
        assertNotNull(
            "El Pixel debe conservar una sesión Firebase para validar los datos importados",
            FirebaseAuth.getInstance().currentUser,
        )
        return ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            esperarTexto("Dashboard")
        }
    }

    private fun navegarBottom(destino: String) {
        composeRule
            .onNodeWithContentDescription(destino, useUnmergedTree = true)
            .performClick()
    }

    private fun esperarFilasDeTransaccion() {
        val categoriaGenerica = CATEGORIAS_PUBLICAS
            .map { hasText(it) }
            .reduce(SemanticsMatcher::or)
        val fila = hasClickAction() and hasAnyDescendant(categoriaGenerica)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(fila, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun esperarResultadoDeFiltro() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            val sinResultados = composeRule
                .onAllNodesWithText("Sin resultados", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
            sinResultados || hayFilasDeTransaccion()
        }
    }

    private fun hayFilasDeTransaccion(): Boolean {
        val categoriaGenerica = CATEGORIAS_PUBLICAS
            .map { hasText(it) }
            .reduce(SemanticsMatcher::or)
        return composeRule
            .onAllNodes(
                hasClickAction() and hasAnyDescendant(categoriaGenerica),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    private fun esperarYDesplazar(texto: String) {
        esperarTexto(texto)
        composeRule
            .onNodeWithText(texto, useUnmergedTree = true)
            .performScrollTo()
    }

    private fun assertVisible(texto: String) {
        composeRule
            .onAllNodesWithText(texto, useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(texto, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun esperarTextoQueEmpiezaPor(prefijo: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(prefijo, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun esperarAlguno(vararg textos: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            textos.any { texto ->
                composeRule
                    .onAllNodesWithText(texto, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    private fun esperarAusencia(texto: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(texto, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val BUSQUEDA_SIN_RESULTADOS = "E2E_SIN_COINCIDENCIAS_9F3A"

        val CATEGORIAS_PUBLICAS = listOf(
            "Sin categorizar",
            "Alimentación",
            "Transporte",
            "Servicios",
            "Compras",
            "Entretenimiento",
            "Salud",
            "Educación",
            "Transferencias",
            "Ingresos",
            "Otros",
        )
    }
}
