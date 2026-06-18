package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.domain.model.CategoriaMeta
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.model.MovimientoMeta
import com.example.flowtrack.domain.repository.IMetaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class MetasUseCasesTest {

    @Test
    fun `crear meta rechaza monto objetivo menor o igual a cero`() = runBlocking {
        val repository = FakeMetaRepository()
        val useCase = CrearMetaUseCase(repository)

        val result = useCase(
            uid = "uid",
            nombre = "Viaje",
            montoObjetivo = BigDecimal.ZERO,
            categoria = CategoriaMeta.VIAJES,
            cuentaId = "cuenta-1",
            descripcion = null,
            fechaObjetivo = null,
            emoji = "*",
        )

        assertTrue(result is AppResult.Error)
        assertTrue(repository.metas.isEmpty())
    }

    @Test
    fun `crear meta valida persiste meta con categoria y cuenta`() = runBlocking {
        val repository = FakeMetaRepository()
        val useCase = CrearMetaUseCase(repository)

        val result = useCase(
            uid = "uid",
            nombre = "Fondo",
            montoObjetivo = BigDecimal("1000"),
            categoria = CategoriaMeta.FONDO_EMERGENCIA,
            cuentaId = "cuenta-1",
            descripcion = "Reserva",
            fechaObjetivo = null,
            emoji = "*",
        )

        assertTrue(result is AppResult.Success)
        assertEquals(1, repository.metas.size)
        assertEquals(CategoriaMeta.FONDO_EMERGENCIA, repository.metas.single().categoria)
        assertEquals("cuenta-1", repository.metas.single().cuentaId)
        assertEquals(BigDecimal("1000.00"), repository.metas.single().montoObjetivo)
    }

    @Test
    fun `retirar meta rechaza monto mayor al acumulado`() = runBlocking {
        val repository = FakeMetaRepository()
        val useCase = RetirarDeMetaUseCase(repository)
        val meta = meta(montoActual = BigDecimal("100.00"), cuentaId = "cuenta-1")

        val result = useCase(
            uid = "uid",
            meta = meta,
            cuentaId = "cuenta-1",
            monto = BigDecimal("150.00"),
        )

        assertTrue(result is AppResult.Error)
        assertEquals(0, repository.retiros)
    }

    @Test
    fun `retirar meta valido delega al repositorio`() = runBlocking {
        val repository = FakeMetaRepository()
        val useCase = RetirarDeMetaUseCase(repository)
        val meta = meta(montoActual = BigDecimal("100.00"), cuentaId = "cuenta-1")

        val result = useCase(
            uid = "uid",
            meta = meta,
            cuentaId = "cuenta-1",
            monto = BigDecimal("40.00"),
        )

        assertTrue(result is AppResult.Success)
        assertEquals(1, repository.retiros)
        assertEquals(BigDecimal("60.00"), (result as AppResult.Success).data.montoActual)
    }
}

private fun meta(
    montoActual: BigDecimal,
    cuentaId: String?,
) = Meta(
    id = "meta-1",
    uidUsuario = "uid",
    nombre = "Meta",
    emoji = "*",
    montoObjetivo = BigDecimal("1000.00"),
    montoActual = montoActual,
    fechaLimite = null,
    activa = true,
    creadoEn = Instant.EPOCH,
    cuentaId = cuentaId,
)

private class FakeMetaRepository : IMetaRepository {
    val metas = mutableListOf<Meta>()
    var retiros = 0

    override fun observarMetas(uid: String): Flow<List<Meta>> = flowOf(metas)

    override suspend fun obtenerMetas(uid: String): AppResult<List<Meta>> = AppResult.Success(metas)

    override suspend fun obtenerMeta(uid: String, metaId: String): AppResult<Meta?> =
        AppResult.Success(metas.firstOrNull { it.id == metaId })

    override suspend fun guardarMeta(meta: Meta): AppResult<Unit> {
        metas += meta
        return AppResult.Success(Unit)
    }

    override suspend fun cancelarMeta(uid: String, metaId: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun depositar(
        uid: String,
        metaId: String,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String,
    ): AppResult<Meta> = AppResult.Success(meta(BigDecimal("0.00"), cuentaId))

    override suspend fun retirar(
        uid: String,
        metaId: String,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String,
    ): AppResult<Meta> {
        retiros += 1
        return AppResult.Success(meta(BigDecimal("100.00") - monto, cuentaId))
    }

    override fun observarMovimientos(uid: String, metaId: String): Flow<List<MovimientoMeta>> = flowOf(emptyList())
}
