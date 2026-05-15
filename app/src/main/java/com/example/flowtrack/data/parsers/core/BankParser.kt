package com.example.flowtrack.data.parsers.core

import com.example.flowtrack.domain.model.TipoDocumento

/**
 * Contrato que todo parser de banco debe cumplir.
 * Las implementaciones se registran vía Hilt @IntoSet para descubrimiento dinámico.
 * Agregar un banco nuevo = implementar esta interfaz + registrarla en ParserModule.
 */
interface BankParser {

    /** Código único del banco. Debe coincidir con un documento en /catalogoBancos. */
    val codigoBanco: String

    /** Qué tipo de documento procesa este parser. */
    val tipoDocumento: TipoDocumento

    /** Versión del parser. Se incrementa cuando el banco cambia su formato. */
    val version: Int

    /** Extensiones de archivo que este parser acepta (sin punto, lowercase). */
    val formatosArchivo: Set<String>

    /**
     * Evalúa si este parser puede manejar el archivo dado.
     * NO parsea el archivo completo — solo inspecciona headers/metadatos.
     * Debe ser rápido (<100ms) ya que se ejecuta para todos los parsers registrados.
     *
     * @return ConfianzaDeteccion(0.0) si definitivamente no puede manejar el archivo.
     */
    suspend fun puedeManejar(archivo: ArchivoEntrada): ConfianzaDeteccion

    /**
     * Parsea el archivo completo y devuelve el resultado normalizado.
     * Solo se llama después de que puedeManejar() devolvió confianza > 0.0.
     * Toda excepción interna debe ser capturada y convertida en ResultadoParseo.Error.
     */
    suspend fun parsear(archivo: ArchivoEntrada, contexto: ContextoParseo): ResultadoParseo
}
