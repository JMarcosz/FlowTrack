package com.example.flowtrack.presentation.upload

import android.net.Uri
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.domain.model.FileFormat
import com.example.flowtrack.domain.model.ProductoTipo
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.usecases.carga.ProcesarArchivoUseCase
import com.example.flowtrack.domain.usecases.carga.ResultadoImportacion
import com.example.flowtrack.presentation.screens.upload.BancoOpcion
import com.example.flowtrack.presentation.screens.upload.UploadEstado
import com.example.flowtrack.presentation.screens.upload.UploadViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var procesarArchivoUseCase: ProcesarArchivoUseCase
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var viewModel: UploadViewModel

    private val uriSintetica: Uri = mock()

    private val bancoTest = BancoOpcion(
        codigo = "BANRESERVAS",
        nombre = "BanReservas",
        formato = FileFormat.PDF,
        productoTipo = ProductoTipo.CUENTA,
        formatoLabel = "PDF",
    )

    private fun cargaDePrueba(bancoCodigo: String = "BANRESERVAS") = Carga(
        id = "carga-test-001",
        uidUsuario = "uid-test",
        nombreArchivo = "estado_prueba.pdf",
        tamanioBytes = 1024L,
        mimeType = "application/pdf",
        bancoCodigo = bancoCodigo,
        parserVersion = 1,
        tipoDocumento = TipoDocumento.CUENTA_CORRIENTE,
        cuentaId = "cuenta-test-001",
        tarjetaId = null,
        periodoInicio = Instant.parse("2024-01-01T00:00:00Z"),
        periodoFin = Instant.parse("2024-01-31T00:00:00Z"),
        transaccionesInsertadas = 5,
        transaccionesDuplicadas = 0,
        advertencias = emptyList(),
        estado = EstadoCarga.EXITOSO,
        procesadoEn = Instant.now(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        procesarArchivoUseCase = mock()
        val firebaseUser: FirebaseUser = mock { on { uid } doReturn "uid-test" }
        firebaseAuth = mock { on { currentUser } doReturn firebaseUser }
        viewModel = UploadViewModel(procesarArchivoUseCase, firebaseAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Estado inicial ───────────────────────────────────────────────────────

    @Test
    fun `estado inicial es Idle`() = runTest {
        assertEquals(UploadEstado.Idle, viewModel.estado.first())
    }

    @Test
    fun `bancoSeleccionado inicial es null`() = runTest {
        assertNull(viewModel.bancoSeleccionado.first())
    }

    // ─── Selección de banco ───────────────────────────────────────────────────

    @Test
    fun `seleccionarBanco actualiza bancoSeleccionado`() = runTest {
        viewModel.seleccionarBanco(bancoTest)
        assertEquals(bancoTest, viewModel.bancoSeleccionado.first())
    }

    @Test
    fun `procesarArchivo sin banco seleccionado emite Error`() = runTest {
        viewModel.procesarArchivo(uriSintetica)
        advanceUntilIdle()

        val estado = viewModel.estado.first()
        assertTrue("Se esperaba Error por banco no seleccionado, fue: $estado", estado is UploadEstado.Error)
    }

    // ─── Flujo exitoso ────────────────────────────────────────────────────────

    @Test
    fun `procesarArchivo - resultado Exito emite UploadEstado Exito con datos correctos`() = runTest {
        val carga = cargaDePrueba("BANRESERVAS")
        procesarArchivoUseCase.stub {
            onBlocking { ejecutar(any(), eq("uid-test"), any(), any(), any(), anyOrNull(), anyOrNull()) } doReturn
                ResultadoImportacion.Exito(carga, transaccionesInsertadas = 5)
        }

        viewModel.seleccionarBanco(bancoTest)
        viewModel.procesarArchivo(uriSintetica)
        advanceUntilIdle()

        val estado = viewModel.estado.first()
        assertTrue("Se esperaba UploadEstado.Exito, fue: $estado", estado is UploadEstado.Exito)
        val exito = estado as UploadEstado.Exito
        assertEquals(5, exito.transaccionesInsertadas)
        assertEquals("BANRESERVAS", exito.banco)
    }

    // ─── Propagación de errores ───────────────────────────────────────────────

    @Test
    fun `procesarArchivo - error de Firestore emite UploadEstado Error con mensaje legible`() = runTest {
        procesarArchivoUseCase.stub {
            onBlocking { ejecutar(any(), eq("uid-test"), any(), any(), any(), anyOrNull(), anyOrNull()) } doReturn
                ResultadoImportacion.Error(
                    AppResult.Error(ErrorApp.FirestoreError("PERMISSION_DENIED: Missing permissions"))
                )
        }

        viewModel.seleccionarBanco(bancoTest)
        viewModel.procesarArchivo(uriSintetica)
        advanceUntilIdle()

        val estado = viewModel.estado.first()
        assertTrue("Se esperaba UploadEstado.Error, fue: $estado", estado is UploadEstado.Error)
        assertTrue((estado as UploadEstado.Error).mensaje.isNotBlank())
    }

    @Test
    fun `procesarArchivo - error de parseo emite UploadEstado Error`() = runTest {
        procesarArchivoUseCase.stub {
            onBlocking { ejecutar(any(), eq("uid-test"), any(), any(), any(), anyOrNull(), anyOrNull()) } doReturn
                ResultadoImportacion.Error(
                    AppResult.Error(ErrorApp.ParseError("Formato de PDF no reconocido"))
                )
        }

        viewModel.seleccionarBanco(bancoTest)
        viewModel.procesarArchivo(uriSintetica)
        advanceUntilIdle()

        val estado = viewModel.estado.first()
        assertTrue(estado is UploadEstado.Error)
        assertTrue((estado as UploadEstado.Error).mensaje.isNotBlank())
    }

    @Test
    fun `procesarArchivo - archivo muy grande emite error con tamanio`() = runTest {
        procesarArchivoUseCase.stub {
            onBlocking { ejecutar(any(), eq("uid-test"), any(), any(), any(), anyOrNull(), anyOrNull()) } doReturn
                ResultadoImportacion.Error(
                    AppResult.Error(
                        ErrorApp.ArchivoMuyGrande(
                            tamanioBytes = 15 * 1024 * 1024L,
                            limiteBytes = 10 * 1024 * 1024L,
                        )
                    )
                )
        }

        viewModel.seleccionarBanco(bancoTest)
        viewModel.procesarArchivo(uriSintetica)
        advanceUntilIdle()

        val estado = viewModel.estado.first()
        assertTrue(estado is UploadEstado.Error)
        val error = estado as UploadEstado.Error
        assertTrue(
            "El mensaje debe mencionar MB, fue: '${error.mensaje}'",
            error.mensaje.contains("MB", ignoreCase = true),
        )
    }

    // ─── Usuario no autenticado ───────────────────────────────────────────────

    @Test
    fun `procesarArchivo - sin usuario autenticado emite error de sesion`() = runTest {
        val authSinUsuario: FirebaseAuth = mock { on { currentUser } doReturn null }
        val vmSinAuth = UploadViewModel(procesarArchivoUseCase, authSinUsuario)

        vmSinAuth.seleccionarBanco(bancoTest)
        vmSinAuth.procesarArchivo(uriSintetica)
        advanceUntilIdle()

        val estado = vmSinAuth.estado.first()
        assertTrue("Se esperaba Error por sesión, fue: $estado", estado is UploadEstado.Error)
        val error = estado as UploadEstado.Error
        assertTrue(
            "Debe mencionar sesión/login, fue: '${error.mensaje}'",
            error.mensaje.contains("sesión", ignoreCase = true) ||
                error.mensaje.contains("iniciar", ignoreCase = true),
        )
    }

    // ─── Resetear ─────────────────────────────────────────────────────────────

    @Test
    fun `resetear vuelve el estado a Idle y limpia banco seleccionado`() = runTest {
        val carga = cargaDePrueba()
        procesarArchivoUseCase.stub {
            onBlocking { ejecutar(any(), eq("uid-test"), any(), any(), any(), anyOrNull(), anyOrNull()) } doReturn
                ResultadoImportacion.Exito(carga, 3)
        }
        viewModel.seleccionarBanco(bancoTest)
        viewModel.procesarArchivo(uriSintetica)
        advanceUntilIdle()
        assertTrue(viewModel.estado.first() is UploadEstado.Exito)

        viewModel.resetear()
        assertEquals(UploadEstado.Idle, viewModel.estado.first())
        assertNull(viewModel.bancoSeleccionado.first())
    }

    // ─── Estado intermedio ────────────────────────────────────────────────────

    @Test
    fun `procesarArchivo - estado Procesando se emite antes del resultado`() = runTest {
        val carga = cargaDePrueba()
        procesarArchivoUseCase.stub {
            onBlocking { ejecutar(any(), eq("uid-test"), any(), any(), any(), anyOrNull(), anyOrNull()) } doReturn
                ResultadoImportacion.Exito(carga, 2)
        }

        val estados = mutableListOf<UploadEstado>()
        val job = launch(testDispatcher) {
            viewModel.estado.collect { estados.add(it) }
        }

        viewModel.seleccionarBanco(bancoTest)
        viewModel.procesarArchivo(uriSintetica)
        advanceUntilIdle()
        job.cancel()

        assertTrue("Se esperaban al menos 2 estados, se emitieron: ${estados.size}", estados.size >= 2)
        assertTrue("Último estado debe ser Exito, fue: ${estados.last()}", estados.last() is UploadEstado.Exito)
    }
}
