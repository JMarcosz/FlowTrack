package com.example.flowtrack.core.di

import com.example.flowtrack.data.parsers.banreservas.BanReservasPdfParser
import com.example.flowtrack.data.parsers.cibao.CibaoXlsParser
import com.example.flowtrack.data.parsers.core.BankParser
import com.example.flowtrack.data.parsers.popular.PopularCsvParser
import com.example.flowtrack.data.parsers.qik.QikPdfParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Módulo Hilt que registra todos los parsers en el Set<BankParser>.
 * Para agregar un banco nuevo: implementar BankParser + @Binds @IntoSet aquí.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds @IntoSet
    abstract fun bindBanReservas(impl: BanReservasPdfParser): BankParser

    @Binds @IntoSet
    abstract fun bindPopular(impl: PopularCsvParser): BankParser

    @Binds @IntoSet
    abstract fun bindQik(impl: QikPdfParser): BankParser

    @Binds @IntoSet
    abstract fun bindCibao(impl: CibaoXlsParser): BankParser

    // Post-MVP:
    // @Binds @IntoSet abstract fun bindBhd(impl: BhdParser): BankParser
}

