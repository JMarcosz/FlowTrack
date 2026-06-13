package com.example.flowtrack.data.firestore.repositories

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.DispositivoPush
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DispositivoPushRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun docRef(uid: String, dispositivoId: String) = firestore
        .collection("usuarios").document(uid)
        .collection("dispositivos").document(dispositivoId)

    suspend fun registrarToken(
        context: Context,
        uid: String,
        tokenFcm: String,
    ): AppResult<Unit> = try {
        val dispositivoId = dispositivoId(context)
        val dispositivo = DispositivoPush(
            id = dispositivoId,
            uidUsuario = uid,
            tokenFcm = tokenFcm,
            activo = true,
            actualizadoEn = Instant.now(),
            ultimoUsuarioUid = uid,
            versionApp = versionApp(context),
            modeloDispositivo = android.os.Build.MODEL,
        )
        docRef(uid, dispositivoId).set(dispositivo.toDto()).await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error guardando token FCM: ${e.message}", e))
    }

    suspend fun desactivarToken(
        context: Context,
        uid: String,
    ): AppResult<Unit> = try {
        val dispositivoId = dispositivoId(context)
        docRef(uid, dispositivoId)
            .set(
                mapOf(
                    "id" to dispositivoId,
                    "uidUsuario" to uid,
                    "activo" to false,
                    "actualizadoEn" to com.google.firebase.Timestamp.now(),
                ),
                SetOptions.merge(),
            )
            .await()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error desactivando token FCM: ${e.message}", e))
    }

    @SuppressLint("HardwareIds")
    private fun dispositivoId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "desconocido"

    private fun versionApp(context: Context): String? = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    }.getOrNull()
}
