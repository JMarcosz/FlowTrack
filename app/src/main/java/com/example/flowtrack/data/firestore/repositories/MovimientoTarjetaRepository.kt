package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.dto.MovimientoTarjetaDto
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovimientoTarjetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun obtenerMovimientos(
        uid: String,
        inicio: Instant,
        fin: Instant? = null,
    ): AppResult<List<MovimientoTarjeta>> {
        return try {
            var query = firestore
                .collection("usuarios").document(uid)
                .collection("movimientosTarjeta")
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
