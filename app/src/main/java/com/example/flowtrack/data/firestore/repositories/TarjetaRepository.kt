package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.money
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.OrigenTasa
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.repository.ITarjetaRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TarjetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) : ITarjetaRepository {
    /** Flow reactivo: emite desde cache local inmediatamente, luego actualiza si hay cambios. */
    override fun observarTarjetas(uid: String): Flow<List<Tarjeta>> =
        offlineStore.observeTarjetas(uid)
            .onStart {
                if (!offlineStore.hasRecords("TARJETA", uid)) {
                    try {
                        syncTarjetas(uid)
                    } catch (e: Exception) {
                        android.util.Log.e("TarjetaRepository", "Error syncing tarjetas in background", e)
                    }
                }
            }

    private fun mapDocToTarjeta(doc: DocumentSnapshot, uid: String): Tarjeta? = runCatching {
        val monedaStr = doc.getString("moneda") ?: "DOP"
        val origenStr = doc.getString("tasaInteresOrigen") ?: "AUTO_EXTRAIDA"
        val estadoStr = doc.getString("estado") ?: "ACTIVO"

        Tarjeta(
            id = doc.id,
            uidUsuario = doc.getString("uidUsuario") ?: uid,
            bancoCodigo = doc.getString("bancoCodigo") ?: "",
            ultimos4 = doc.getString("ultimos4") ?: "",
            alias = doc.getString("alias") ?: "",
            tipoRed = doc.getString("tipoRed"),
            limiteCredito = doc.money("limiteCredito") ?: BigDecimal.ZERO.setScale(2),
            moneda = Moneda.valueOf(monedaStr),
            diaCorte = doc.getLong("diaCorte")?.toInt() ?: 1,
            diaPago = doc.getLong("diaPago")?.toInt() ?: 1,
            tasaInteresAnual = doc.money("tasaInteresAnual")?.toDouble() ?: 0.0,
            tasaInteresOrigen = OrigenTasa.valueOf(origenStr),
            estado = EstadoTarjeta.valueOf(estadoStr),
            titular = doc.getString("titular") ?: "",
            activa = doc.getBoolean("activa") ?: true,
            ultimaSincronizacion = doc.getTimestamp("ultimaSincronizacion")?.toDate()?.toInstant(),
            creadoEn = doc.getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now(),
        )
    }.getOrNull()

    override suspend fun obtenerTarjetas(uid: String): AppResult<List<Tarjeta>> {
        return try {
            val local = offlineStore.getTarjetas(uid)
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            syncTarjetas(uid)
            AppResult.Success(offlineStore.getTarjetas(uid))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar tarjetas: ${e.message}", e))
        }
    }

    override suspend fun obtenerEstadosTarjeta(uid: String, tarjetaId: String): AppResult<List<EstadoTarjetaSnap>> {
        return try {
            val local = offlineStore.getEstadosTarjeta(uid, tarjetaId)
            if (local.isNotEmpty()) {
                return AppResult.Success(local)
            }

            syncEstados(uid, tarjetaId)
            AppResult.Success(offlineStore.getEstadosTarjeta(uid, tarjetaId))
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar historial de la tarjeta: ${e.message}", e))
        }
    }

    override suspend fun guardarTarjeta(tarjeta: Tarjeta): AppResult<Unit> {
        return try {
            offlineStore.upsertTarjeta(tarjeta)
            firestore.collection("usuarios").document(tarjeta.uidUsuario)
                .collection("tarjetas").document(tarjeta.id)
                .set(tarjeta.toDto())
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al guardar tarjeta: ${e.message}", e))
        }
    }

    override suspend fun actualizarTarjeta(tarjeta: Tarjeta): AppResult<Unit> {
        return try {
            offlineStore.upsertTarjeta(tarjeta)
            firestore.collection("usuarios").document(tarjeta.uidUsuario)
                .collection("tarjetas").document(tarjeta.id)
                .set(tarjeta.toDto(), SetOptions.merge())
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al actualizar tarjeta: ${e.message}", e))
        }
    }

    /** Soft-delete: marca la tarjeta como inactiva. */
    override suspend fun eliminarTarjeta(uid: String, tarjetaId: String): AppResult<Unit> {
        return try {
            offlineStore.deactivateTarjeta(uid, tarjetaId)
            firestore.collection("usuarios").document(uid)
                .collection("tarjetas").document(tarjetaId)
                .update("activa", false)
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar tarjeta: ${e.message}", e))
        }
    }

    private suspend fun syncTarjetas(uid: String) {
        val snapshot = firestore
            .collection("usuarios").document(uid)
            .collection("tarjetas")
            .orderBy("creadoEn", Query.Direction.ASCENDING)
            .get()
            .await()
        val tarjetas = snapshot.documents.mapNotNull { doc -> mapDocToTarjeta(doc, uid) }
        if (tarjetas.isNotEmpty()) offlineStore.upsertTarjetas(tarjetas)
    }

    private suspend fun syncEstados(uid: String, tarjetaId: String) {
        val snapshot = firestore
            .collection("usuarios").document(uid)
            .collection("estadosTarjeta")
            .whereEqualTo("tarjetaId", tarjetaId)
            .orderBy("fechaCorte", Query.Direction.DESCENDING)
            .get()
            .await()
        val estados = snapshot.documents.mapNotNull { doc ->
            val monedaStr = doc.getString("moneda") ?: "DOP"
            EstadoTarjetaSnap(
                id = doc.id,
                uidUsuario = doc.getString("uidUsuario") ?: uid,
                tarjetaId = doc.getString("tarjetaId") ?: tarjetaId,
                fechaCorte = doc.getTimestamp("fechaCorte")?.toDate()?.toInstant() ?: Instant.now(),
                fechaLimitePago = doc.getTimestamp("fechaLimitePago")?.toDate()?.toInstant() ?: Instant.now(),
                periodoInicio = doc.getTimestamp("periodoInicio")?.toDate()?.toInstant() ?: Instant.now(),
                periodoFin = doc.getTimestamp("periodoFin")?.toDate()?.toInstant() ?: Instant.now(),
                balanceAlCorte = doc.money("balanceAlCorte") ?: BigDecimal.ZERO.setScale(2),
                balanceAnterior = doc.money("balanceAnterior"),
                pagoMinimo = doc.money("pagoMinimo") ?: BigDecimal.ZERO.setScale(2),
                pagoTotal = doc.money("pagoTotal") ?: BigDecimal.ZERO.setScale(2),
                montoVencido = doc.money("montoVencido") ?: BigDecimal.ZERO.setScale(2),
                balancePromedioDiario = doc.money("balancePromedioDiario"),
                interesFinanciamiento = doc.money("interesFinanciamiento"),
                cashbackGanado = doc.money("cashbackGanado"),
                moneda = Moneda.valueOf(monedaStr),
                cargaId = doc.getString("cargaId") ?: "",
                creadoEn = doc.getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now()
            )
        }
        if (estados.isNotEmpty()) offlineStore.upsertEstadosTarjeta(estados)
    }
}