package com.example.flowtrack.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                    .setSizeBytes(100L * 1024L * 1024L) // 100 MB cache para transacciones offline
                    .build()
            )
            .build()
        firestore.firestoreSettings = settings
        return firestore
    }
}
