package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.money
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.repository.ICuentaRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuentaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) : ICuentaRepository {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid).collection("cuentas")

    /** Flow reactivo: emite desde cache local inmediatamente, luego actualiza si hay cambios. */
    override fun observarCuentas(uid: String): Flow<List<Cuenta>> =
        offlineStore.observeCuentas(uid)
            .onStart {
                if (!offlineStore.hasRecords("CUENTA", uid)) {
                    try {
                        syncRemote(uid)
                    } catch (e: Exception) {
                        android.util.Log.e("CuentaRepository", "Error syncing cuentas in background", e)
                    }
                }
            }

    override suspend fun obtenerCuentas(uid: String): AppResult<List<Cuenta>> {
        return try {
            val local = offlineStore.getCuentas(uid)
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            syncRemote(uid)
            AppResult.Success(offlineStore.getCuentas(uid))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar cuentas: ${e.message}", e))
        }
    }

    override suspend fun guardarCuenta(cuenta: Cuenta): AppResult<Unit> {
        return try {
            offlineStore.upsertCuenta(cuenta)
            firestore.collection("usuarios").document(cuenta.uidUsuario)
                .collection("cuentas").document(cuenta.id)
                .set(cuenta.toDto())
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al guardar cuenta: ${e.message}", e))
        }
    }

    override suspend fun actualizarCuenta(cuenta: Cuenta): AppResult<Unit> {
        return try {
            offlineStore.upsertCuenta(cuenta)
            firestore.collection("usuarios").document(cuenta.uidUsuario)
                .collection("cuentas").document(cuenta.id)
                .set(cuenta.toDto(), SetOptions.merge())
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al actualizar cuenta: ${e.message}", e))
        }
    }

    /** Soft-delete: marca como inactiva y oculta en dashboard. */
    override suspend fun eliminarCuenta(uid: String, cuentaId: String): AppResult<Unit> {
        return try {
            offlineStore.deactivateCuenta(uid, cuentaId)
            firestore.collection("usuarios").document(uid)
                .collection("cuentas").document(cuentaId)
                .update(
                    mapOf(
                        "activa" to false,
                        "mostrarEnDashboard" to false,
                    )
                )
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar cuenta: ${e.message}", e))
        }
    }

    private suspend fun syncRemote(uid: String) {
        val snapshot = colRef(uid)
            .orderBy("creadoEn", Query.Direction.ASCENDING)
            .get()
            .await()
        val cuentas = snapshot.documents.mapNotNull { doc -> doc.toCuentaCompat(uid) }
        if (cuentas.isNotEmpty()) offlineStore.upsertCuentas(cuentas)
    }
}

private fun DocumentSnapshot.toCuentaCompat(uidFallback: String): Cuenta? = runCatching {
    Cuenta(
        id = id,
        uidUsuario = getString("uidUsuario") ?: uidFallback,
        bancoCodigo = getString("bancoCodigo") ?: "",
        numeroCuenta = getString("numeroCuenta") ?: "",
        numeroCuentaCompleto = getString("numeroCuentaCompleto"),
        alias = getString("alias") ?: "",
        tipoCuenta = com.example.flowtrack.domain.model.TipoCuenta.valueOf(getString("tipoCuenta") ?: return null),
        moneda = com.example.flowtrack.domain.model.Moneda.valueOf(getString("moneda") ?: return null),
        balanceActual = money("balanceActual"),
        balanceAlCorte = money("balanceAlCorte"),
        fechaUltimoCorte = getTimestamp("fechaUltimoCorte")?.toDate()?.toInstant(),
        titular = getString("titular") ?: "",
        activa = getBoolean("activa") ?: true,
        mostrarEnDashboard = getBoolean("mostrarEnDashboard") ?: true,
        ultimaSincronizacion = getTimestamp("ultimaSincronizacion")?.toDate()?.toInstant(),
        creadoEn = getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now(),
    )
}.getOrNull()
