package com.example.flowtrack.domain.repository

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import com.example.flowtrack.data.local.TransaccionesCursor
import com.example.flowtrack.data.firestore.repositories.TransaccionesPage

interface ITransaccionRepository {
    fun observarTransaccionesRecientes(
        uid: String,
        inicio: Instant,
        fin: Instant,
        limite: Int = 100
    ): Flow<List<Transaccion>>

    suspend fun obtenerTransaccionesPage(
        uid: String,
        lastVisible: TransaccionesCursor? = null,
        pageSize: Int = 50,
        inicio: Instant? = null,
        fin: Instant? = null
    ): AppResult<TransaccionesPage>

    suspend fun obtenerTransacciones(
        uid: String,
        inicio: Instant? = null,
        fin: Instant? = null,
        limite: Int = 100,
        cuentaId: String? = null
    ): AppResult<List<Transaccion>>

    suspend fun obtenerDerivadas(
        uid: String,
        padreId: String
    ): AppResult<List<Transaccion>>

    suspend fun actualizarTransaccion(tx: Transaccion): AppResult<Unit>

    suspend fun eliminarTransaccion(uid: String, txId: String): AppResult<Unit>

    suspend fun guardarTransaccionesEnLote(
        uid: String,
        transacciones: List<Transaccion>
    ): AppResult<Unit>
}