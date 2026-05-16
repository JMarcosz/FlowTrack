package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.OrigenTasa
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TarjetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun obtenerTarjetas(uid: String): AppResult<List<Tarjeta>> {
        return try {
            val snapshot = firestore
                .collection("usuarios").document(uid)
                .collection("tarjetas")
                .orderBy("creadoEn", Query.Direction.ASCENDING)
                .get()
                .await()

            val tarjetas = snapshot.documents.mapNotNull { doc ->
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
                    limiteCredito = BigDecimal.valueOf(doc.getDouble("limiteCredito") ?: 0.0),
                    moneda = Moneda.valueOf(monedaStr),
                    diaCorte = doc.getLong("diaCorte")?.toInt() ?: 1,
                    diaPago = doc.getLong("diaPago")?.toInt() ?: 1,
                    tasaInteresAnual = doc.getDouble("tasaInteresAnual") ?: 0.0,
                    tasaInteresOrigen = OrigenTasa.valueOf(origenStr),
                    estado = EstadoTarjeta.valueOf(estadoStr),
                    titular = doc.getString("titular") ?: "",
                    activa = doc.getBoolean("activa") ?: true,
                    ultimaSincronizacion = doc.getTimestamp("ultimaSincronizacion")?.toDate()?.toInstant(),
                    creadoEn = doc.getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now()
                )
            }
            AppResult.Success(tarjetas)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar tarjetas: ${e.message}", e))
        }
    }

    suspend fun obtenerEstadosTarjeta(uid: String, tarjetaId: String): AppResult<List<EstadoTarjetaSnap>> {
        return try {
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
                    balanceAlCorte = BigDecimal.valueOf(doc.getDouble("balanceAlCorte") ?: 0.0),
                    balanceAnterior = doc.getDouble("balanceAnterior")?.let { BigDecimal.valueOf(it) },
                    pagoMinimo = BigDecimal.valueOf(doc.getDouble("pagoMinimo") ?: 0.0),
                    pagoTotal = BigDecimal.valueOf(doc.getDouble("pagoTotal") ?: 0.0),
                    montoVencido = BigDecimal.valueOf(doc.getDouble("montoVencido") ?: 0.0),
                    balancePromedioDiario = doc.getDouble("balancePromedioDiario")?.let { BigDecimal.valueOf(it) },
                    interesFinanciamiento = doc.getDouble("interesFinanciamiento")?.let { BigDecimal.valueOf(it) },
                    cashbackGanado = doc.getDouble("cashbackGanado")?.let { BigDecimal.valueOf(it) },
                    moneda = Moneda.valueOf(monedaStr),
                    cargaId = doc.getString("cargaId") ?: "",
                    creadoEn = doc.getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now()
                )
            }
            AppResult.Success(estados)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar historial de la tarjeta: ${e.message}", e))
        }
    }

    suspend fun guardarTarjeta(tarjeta: Tarjeta): AppResult<Unit> {
        return try {
            firestore.collection("usuarios").document(tarjeta.uidUsuario)
                .collection("tarjetas").document(tarjeta.id)
                .set(tarjeta.toDto())
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al guardar tarjeta: ${e.message}", e))
        }
    }

    suspend fun actualizarTarjeta(tarjeta: Tarjeta): AppResult<Unit> {
        return try {
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
    suspend fun eliminarTarjeta(uid: String, tarjetaId: String): AppResult<Unit> {
        return try {
            firestore.collection("usuarios").document(uid)
                .collection("tarjetas").document(tarjetaId)
                .update("activa", false)
                .await()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al eliminar tarjeta: ${e.message}", e))
        }
    }
}
