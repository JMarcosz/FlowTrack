package com.example.flowtrack.core.di

import com.example.flowtrack.data.parsers.banreservas.BanReservasPdfParser
import com.example.flowtrack.data.parsers.core.BankParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Módulo Hilt que registra todos los parsers en el Set<BankParser>.
 * ParserRegistry recibe ese Set por inyección y opera sin conocer
 * los tipos concretos.
 *
 * Para agregar un banco nuevo:
 *   1. Implementar BankParser en data/parsers/{banco}/
 *   2. Agregar @Binds @IntoSet aquí
 *   3. Cero cambios en el resto del sistema
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @IntoSet
    abstract fun bindBanReservas(impl: BanReservasPdfParser): BankParser

    // Sprint 3: descomentaar cuando estén listos
    // @Binds @IntoSet abstract fun bindPopular(impl: PopularCsvParser): BankParser
    // @Binds @IntoSet abstract fun bindQik(impl: QikPdfParser): BankParser
    // @Binds @IntoSet abstract fun bindCibao(impl: CibaoXlsParser): BankParser

    // Post-MVP:
    // @Binds @IntoSet abstract fun bindBhd(impl: BhdParser): BankParser
}
