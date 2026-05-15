package com.example.flowtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.flowtrack.data.firestore.repositories.ConfiguracionRepository
import com.example.flowtrack.presentation.navigation.AppNavGraph
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
            val uid = auth.currentUser?.uid
            val configFlow = remember(uid) {
                if (uid != null) configuracionRepository.observarConfiguracion(uid) else emptyFlow()
            }
            val config by configFlow.collectAsState(initial = null)
            
            val isDarkTheme = config?.temaOscuro ?: false

            FlowTrackTheme(darkTheme = isDarkTheme) {
                AppNavGraph()
            }
        }
    }
}
