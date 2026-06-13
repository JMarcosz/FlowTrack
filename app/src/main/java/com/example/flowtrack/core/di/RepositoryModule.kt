package com.example.flowtrack.core.di

import com.example.flowtrack.data.firestore.repositories.CuentaRepository
import com.example.flowtrack.data.firestore.repositories.TarjetaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.repository.ICuentaRepository
import com.example.flowtrack.domain.repository.ITarjetaRepository
import com.example.flowtrack.domain.repository.ITransaccionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransaccionRepository(
        impl: TransaccionRepository
    ): ITransaccionRepository

    @Binds
    @Singleton
    abstract fun bindCuentaRepository(
        impl: CuentaRepository
    ): ICuentaRepository

    @Binds
    @Singleton
    abstract fun bindTarjetaRepository(
        impl: TarjetaRepository
    ): ITarjetaRepository
}