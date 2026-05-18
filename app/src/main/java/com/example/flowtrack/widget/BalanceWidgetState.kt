package com.example.flowtrack.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

data class BalanceWidgetData(
    val ingresos: Double,
    val gastos: Double,
    val periodo: String,
)

object WidgetKeys {
    val INGRESOS = doublePreferencesKey("widget_ingresos")
    val GASTOS   = doublePreferencesKey("widget_gastos")
    val PERIODO  = stringPreferencesKey("widget_periodo")
}

val widgetStateDefinition: GlanceStateDefinition<Preferences> =
    PreferencesGlanceStateDefinition

fun Preferences.toWidgetData(): BalanceWidgetData = BalanceWidgetData(
    ingresos = this[WidgetKeys.INGRESOS] ?: 0.0,
    gastos   = this[WidgetKeys.GASTOS] ?: 0.0,
    periodo  = this[WidgetKeys.PERIODO] ?: "--",
)
