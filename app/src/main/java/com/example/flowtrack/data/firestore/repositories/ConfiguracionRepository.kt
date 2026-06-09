package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.ConfiguracionUsuario
import com.example.flowtrack.domain.model.Moneda
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfiguracionRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun observarConfiguracion(uid: String): Flow<ConfiguracionUsuario> = callbackFlow {
        val listener = firestore.collection("usuarios").document(uid).collection("configuracion").document("preferencias")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // No romper el flow — el listener se cancela limpiamente cuando uid cambia
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val config = ConfiguracionUsuario(
                        uidUsuario = snapshot.getString("uidUsuario") ?: uid,
                        idioma = snapshot.getString("idioma") ?: "es-DO",
                        formatoFecha = snapshot.getString("formatoFecha") ?: "dd/MM/yyyy",
                        formatoMoneda = snapshot.getString("formatoMoneda") ?: "RD$ 0.00",
                        monedaPredeterminada = Moneda.valueOf(snapshot.getString("monedaPredeterminada") ?: "DOP"),
                        temaOscuro = snapshot.getBoolean("temaOscuro") ?: false,
                        ultimoBackup = snapshot.getTimestamp("ultimoBackup")?.toDate()?.toInstant()
                    )
                    trySend(config)
                } else {
                    // Retornar default si no existe
                    trySend(ConfiguracionUsuario(uidUsuario = uid))
                }
            }
            
        awaitClose { listener.remove() }
    }

    suspend fun actualizarConfiguracion(config: ConfiguracionUsuario): AppResult<Unit> {
        return try {
            val dto = mapOf(
                "uidUsuario" to config.uidUsuario,
                "idioma" to config.idioma,
                "formatoFecha" to config.formatoFecha,
                "formatoMoneda" to config.formatoMoneda,
                "monedaPredeterminada" to config.monedaPredeterminada.name,
                "temaOscuro" to config.temaOscuro,
                "ultimoBackup" to config.ultimoBackup?.let { Timestamp(it.epochSecond, it.nano) }
            )
            firestore.collection("usuarios").document(config.uidUsuario)
                .collection("configuracion").document("preferencias")
                .set(dto)
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error guardando configuracion: ${e.message}", e))
        }
    }
}
