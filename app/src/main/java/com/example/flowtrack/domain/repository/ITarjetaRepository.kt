package com.example.flowtrack.domain.repository

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Tarjeta
import kotlinx.coroutines.flow.Flow

interface ITarjetaRepository {
    fun observarTarjetas(uid: String): Flow<List<Tarjeta>>
    suspend fun obtenerTarjetas(uid: String): AppResult<List<Tarjeta>>
    suspend fun obtenerEstadosTarjeta(uid: String, tarjetaId: String): AppResult<List<EstadoTarjetaSnap>>
    suspend fun guardarTarjeta(tarjeta: Tarjeta): AppResult<Unit>
    suspend fun actualizarTarjeta(tarjeta: Tarjeta): AppResult<Unit>
    suspend fun eliminarTarjeta(uid: String, tarjetaId: String): AppResult<Unit>
}