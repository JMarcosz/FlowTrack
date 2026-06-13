package com.example.flowtrack.presentation.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.flowtrack.ui.theme.Primary

/**
 * Switch estilizado con el color primario del DS.
 * Úsalo en configuración, reglas y cualquier toggle de la app.
 */
@Composable
fun FinanzasSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Primary,
            checkedTrackColor = Primary.copy(alpha = 0.3f),
        ),
    )
}
