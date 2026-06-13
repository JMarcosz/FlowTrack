package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.data.firestore.mappers.toBigDecimalCompat
import com.example.flowtrack.domain.model.TasaCambio
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TasaCambioRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) {
    suspend fun obtenerTasaDelDia(): AppResult<TasaCambio> {
        val hoy = LocalDate.now(ZoneId.of("America/Santo_Domingo"))
        val docId = hoy.toString()

        return try {
            offlineStore.getTasasCambio(1).firstOrNull { it.fecha == hoy }?.let {
                return AppResult.Success(it)
            }

            val snapshot = firestore.collection("tasasCambio").document(docId).get().await()
            if (snapshot.exists()) {
                val compra = snapshot.get("compra").toBigDecimalCompat() ?: BigDecimal.ZERO
                val venta = snapshot.get("venta").toBigDecimalCompat() ?: BigDecimal.ZERO
                val fuente = snapshot.getString("fuente") ?: "Firebase"
                val tasa = TasaCambio(compra, venta, hoy, fuente)
                offlineStore.upsertTasaCambio(tasa)
                return AppResult.Success(tasa)
            }

            val tasaMock = TasaCambio(BigDecimal("58.50"), BigDecimal("59.10"), hoy, "BCRD (Mock)")
            firestore.collection("tasasCambio").document(docId).set(
                mapOf(
                    "compra" to tasaMock.compra.toPlainString(),
                    "venta" to tasaMock.venta.toPlainString(),
                    "fecha" to tasaMock.fecha.toString(),
                    "fuente" to tasaMock.fuente
                )
            ).await()
            offlineStore.upsertTasaCambio(tasaMock)

            AppResult.Success(tasaMock)
        } catch (e: Exception) {
            val local = offlineStore.getTasasCambio(1).firstOrNull { it.fecha == hoy }
            if (local != null) AppResult.Success(local)
            else AppResult.Error(ErrorApp.FirestoreError("Error obteniendo tasa de cambio: ${e.message}", e))
        }
    }

    suspend fun obtenerHistorico(diasAtras: Int = 30): AppResult<List<TasaCambio>> {
        return try {
            val local = offlineStore.getTasasCambio(diasAtras)
            if (local.isNotEmpty()) return AppResult.Success(local.sortedBy { it.fecha })

            val snapshot = firestore.collection("tasasCambio")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(diasAtras.toLong())
                .get()
                .await()

            val tasas = snapshot.documents.mapNotNull { doc ->
                val compra = doc.get("compra").toBigDecimalCompat() ?: return@mapNotNull null
                val venta = doc.get("venta").toBigDecimalCompat() ?: return@mapNotNull null
                val fechaStr = doc.getString("fecha") ?: doc.id
                runCatching {
                    TasaCambio(compra, venta, LocalDate.parse(fechaStr), doc.getString("fuente") ?: "Firebase")
                }.getOrNull()
            }.sortedBy { it.fecha }

            if (tasas.isNotEmpty()) offlineStore.upsertTasasCambio(tasas)
            AppResult.Success(tasas)
        } catch (e: Exception) {
            val local = offlineStore.getTasasCambio(diasAtras)
            if (local.isNotEmpty()) AppResult.Success(local.sortedBy { it.fecha })
            else AppResult.Error(ErrorApp.FirestoreError("Error obteniendo historico: ${e.message}", e))
        }
    }
}
