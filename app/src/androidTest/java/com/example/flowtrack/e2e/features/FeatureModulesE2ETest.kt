package com.example.flowtrack.e2e.features

import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flowtrack.MainActivity
import com.example.flowtrack.presentation.screens.bancos.BancosYCuentasScreen
import com.example.flowtrack.presentation.screens.categorias.CategoriasScreen
import com.example.flowtrack.presentation.screens.configuracion.ConfiguracionScreen
import com.example.flowtrack.presentation.screens.conversor.ConversorScreen
import com.example.flowtrack.presentation.screens.exportar.ExportarScreen
import com.example.flowtrack.presentation.screens.exportar.ExportarViewModel
import com.example.flowtrack.presentation.screens.metas.MetasScreen
import com.example.flowtrack.presentation.screens.notificaciones.NotificacionesScreen
import com.example.flowtrack.presentation.screens.perfil.PerfilScreen
import com.example.flowtrack.presentation.screens.presupuestos.PresupuestosScreen
import com.example.flowtrack.presentation.screens.reglas.ReglasScreen
import com.example.flowtrack.presentation.screens.sugerencias.SugerenciasScreen
import com.example.flowtrack.ui.theme.FlowTrackTheme
import com.google.firebase.auth.FirebaseAuth
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FeatureModulesE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @After
    fun cerrarChooserSiSigueVisible() {
        val top = actividadSuperior()
        if (
            top.contains("ChooserActivity", ignoreCase = true) ||
            top.contains("ResolverActivity", ignoreCase = true)
        ) {
            ejecutarShell("input keyevent KEYCODE_BACK")
        }
    }

    @Test
    fun categorias_muestraEstadoActualSinMutarlo() {
        launchScreen { CategoriasScreen(rememberNavController()) }.use {
            esperarTexto("Gestión de Categorías")
            esperarYDesplazar("Categorías del Sistema")
            assertVisible("Categorías del Sistema")
            composeRule
                .onNodeWithContentDescription("Nueva Categoría", useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun reglas_muestraReglasYSugeridasSinMutarlas() {
        launchScreen { ReglasScreen(rememberNavController()) }.use {
            esperarTexto("Reglas de Categorización")
            assertVisible("Mis reglas")
            composeRule.onNodeWithText("Sugeridas", useUnmergedTree = true).performClick()
            esperarAlguno("¡Todo al día!", "Asignar")
        }
    }

    @Test
    fun sugerencias_muestraEstadoActualSinMutarlo() {
        launchScreen { SugerenciasScreen(rememberNavController()) }.use {
            esperarTexto("Asistente de Categorización")
            esperarAlguno("¡Todo al día!", "Asignar Categoría")
        }
    }

    @Test
    fun presupuestos_muestraEstadoActualYFormularioCancelable() {
        launchScreen { PresupuestosScreen(rememberNavController()) }.use {
            esperarTexto("Presupuestos")
            composeRule
                .onNodeWithContentDescription("Nuevo presupuesto", useUnmergedTree = true)
                .performClick()
            esperarTexto("Nuevo presupuesto")
            assertVisible("Monto límite (DOP)")
            composeRule.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
            esperarAusencia("Monto límite (DOP)")
        }
    }

    @Test
    fun metas_muestraEstadoActualYFormularioCancelable() {
        launchScreen { MetasScreen(rememberNavController()) }.use {
            esperarTexto("Metas de ahorro")
            composeRule
                .onNodeWithContentDescription("Nueva meta", useUnmergedTree = true)
                .performClick()
            esperarTexto("Nueva meta de ahorro")
            assertVisible("Nombre de la meta")
            assertVisible("Monto objetivo (DOP)")
            composeRule.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
            esperarAusencia("Monto objetivo (DOP)")
        }
    }

    @Test
    fun conversor_calculaValoresSinteticosEnAmbasDirecciones() {
        launchScreen { ConversorScreen(rememberNavController()) }.use {
            esperarTexto("Conversor de Divisas")
            esperarTexto("Monto")

            val entrada = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst()
            entrada.performTextInput("1000")
            esperarResultadoDistintoDeCero("USD")

            composeRule
                .onNodeWithContentDescription("Invertir dirección", useUnmergedTree = true)
                .performClick()

            entrada.performTextClearance()
            entrada.performTextInput("10")
            esperarResultadoDistintoDeCero("DOP")
        }
    }

    @Test
    fun exportar_csvValidaArchivoMimeYResolucionSinCompartir() {
        ejecutarExportacion(
            ExportExpectation("CSV", "text/csv", "Compartir CSV", ".csv"),
        )
    }

    @Test
    fun exportar_pdfValidaArchivoMimeYResolucionSinCompartir() {
        ejecutarExportacion(
            ExportExpectation("PDF", "application/pdf", "Compartir PDF", ".pdf"),
        )
    }

    @Test
    fun exportar_xlsxValidaArchivoMimeYResolucionSinCompartir() {
        ejecutarExportacion(
            ExportExpectation(
                "XLSX",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Compartir XLSX",
                ".xlsx",
            ),
        )
    }

    private fun ejecutarExportacion(expected: ExportExpectation) {
        val cache = instrumentation.targetContext.cacheDir
        val archivosIniciales = rutasRelativas(cache)
        val viewModelRef = AtomicReference<ExportarViewModel>()

        try {
            launchScreen {
                val viewModel: ExportarViewModel = hiltViewModel()
                viewModelRef.set(viewModel)
                ExportarScreen(rememberNavController(), viewModel = viewModel)
            }.use {
                esperarTexto("Exportar estados")
                instrumentation.runOnMainSync {
                    viewModelRef.get().setFechaInicio(LocalDate.of(2000, 1, 1))
                }
                composeRule.waitForIdle()

                desplazarAlInicio()
                esperarTexto(expected.formato)
                composeRule
                    .onNodeWithText(expected.formato, useUnmergedTree = true)
                    .performClick()
                desplazarHastaTexto("Exportar")
                composeRule
                    .onNodeWithText("Exportar", useUnmergedTree = true)
                    .performScrollTo()
                    .performClick()

                val tituloValidado = esperarResolucionExportacion(expected, viewModelRef.get())
                assertTrue(
                    "La exportación ${expected.formato} debe crear un archivo temporal",
                    rutasRelativas(cache).any { it.endsWith(expected.extension, ignoreCase = true) },
                )
                assumeTrue(
                    "BLOCKED: Android resolvió ACTION_CHOOSER directamente a una app externa; " +
                        "el archivo fue generado, pero MIME/título no pudieron observarse " +
                        "de forma completa en el chooser.",
                    tituloValidado,
                )
            }
        } finally {
            eliminarArchivosNuevos(cache, archivosIniciales)
        }
    }

    @Test
    fun configuracion_restauraTemaYProtegeAccionesDestructivas() {
        launchScreen { ConfiguracionScreen(rememberNavController()) }.use {
            esperarTexto("Configuración")
            esperarYDesplazar("Modo oscuro")

            val toggleMatcher = SemanticsMatcher.expectValue(
                SemanticsProperties.ToggleableState,
                ToggleableState.On,
            ) or SemanticsMatcher.expectValue(
                SemanticsProperties.ToggleableState,
                ToggleableState.Off,
            )
            val estadoInicial = composeRule
                .onAllNodes(toggleMatcher, useUnmergedTree = true)
                .onFirst()
                .fetchSemanticsNode()
                .config[SemanticsProperties.ToggleableState]

            val filaTema = hasClickAction() and hasAnyDescendant(hasText("Modo oscuro"))
            composeRule.onAllNodes(filaTema, useUnmergedTree = true).onFirst().performClick()
            esperarEstadoToggle(estadoInicial.invertido())
            composeRule.onAllNodes(filaTema, useUnmergedTree = true).onFirst().performClick()
            esperarEstadoToggle(estadoInicial)

            esperarYDesplazar("Borrar mis datos")
            composeRule.onNodeWithText("Borrar mis datos", useUnmergedTree = true).performClick()
            esperarTexto("Borrar todos mis datos")
            composeRule.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
            esperarAusencia("Borrar todos mis datos")
        }
    }

    @Test
    fun bancosYCuentas_validaUiSinCambiarDatos() {
        launchScreen { BancosYCuentasScreen(rememberNavController()) }.use {
            esperarTexto("Bancos y Cuentas")
            composeRule
                .onNodeWithContentDescription("Agregar cuenta", useUnmergedTree = true)
                .performClick()
            esperarTexto("Nueva cuenta")
            assertVisible("Alias (ej: Cuenta nómina)")
            composeRule.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
            esperarAusencia("Nueva cuenta")
        }
    }

    @Test
    fun notificaciones_validaUiSinCambiarPreferencias() {
        launchScreen { NotificacionesScreen(rememberNavController()) }.use {
            esperarTexto("Notificaciones")
            assertVisible("Activar notificaciones")
            esperarAlguno("Probar recordatorios ahora", "Recordatorios, resúmenes y alertas")
        }
    }

    @Test
    fun perfil_cancelaCierreSinPerderSesion() {
        launchScreen { PerfilScreen(rememberNavController()) }.use {
            esperarTexto("Perfil")
            assertVisible("Información de cuenta")
            assertVisible("ID de usuario")
            composeRule.onNodeWithText("Cerrar sesión", useUnmergedTree = true).performClick()
            esperarTexto("¿Estás seguro que deseas cerrar sesión?")
            composeRule.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
            assertNotNull(FirebaseAuth.getInstance().currentUser)
        }
    }

    private fun launchScreen(content: @Composable () -> Unit): ActivityScenario<MainActivity> {
        assertNotNull(
            "El Pixel debe conservar la sesión Firebase actual",
            FirebaseAuth.getInstance().currentUser,
        )
        return ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    FlowTrackTheme {
                        content()
                    }
                }
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
    }

    private fun esperarResolucionExportacion(
        expected: ExportExpectation,
        viewModel: ExportarViewModel,
    ): Boolean {
        composeRule.waitUntil(CHOOSER_TIMEOUT_MILLIS) {
            val top = actividadSuperior()
            top.contains("ChooserActivity", ignoreCase = true) ||
                top.contains("ResolverActivity", ignoreCase = true) ||
                viewModel.state.value.error != null ||
                viewModel.state.value.exito != null
        }

        viewModel.state.value.error?.let { error ->
            throw AssertionError("Exportación ${expected.formato} bloqueada: $error")
        }

        Thread.sleep(1_000)
        val top = actividadSuperior()
        val dump = ejecutarShell("dumpsys activity activities")
        val mimeVisible = dump.contains(expected.mime, ignoreCase = true)

        val chooserVisible =
            top.contains("ChooserActivity", ignoreCase = true) ||
                top.contains("ResolverActivity", ignoreCase = true)
        if (chooserVisible) {
            assertTrue(
                "El chooser debe conservar el MIME ${expected.mime}",
                mimeVisible,
            )
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                textosVentanaActiva().any { it.contains(expected.titulo, ignoreCase = true) }
            }
            ejecutarShell("input keyevent KEYCODE_BACK")
            return true
        }

        val packageName = Regex("""TASK \d+:(\S+)""")
            .find(top)
            ?.groupValues
            ?.getOrNull(1)
        if (packageName != null && packageName != instrumentation.targetContext.packageName) {
            ejecutarShell("am force-stop $packageName")
        } else {
            ejecutarShell("input keyevent KEYCODE_BACK")
        }
        return false
    }

    private fun esperarResultadoDistintoDeCero(prefijo: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(
                    hasText(prefijo, substring = true) and
                        !hasText("$prefijo 0.00"),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun esperarEstadoToggle(estado: ToggleableState) {
        val matcher = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, estado)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(matcher, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun ToggleableState.invertido(): ToggleableState = when (this) {
        ToggleableState.On -> ToggleableState.Off
        ToggleableState.Off -> ToggleableState.On
        ToggleableState.Indeterminate -> error("El tema no admite estado indeterminado")
    }

    private fun actividadSuperior(): String = ejecutarShell("dumpsys activity top")

    private fun textosVentanaActiva(): List<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        return buildList { recolectarTextos(root, this) }
    }

    private fun recolectarTextos(node: AccessibilityNodeInfo, output: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child ->
                recolectarTextos(child, output)
                child.recycle()
            }
        }
    }

    private fun ejecutarShell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private fun rutasRelativas(root: File): Set<String> =
        root.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()

    private fun eliminarArchivosNuevos(root: File, iniciales: Set<String>) {
        root.walkBottomUp().forEach { file ->
            if (file.isFile && file.relativeTo(root).invariantSeparatorsPath !in iniciales) {
                file.delete()
            } else if (file.isDirectory && file != root && file.listFiles().isNullOrEmpty()) {
                file.delete()
            }
        }
    }

    private fun assertVisible(texto: String) {
        composeRule
            .onAllNodesWithText(texto, useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }

    private fun esperarYDesplazar(texto: String) {
        esperarTexto(texto)
        composeRule
            .onAllNodesWithText(texto, useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
    }

    private fun desplazarHastaTexto(texto: String) {
        repeat(8) {
            if (
                composeRule
                    .onAllNodesWithText(texto, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                return
            }
            composeRule
                .onAllNodes(hasScrollAction(), useUnmergedTree = true)
                .onFirst()
                .performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        esperarTexto(texto)
    }

    private fun desplazarAlInicio() {
        repeat(8) {
            val scrollables = composeRule
                .onAllNodes(hasScrollAction(), useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (scrollables.isEmpty()) return
            composeRule
                .onAllNodes(hasScrollAction(), useUnmergedTree = true)
                .onFirst()
                .performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(texto, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun esperarAlguno(vararg textos: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            textos.any { texto ->
                composeRule
                    .onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
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

    private data class ExportExpectation(
        val formato: String,
        val mime: String,
        val titulo: String,
        val extension: String,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val CHOOSER_TIMEOUT_MILLIS = 60_000L
    }
}
