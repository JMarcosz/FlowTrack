package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.data.firestore.mappers.toNotificacionConfigDto
import com.example.flowtrack.domain.model.NotificacionConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste la configuración de notificaciones del usuario en
 * `usuarios/{uid}/configuracion/notificaciones`.
 */
@Singleton
class NotificacionConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    private fun docRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("configuracion").document("notificaciones")

    fun observar(uid: String): Flow<NotificacionConfig> =
        offlineStore.observeNotificacionConfig(uid)
            .onStart {
                if (!offlineStore.hasRecords("NOTIFICACION_CONFIG", uid)) {
                    syncRemote(uid)
                }
            }

    suspend fun guardar(config: NotificacionConfig): AppResult<Unit> {
        return try {
            offlineStore.upsertNotificacionConfig(config)
            docRef(config.uidUsuario).set(config.toDto()).await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error guardando notificaciones: ${e.message}", e))
        }
    }

    private suspend fun syncRemote(uid: String) {
        runCatching {
            val snapshot = docRef(uid).get().await()
            val config = snapshot
                .takeIf { it.exists() }
                ?.toNotificacionConfigDto()
                ?.toDomain()
                ?: NotificacionConfig(uidUsuario = uid)
            offlineStore.upsertNotificacionConfig(config)
        }
    }
}
