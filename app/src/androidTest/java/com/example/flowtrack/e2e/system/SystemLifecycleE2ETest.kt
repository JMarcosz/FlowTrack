package com.example.flowtrack.e2e.system

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flowtrack.MainActivity
import com.example.flowtrack.core.notifications.NotificationHelper
import com.example.flowtrack.core.notifications.NotificationRoute
import com.example.flowtrack.core.workers.NotificacionScheduler
import com.google.firebase.auth.FirebaseAuth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemLifecycleE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun limpiarNotificacionSintetica() {
        context.getSystemService(NotificationManager::class.java)?.cancel(TEST_NOTIFICATION_ID)
    }

    @Test
    fun canalesNotificacion_estanRegistradosConImportanciaValida() {
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager)

        val canales = listOf(
            NotificationHelper.CANAL_PAGOS,
            NotificationHelper.CANAL_RESUMENES,
            NotificationHelper.CANAL_ALERTAS,
            NotificationHelper.CANAL_PUSH,
        )
        canales.forEach { id ->
            val canal = manager.getNotificationChannel(id)
            assertNotNull("Falta el canal de notificacion $id", canal)
            assertNotEquals(
                "El canal $id no debe estar bloqueado por defecto",
                NotificationManager.IMPORTANCE_NONE,
                canal.importance,
            )
        }
    }

    @Test
    fun permisoNotificaciones_coincideConEstadoPreparadoPorAdb() {
        val esperado = InstrumentationRegistry.getArguments()
            .getString(ARG_EXPECTED_NOTIFICATION_PERMISSION)
            ?.toBooleanStrictOrNull()
            ?: error("Falta -e $ARG_EXPECTED_NOTIFICATION_PERMISSION true|false")
        val concedido = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        assertEquals(esperado, concedido)
        assertEquals(esperado, NotificationHelper.puedeNotificar(context))
    }

    @Test
    fun pantallaNotificaciones_esAccesibleSinModificarPreferencias() {
        launchDashboard().use {
            esperarDescripcion("Notificaciones")
            composeRule
                .onNodeWithContentDescription("Notificaciones", useUnmergedTree = true)
                .performClick()

            esperarTexto("Notificaciones")
            composeRule
                .onNodeWithText("Activar notificaciones", useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun pruebaSintetica_conPermisoConcedidoSePublicaYSeLimpia() {
        assertTrue(
            "ADB debe conceder POST_NOTIFICATIONS antes de esta prueba",
            NotificationHelper.puedeNotificar(context),
        )

        NotificacionScheduler.dispararPruebaInmediata(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val notificacion = manager.activeNotifications.firstOrNull { it.id == TEST_NOTIFICATION_ID }
        assertNotNull("No se publico la notificacion sintetica", notificacion)
        assertEquals(NotificationHelper.CANAL_ALERTAS, notificacion!!.notification.channelId)
    }

    @Test
    fun rutasDeNotificacion_abrenDestinosPublicosEsperados() {
        val casos = listOf(
            NotificationRoute.ROUTE_TRANSACCIONES to "Transacciones",
            NotificationRoute.ROUTE_RESUMEN to "Resumen por banco",
            NotificationRoute.ROUTE_TARJETAS to "Tarjetas",
            NotificationRoute.ROUTE_HISTORIAL to "Historial de importaciones",
            NotificationRoute.ROUTE_NOTIFICACIONES to "Activar notificaciones",
            NotificationRoute.ROUTE_DASHBOARD to "Dashboard",
        )

        casos.forEach { (route, textoEsperado) ->
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(NotificationRoute.EXTRA_ROUTE, route)
            }
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                esperarTexto(textoEsperado)
            }
        }
    }

    @Test
    fun relanzamiento_conservaSesionYDashboard() {
        launchDashboard().use {
            esperarTexto("Dashboard")
            composeRule.onNodeWithText("Dashboard", useUnmergedTree = true).assertIsDisplayed()
            esperarTexto("Balance neto")
        }
    }

    @Test
    fun lecturaOffline_muestraDashboardYTransaccionesDesdeCache() {
        val offlineEsperado = InstrumentationRegistry.getArguments()
            .getString(ARG_EXPECTED_OFFLINE)
            ?.toBooleanStrictOrNull()
            ?: error("Falta -e $ARG_EXPECTED_OFFLINE true")
        assertTrue("Esta prueba solo debe ejecutarse durante la fase offline", offlineEsperado)

        launchDashboard().use {
            esperarTexto("Dashboard")
            esperarTexto("Balance neto")
            esperarDescripcion("Transacciones")
            composeRule
                .onNodeWithContentDescription("Transacciones", useUnmergedTree = true)
                .performClick()
            esperarTexto("Buscar transacciones")
        }
    }

    private fun launchDashboard(): ActivityScenario<MainActivity> {
        assertNotNull(
            "El Pixel debe conservar la sesion Firebase; la prueba no inicia ni cierra sesion",
            FirebaseAuth.getInstance().currentUser,
        )
        return ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            esperarTexto("Dashboard")
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

    private fun esperarDescripcion(descripcion: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithContentDescription(descripcion, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val TEST_NOTIFICATION_ID = 9_001
        const val ARG_EXPECTED_NOTIFICATION_PERMISSION = "expectedNotificationPermission"
        const val ARG_EXPECTED_OFFLINE = "expectedOffline"
    }
}
