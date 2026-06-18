package com.example.flowtrack.presentation.screens.dashboard

import androidx.compose.ui.graphics.Color
import com.example.flowtrack.presentation.components.DonutSlice

data class DashboardUiState(
    val nombreUsuario: String,
    val totalGastos: String,
    val totalIngresos: String,
    val balanceNeto: String,
    val comparacion: DashboardComparacionUiModel,
    val deltaBalance: DashboardDeltaUiModel,
    val grafica: GraficaUiModel,
    val categorias: List<CategoriaResumenUiModel>,
    val bancos: List<BancoResumenUiModel>,
    val coverageWarning: Boolean,
)

data class GraficaUiModel(
    val labels: List<String>,
    val gastos: List<Float>,
    val ingresos: List<Float>,
    val balances: List<Float>,
    val donutSlices: List<DonutSlice>,
)

data class CategoriaResumenUiModel(
    val id: String,
    val nombre: String,
    val color: Color,
    val montoTexto: String,
    val montoValor: Float,
)

data class BancoResumenUiModel(
    val codigo: String,
    val nombre: String,
    val gastosTexto: String,
    val ingresosTexto: String,
)

data class DashboardComparacionUiModel(
    val comparisonAvailable: Boolean,
    val coverageWarning: Boolean,
    val expenseChangePercentage: String?,
    val incomeChangePercentage: String?,
    val expenseIsIncrement: Boolean,
    val incomeIsIncrement: Boolean,
)

data class DashboardDeltaUiModel(
    val comparisonAvailable: Boolean,
    val coverageWarning: Boolean,
    val changePercentage: String?,
    val isIncrement: Boolean,
)
