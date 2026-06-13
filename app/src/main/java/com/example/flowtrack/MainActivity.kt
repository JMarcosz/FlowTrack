package com.example.flowtrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.flowtrack.core.notifications.NotificacionAlarmScheduler
import com.example.flowtrack.core.notifications.NotificationRoute
import com.example.flowtrack.core.workers.NotificacionScheduler
import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.data.firestore.repositories.ConfiguracionRepository
import com.example.flowtrack.data.firestore.repositories.DispositivoPushRepository
import com.example.flowtrack.presentation.navigation.AppNavGraph
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.FlowTrackTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var configuracionRepository: ConfiguracionRepository

    @Inject
    lateinit var alarmScheduler: NotificacionAlarmScheduler

    @Inject
    lateinit var dispositivoPushRepository: DispositivoPushRepository

    @Inject
    lateinit var offlineStore: OfflineStore

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        pendingRoute = intent?.getStringExtra(NotificationRoute.EXTRA_ROUTE)
        setContent {
            var uid by remember { mutableStateOf(auth.currentUser?.uid) }
            var lastUid by remember { mutableStateOf(auth.currentUser?.uid) }
            DisposableEffect(Unit) {
                val listener = FirebaseAuth.AuthStateListener { fa ->
                    uid = fa.currentUser?.uid
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }

            val configFlow = remember(uid) {
                if (uid != null) configuracionRepository.observarConfiguracion(uid!!) else emptyFlow()
            }
            val config by configFlow.collectAsState(initial = null)
            val isDarkTheme = config?.temaOscuro ?: false

            LaunchedEffect(uid, config) {
                if (uid == null) {
                    NotificacionScheduler.cancelarFallback(this@MainActivity)
                } else if (config != null) {
                    alarmScheduler.sincronizar(uid!!)
                }
            }

            LaunchedEffect(uid) {
                if (uid == null && lastUid != null) {
                    alarmScheduler.cancelarExactas(lastUid!!)
                    dispositivoPushRepository.desactivarToken(this@MainActivity, lastUid!!)
                    offlineStore.clearUser(lastUid!!)
                    lastUid = null
                }
                val currentUid = uid ?: return@LaunchedEffect
                if (lastUid != null && lastUid != currentUid) {
                    alarmScheduler.cancelarExactas(lastUid!!)
                    dispositivoPushRepository.desactivarToken(this@MainActivity, lastUid!!)
                    offlineStore.clearUser(lastUid!!)
                }
                val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return@LaunchedEffect
                dispositivoPushRepository.registrarToken(this@MainActivity, currentUid, token)
                lastUid = currentUid
            }

            val startDestination = if (auth.currentUser != null) Screen.Dashboard.route
                                   else Screen.Login.route

            FlowTrackTheme(darkTheme = isDarkTheme) {
                AppNavGraph(
                    startDestination = startDestination,
                    initialRoute = pendingRoute,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(NotificationRoute.EXTRA_ROUTE)
    }
}
