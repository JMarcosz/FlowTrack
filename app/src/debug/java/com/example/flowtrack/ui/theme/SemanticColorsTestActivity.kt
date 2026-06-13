package com.example.flowtrack.ui.theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier

class SemanticColorsTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlowTrackTheme {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Income))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Expense))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Success))
                }
            }
            SideEffect { composed = true }
        }
    }

    companion object {
        @Volatile
        var composed: Boolean = false
    }
}
