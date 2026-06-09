package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.NotificacionConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste la configuración de notificaciones del usuario en
 * `usuarios/{uid}/configuracion/notificaciones`.
 */
@Singleton
class NotificacionConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun docRef(uid: String) = firestore
        .collection("usuarios").document(uid)
        .collection("configuracion").document("notificaciones")

    fun observar(uid: String): Flow<NotificacionConfig> = callbackFlow {
        val listener = docRef(uid).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                trySend(
                    NotificacionConfig(
                        uidUsuario = snapshot.getString("uidUsuario") ?: uid,
                        activa = snapshot.getBoolean("activa") ?: true,
                        pago7dias = snapshot.getBoolean("pago7dias") ?: true,
                        pago3dias = snapshot.getBoolean("pago3dias") ?: true,
                        pago1dia = snapshot.getBoolean("pago1dia") ?: true,
                        pagoMismoDia = snapshot.getBoolean("pagoMismoDia") ?: true,
                        resumenMensual = snapshot.getBoolean("resumenMensual") ?: true,
                        alertasGastosAltos = snapshot.getBoolean("alertasGastosAltos") ?: false,
                        umbralGastoAlto = snapshot.getDouble("umbralGastoAlto")
                            ?.let { BigDecimal.valueOf(it) } ?: BigDecimal("5000"),
                    )
                )
            } else {
                trySend(NotificacionConfig(uidUsuario = uid))
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun guardar(config: NotificacionConfig): AppResult<Unit> {
        return try {
            val dto = mapOf(
                "uidUsuario" to config.uidUsuario,
                "activa" to config.activa,
                "pago7dias" to config.pago7dias,
                "pago3dias" to config.pago3dias,
                "pago1dia" to config.pago1dia,
                "pagoMismoDia" to config.pagoMismoDia,
                "resumenMensual" to config.resumenMensual,
                "alertasGastosAltos" to config.alertasGastosAltos,
                "umbralGastoAlto" to config.umbralGastoAlto.toDouble(),
            )
            docRef(config.uidUsuario).set(dto).await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error guardando notificaciones: ${e.message}", e))
        }
    }
}
