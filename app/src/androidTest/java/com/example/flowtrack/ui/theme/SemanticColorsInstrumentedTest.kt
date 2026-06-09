package com.example.flowtrack.ui.theme

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SemanticColorsInstrumentedTest {

    @Test
    fun renderiza_ingreso_gasto_y_exito_con_sus_tokens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        SemanticColorsTestActivity.composed = false
        val intent = Intent(
            instrumentation.targetContext,
            SemanticColorsTestActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as SemanticColorsTestActivity

        try {
            val deadline = System.currentTimeMillis() + 5_000
            while (!SemanticColorsTestActivity.composed && System.currentTimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                Thread.sleep(100)
            }
            assertTrue("La composición no terminó a tiempo", SemanticColorsTestActivity.composed)
            instrumentation.waitForIdleSync()

            lateinit var screenshot: Bitmap
            instrumentation.runOnMainSync {
                val view = activity.window.decorView
                screenshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(screenshot))
            }

            val y = screenshot.height / 2
            assertEquals(Income.toArgb(), screenshot.getPixel(screenshot.width / 6, y))
            assertEquals(Expense.toArgb(), screenshot.getPixel(screenshot.width / 2, y))
            assertEquals(Success.toArgb(), screenshot.getPixel(screenshot.width * 5 / 6, y))
        } finally {
            activity.finish()
        }
    }
}
