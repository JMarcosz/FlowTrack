package com.example.flowtrack.domain.repository

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.model.MovimientoMeta
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface IMetaRepository {
    fun observarMetas(uid: String): Flow<List<Meta>>
    suspend fun obtenerMetas(uid: String): AppResult<List<Meta>>
    suspend fun obtenerMeta(uid: String, metaId: String): AppResult<Meta?>
    suspend fun guardarMeta(meta: Meta): AppResult<Unit>
    suspend fun cancelarMeta(uid: String, metaId: String): AppResult<Unit>
    suspend fun depositar(
        uid: String,
        metaId: String,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String,
    ): AppResult<Meta>
    suspend fun retirar(
        uid: String,
        metaId: String,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String,
    ): AppResult<Meta>
    fun observarMovimientos(uid: String, metaId: String): Flow<List<MovimientoMeta>>
}
