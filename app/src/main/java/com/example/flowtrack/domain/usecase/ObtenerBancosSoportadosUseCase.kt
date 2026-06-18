package com.example.flowtrack.domain.usecase

import com.example.flowtrack.domain.model.BancoSoportado
import com.example.flowtrack.domain.model.FormatoArchivo
import com.example.flowtrack.domain.model.ProductoTipo
import javax.inject.Inject

class ObtenerBancosSoportadosUseCase @Inject constructor() {
    operator fun invoke(): List<BancoSoportado> {
        return listOf(
            BancoSoportado(
                codigo = "BANRESERVAS",
                nombre = "BanReservas",
                formatosPermitidos = listOf(FormatoArchivo.PDF),
                productoTipo = ProductoTipo.CUENTA,
            ),
            BancoSoportado(
                codigo = "POPULAR",
                nombre = "Banco Popular",
                formatosPermitidos = listOf(FormatoArchivo.CSV, FormatoArchivo.PDF),
                productoTipo = ProductoTipo.CUENTA,
            ),
            BancoSoportado(
                codigo = "QIK",
                nombre = "Qik",
                formatosPermitidos = listOf(FormatoArchivo.PDF),
                productoTipo = ProductoTipo.TARJETA,
            ),
            BancoSoportado(
                codigo = "CIBAO",
                nombre = "Asociación Cibao",
                formatosPermitidos = listOf(FormatoArchivo.XLS),
                productoTipo = ProductoTipo.TARJETA,
            ),
            BancoSoportado(
                codigo = "BHD",
                nombre = "BHD León",
                formatosPermitidos = listOf(FormatoArchivo.PDF),
                productoTipo = ProductoTipo.CUENTA,
            ),
        )
    }
}
