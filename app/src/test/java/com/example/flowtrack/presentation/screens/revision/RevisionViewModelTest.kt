package com.example.flowtrack.presentation.screens.revision

import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Moneda
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RevisionViewModelTest {

    @Test
    fun confirmar_y_consumirConfirmacion_reiniciaEstado() {
        val viewModel = RevisionViewModel()

        viewModel.cargarResultado(
            nombreArchivo = "estado.pdf",
            banco = "BANRESERVAS",
            transacciones = listOf(
                TransaccionNormalizada(
                    fecha = LocalDate.of(2026, 1, 1),
                    fechaPosteo = null,
                    descripcionCorta = "Pago",
                    descripcionOriginal = "Pago",
                    monto = BigDecimal("100.00"),
                    tipo = TipoTransaccion.DEBITO,
                    moneda = Moneda.DOP,
                    balanceDespues = null,
                    referencia = null,
                    serial = null,
                ),
            ),
            advertencias = emptyList(),
            duplicados = 0,
            periodo = "Enero 2026",
        )

        viewModel.confirmar()
        assertTrue(viewModel.estado.value is RevisionEstado.Confirmado)

        viewModel.consumirConfirmacion()
        assertTrue(viewModel.estado.value is RevisionEstado.Cargando)
    }
}
