package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.firestore.asListFlow
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.dto.MovimientoTarjetaDto
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovimientoTarjetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun colRef(uid: String) = firestore
        .collection("usuarios").document(uid).collection("movimientosTarjeta")

    /** Flow reactivo con límite. Emite desde cache local inmediatamente, actualiza con cambios del servidor. */
    fun observarMovimientos(
        uid: String,
        inicio: Instant,
        fin: Instant,
        limite: Int = 200,
    ): Flow<List<MovimientoTarjeta>> = colRef(uid)
        .whereGreaterThanOrEqualTo("fechaTransaccion", Timestamp(inicio.epochSecond, inicio.nano))
        .whereLessThanOrEqualTo("fechaTransaccion", Timestamp(fin.epochSecond, fin.nano))
        .orderBy("fechaTransaccion", Query.Direction.ASCENDING)
        .limit(limite.toLong())
        .asListFlow(MovimientoTarjetaDto::class.java)
        .map { dtos -> dtos.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }
        .flowOn(Dispatchers.Default)

    /** One-shot: úsalo para agregaciones en use-cases. Para UI reactiva usa [observarMovimientos]. */
    suspend fun obtenerMovimientos(
        uid: String,
        inicio: Instant,
        fin: Instant? = null,
    ): AppResult<List<MovimientoTarjeta>> {
        return try {
            var query = colRef(uid)
                .whereGreaterThanOrEqualTo("fechaTransaccion", Timestamp(inicio.epochSecond, inicio.nano))
                .orderBy("fechaTransaccion", Query.Direction.ASCENDING)

            if (fin != null) {
                query = query.whereLessThanOrEqualTo("fechaTransaccion", Timestamp(fin.epochSecond, fin.nano))
            }

            val snapshot = query.get().await()
            val movimientos = snapshot.documents.mapNotNull { doc ->
                runCatching { doc.toObject(MovimientoTarjetaDto::class.java)?.toDomain() }.getOrNull()
            }
            AppResult.Success(movimientos)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error al cargar movimientos de tarjeta: ${e.message}", e))
        }
    }
}
