package com.example.flowtrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlowTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializar PDFBox para Android (necesario para manejar fuentes y recursos)
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
    }
}
