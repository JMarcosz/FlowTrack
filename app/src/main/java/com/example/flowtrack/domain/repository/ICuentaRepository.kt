package com.example.flowtrack.domain.repository

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.Cuenta
import kotlinx.coroutines.flow.Flow

interface ICuentaRepository {
    fun observarCuentas(uid: String): Flow<List<Cuenta>>
    suspend fun obtenerCuentas(uid: String): AppResult<List<Cuenta>>
    suspend fun guardarCuenta(cuenta: Cuenta): AppResult<Unit>
    suspend fun actualizarCuenta(cuenta: Cuenta): AppResult<Unit>
    suspend fun eliminarCuenta(uid: String, cuentaId: String): AppResult<Unit>
}