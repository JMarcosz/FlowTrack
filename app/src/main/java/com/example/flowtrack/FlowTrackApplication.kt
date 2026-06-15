package com.example.flowtrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.flowtrack.core.notifications.NotificationHelper
import com.example.flowtrack.core.workers.ClusteringWorker
import com.example.flowtrack.core.workers.TasaCambioWorker
import com.example.flowtrack.widget.BalanceWidgetWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlowTrackApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        NotificationHelper.crearCanales(this)
        BalanceWidgetWorker.enqueue(this)
        ClusteringWorker.enqueue(this)
        TasaCambioWorker.enqueue(this)
    }
}
