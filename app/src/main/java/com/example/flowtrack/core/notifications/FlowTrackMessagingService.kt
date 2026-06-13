package com.example.flowtrack.core.notifications

import android.app.PendingIntent
import android.content.Intent
import com.example.flowtrack.data.firestore.repositories.DispositivoPushRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FlowTrackMessagingService : FirebaseMessagingService() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var dispositivoPushRepository: DispositivoPushRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = auth.currentUser?.uid ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            dispositivoPushRepository.registrarToken(applicationContext, uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val titulo = data["title"] ?: message.notification?.title ?: "FlowTrack"
        val cuerpo = data["body"] ?: message.notification?.body ?: "Tienes una notificación nueva."
        val canal = data["channelId"] ?: NotificationHelper.CANAL_PUSH
        val route = data["route"] ?: NotificationRoute.ROUTE_DASHBOARD
        val id = data["notificationId"]?.toIntOrNull() ?: message.messageId?.hashCode() ?: System.currentTimeMillis().toInt()

        val intent = Intent(applicationContext, com.example.flowtrack.MainActivity::class.java).apply {
            putExtra(NotificationRoute.EXTRA_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        NotificationHelper.notificar(
            context = applicationContext,
            canalId = canal,
            notifId = id,
            titulo = titulo,
            texto = cuerpo,
            contentIntent = pendingIntent,
        )
    }
}
