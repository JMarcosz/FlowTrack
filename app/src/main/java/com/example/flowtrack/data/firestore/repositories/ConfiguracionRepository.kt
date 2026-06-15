package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toConfiguracionUsuarioDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.ConfiguracionUsuario
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfiguracionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    private fun docRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("configuracion").document("preferencias")

    fun observarConfiguracion(uid: String, temaOscuroInicial: Boolean? = null): Flow<ConfiguracionUsuario> =
        offlineStore.observeConfiguracion(uid)
            .onStart {
                if (!offlineStore.hasRecords("CONFIGURACION", uid)) {
                    syncRemote(uid, temaOscuroInicial)
                }
            }

    suspend fun actualizarConfiguracion(config: ConfiguracionUsuario): AppResult<Unit> = try {
        offlineStore.upsertConfiguracion(config)
        docRef(config.uidUsuario).set(config.toDto()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error guardando configuracion: ${e.message}", e))
    }

    private suspend fun syncRemote(uid: String, temaOscuroInicial: Boolean? = null) {
        runCatching {
            val snapshot = docRef(uid).get().await()
            val config = snapshot
                .takeIf { it.exists() }
                ?.toConfiguracionUsuarioDto()
                ?.toDomain()
                ?: ConfiguracionUsuario(
                    uidUsuario = uid,
                    temaOscuro = temaOscuroInicial ?: false,
                )
            offlineStore.upsertConfiguracion(config)
        }
    }
}
