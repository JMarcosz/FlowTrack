package com.example.flowtrack.presentation.screens.dashboard

import com.example.flowtrack.core.extensions.formatMoney
import com.example.flowtrack.domain.usecase.ResumenDashboard
import com.example.flowtrack.presentation.components.DonutSlice
import com.example.flowtrack.presentation.components.bancoPorCodigo
import com.example.flowtrack.presentation.components.categoriaPorId

fun ResumenDashboard.toDashboardUiState(nombreUsuario: String): DashboardUiState {
    val grafica = GraficaUiModel(
        labels = serie.map { it.etiqueta },
        gastos = serie.map { it.gasto.toFloat() },
        ingresos = serie.map { it.ingreso.toFloat() },
        balances = serie.map { it.balanceAcumulado.toFloat() },
        donutSlices = gastosPorCategoria.map { item ->
            val categoria = categoriaPorId(item.categoriaId)
            DonutSlice(
                value = item.monto.toFloat(),
                color = categoria.color,
                label = categoria.nombre,
            )
        },
    )

    val comparacionUi = DashboardComparacionUiModel(
        comparisonAvailable = this.comparacion.comparisonAvailable,
        coverageWarning = this.comparacion.coverageWarning,
        expenseChangePercentage = this.comparacion.expenseChangePercentage?.let { "%.1f".format(it.toFloat()) },
        incomeChangePercentage = this.comparacion.incomeChangePercentage?.let { "%.1f".format(it.toFloat()) },
        expenseIsIncrement = this.comparacion.expenseIsIncrement,
        incomeIsIncrement = this.comparacion.incomeIsIncrement,
    )

    val deltaBalanceUi = DashboardDeltaUiModel(
        comparisonAvailable = this.comparacion.comparisonAvailable,
        coverageWarning = this.comparacion.coverageWarning,
        changePercentage = this.deltaBalance.porcentaje?.let { "%.1f".format(it.toFloat()) },
        isIncrement = this.deltaBalance.esIncremento,
    )

    return DashboardUiState(
        nombreUsuario = nombreUsuario,
        totalGastos = formatMoney(gastoTotal),
        totalIngresos = formatMoney(ingresoTotal),
        balanceNeto = formatMoney(balanceNeto),
        comparacion = comparacionUi,
        deltaBalance = deltaBalanceUi,
        grafica = grafica,
        categorias = gastosPorCategoria.map { item ->
            val categoria = categoriaPorId(item.categoriaId)
            CategoriaResumenUiModel(
                id = categoria.id,
                nombre = categoria.nombre,
                color = categoria.color,
                montoTexto = formatMoney(item.monto),
                montoValor = item.monto.toFloat(),
            )
        },
        bancos = gastosPorBanco.map { item ->
            val banco = bancoPorCodigo(item.bancoCodigo)
            BancoResumenUiModel(
                codigo = item.bancoCodigo,
                nombre = banco.nombre,
                gastosTexto = formatMoney(item.gastos),
                ingresosTexto = formatMoney(item.ingresos),
            )
        },
        coverageWarning = this.comparacion.coverageWarning,
    )
}
