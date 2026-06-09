package com.example.flowtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.flowtrack.data.firestore.repositories.ConfiguracionRepository
import com.example.flowtrack.presentation.navigation.AppNavGraph
import com.example.flowtrack.presentation.navigation.Screen
import com.example.flowtrack.ui.theme.FlowTrackTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var configuracionRepository: ConfiguracionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // uid reactivo — se actualiza cuando el usuario inicia/cierra sesión
            var uid by remember { mutableStateOf(auth.currentUser?.uid) }
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

            // Si hay sesión activa al abrir la app, ir directo al Dashboard
            val startDestination = if (auth.currentUser != null) Screen.Dashboard.route
                                   else Screen.Login.route

            FlowTrackTheme(darkTheme = isDarkTheme) {
                AppNavGraph(startDestination = startDestination)
            }
        }
    }
}
