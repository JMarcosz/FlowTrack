package com.example.flowtrack

import android.app.Application
import com.example.flowtrack.widget.BalanceWidgetWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlowTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        BalanceWidgetWorker.enqueue(this)
    }
}
