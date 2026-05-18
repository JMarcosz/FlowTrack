package com.example.flowtrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import com.example.flowtrack.MainActivity
import java.text.NumberFormat
import java.util.Locale

class BalanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> =
        widgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

@Composable
private fun WidgetContent() {
    val prefs = currentState<Preferences>()
    val data = prefs.toWidgetData()
    val fmt = NumberFormat.getNumberInstance(Locale("es", "DO")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val balance = data.ingresos - data.gastos

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 16, vertical = 12)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FlowTrack · ${data.periodo}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
            ),
        )

        Spacer(GlanceModifier.height(6))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Ingresos",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
                Text(
                    text = "RD$ ${fmt.format(data.ingresos)}",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                )
            }

            Spacer(GlanceModifier.width(20))

            Column {
                Text(
                    text = "Gastos",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
                Text(
                    text = "RD$ ${fmt.format(data.gastos)}",
                    style = TextStyle(
                        color = GlanceTheme.colors.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                )
            }
        }

        Spacer(GlanceModifier.height(8))

        val balanceColor = if (balance >= 0) GlanceTheme.colors.primary else GlanceTheme.colors.error
        Text(
            text = "Balance: RD$ ${fmt.format(balance)}",
            style = TextStyle(
                color = balanceColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
        )
    }
}
