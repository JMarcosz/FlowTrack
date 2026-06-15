package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.ReglaSugeridaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class AnalizarTransaccionesUseCaseTest {

    private lateinit var transaccionRepository: TransaccionRepository
    private lateinit var sugeridaRepository: ReglaSugeridaRepository
    private lateinit var useCase: AnalizarTransaccionesUseCase

    private val uid = "test-uid"

    @Before
    fun setUp() {
        transaccionRepository = mock()
        sugeridaRepository = mock()
        useCase = AnalizarTransaccionesUseCase(transaccionRepository, sugeridaRepository)
    }

    @Test
    fun `cuando hay menos de 5 transacciones sin categoria, no genera sugerencias`() = runBlocking {
        // GIVEN
        val txs = listOf(
            crearTx("id1", "DESC", null),
            crearTx("id2", "DESC", null)
        )
        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(1000), anyOrNull()))
            .thenReturn(AppResult.Success(txs))

        // WHEN
        val result = useCase.ejecutar(uid)

        // THEN
        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data == 0)
    }

    @Test
    fun `cuando hay suficientes transacciones similares, genera sugerencias`() = runBlocking {
        // GIVEN: 6 transacciones con la palabra "SUPERMERCADO"
        val txs = (1..6).map { i ->
            crearTx("id$i", "SUPERMERCADO NACIONAL $i", null)
        }
        whenever(transaccionRepository.obtenerTransacciones(eq(uid), anyOrNull(), anyOrNull(), eq(1000), anyOrNull()))
            .thenReturn(AppResult.Success(txs))
        whenever(sugeridaRepository.guardarReglas(eq(uid), any())).thenReturn(AppResult.Success(Unit))

        // WHEN
        val result = useCase.ejecutar(uid)

        // THEN
        assertTrue(result is AppResult.Success)
    }

    private fun crearTx(id: String, desc: String, catId: String?) = Transaccion(
        id = id,
        uidUsuario = uid,
        cuentaId = "c1",
        bancoCodigo = "POPULAR",
        fecha = Instant.now(),
        fechaPosteo = null,
        descripcionOriginal = desc,
        descripcionNormalizada = desc,
        descripcionCorta = desc,
        monto = BigDecimal("100.00"),
        tipo = TipoTransaccion.DEBITO,
        moneda = Moneda.DOP,
        balanceDespues = null,
        referencia = null,
        serial = null,
        categoriaId = catId,
        categoriaAutomatica = false,
        esDerivada = false,
        transaccionPadreId = null,
        derivadasIds = emptyList(),
        cargaId = "carga1",
        notaUsuario = null,
        metadataBanco = emptyMap(),
        creadoEn = Instant.now()
    )
}
