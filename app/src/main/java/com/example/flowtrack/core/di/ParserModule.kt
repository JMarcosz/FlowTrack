package com.example.flowtrack.core.di

import com.example.flowtrack.data.parsers.banreservas.BanReservasPdfParser
import com.example.flowtrack.data.parsers.cibao.CibaoXlsParser
import com.example.flowtrack.data.parsers.core.BankStatementParser
import com.example.flowtrack.data.parsers.popular.PopularCsvParser
import com.example.flowtrack.data.parsers.qik.QikPdfParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Módulo Hilt que registra todos los parsers en el Set<BankStatementParser>.
 * Para agregar un banco nuevo: implementar StatementParser + @Binds @IntoSet aquí.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds @IntoSet
    abstract fun bindBanReservas(impl: BanReservasPdfParser): BankStatementParser

    @Binds @IntoSet
    abstract fun bindPopular(impl: PopularCsvParser): BankStatementParser

    @Binds @IntoSet
    abstract fun bindQik(impl: QikPdfParser): BankStatementParser

    @Binds @IntoSet
    abstract fun bindCibao(impl: CibaoXlsParser): BankStatementParser
}
