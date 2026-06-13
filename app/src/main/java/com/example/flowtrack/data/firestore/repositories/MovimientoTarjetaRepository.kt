package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.money
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovimientoTarjetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid).collection("movimientosTarjeta")

    /** Flow reactivo con límite. Emite desde cache local inmediatamente, actualiza con cambios del servidor. */
    fun observarMovimientos(
        uid: String,
        inicio: Instant,
        fin: Instant,
        limite: Int = 200,
    ): Flow<List<MovimientoTarjeta>> = offlineStore.observeMovimientosTarjeta(uid, inicio, fin, limite)
        .onStart {
            if (!offlineStore.hasRecords("MOVIMIENTO_TARJETA", uid)) {
                try {
                    syncRemote(uid, inicio, fin, limite)
                } catch (e: Exception) {
                    android.util.Log.e("MovimientoRepository", "Error syncing movimientos in background", e)
                }
            }
        }

    /** One-shot: úsalo para agregaciones en use-cases. Para UI reactiva usa [observarMovimientos]. */
    suspend fun obtenerMovimientos(
        uid: String,
        inicio: Instant,
        fin: Instant? = null,
    ): AppResult<List<MovimientoTarjeta>> {
        return try {
            val local = offlineStore.getMovimientosTarjeta(uid, inicio, fin, 0)
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            syncRemote(uid, inicio, fin, 0)
            AppResult.Success(offlineStore.getMovimientosTarjeta(uid, inicio, fin, 0))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar movimientos de tarjeta: ${e.message}", e))
        }
    }

    private suspend fun syncRemote(
        uid: String,
        inicio: Instant,
        fin: Instant?,
        limite: Int,
    ) {
        var query = colRef(uid)
            .whereGreaterThanOrEqualTo("fechaTransaccion", com.google.firebase.Timestamp(inicio.epochSecond, inicio.nano))
            .orderBy("fechaTransaccion", Query.Direction.ASCENDING)

        if (fin != null) {
            query = query.whereLessThanOrEqualTo("fechaTransaccion", com.google.firebase.Timestamp(fin.epochSecond, fin.nano))
        }

        if (limite > 0) {
            query = query.limit(limite.toLong())
        }

        val snapshot = query.get().await()
        val movimientos = snapshot.documents.mapNotNull { doc -> doc.toMovimientoCompat() }
        if (movimientos.isNotEmpty()) offlineStore.upsertMovimientosTarjeta(movimientos)
    }
}

private fun DocumentSnapshot.toMovimientoCompat(): MovimientoTarjeta? = runCatching {
    MovimientoTarjeta(
        id = id,
        uidUsuario = getString("uidUsuario") ?: return null,
        tarjetaId = getString("tarjetaId") ?: return null,
        bancoCodigo = getString("bancoCodigo") ?: "",
        fechaTransaccion = getTimestamp("fechaTransaccion")?.toDate()?.toInstant() ?: Instant.now(),
        fechaPosteo = getTimestamp("fechaPosteo")?.toDate()?.toInstant(),
        descripcionOriginal = getString("descripcionOriginal") ?: "",
        descripcionNormalizada = getString("descripcionNormalizada") ?: "",
        monto = money("monto") ?: java.math.BigDecimal.ZERO.setScale(2),
        montoUsd = money("montoUsd"),
        tipoMovimiento = com.example.flowtrack.domain.model.TipoMovimientoTarjeta.valueOf(getString("tipoMovimiento") ?: return null),
        moneda = com.example.flowtrack.domain.model.Moneda.valueOf(getString("moneda") ?: "DOP"),
        numeroAutorizacion = getString("numeroAutorizacion"),
        categoriaId = getString("categoriaId"),
        categoriaAutomatica = getBoolean("categoriaAutomatica") ?: false,
        cargaId = getString("cargaId") ?: "",
        metadataBanco = (get("metadataBanco") as? Map<*, *>)?.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? String ?: return@mapNotNull null
            key to value
        }?.toMap().orEmpty(),
        creadoEn = getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now(),
    )
}.getOrNull()
