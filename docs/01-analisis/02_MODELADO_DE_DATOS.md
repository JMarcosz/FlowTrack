# Modelado de datos — Interfaz genérica, adaptación por banco, formato estandarizado

> **Documento técnico de modelado.** Define en cascada: (1) el contrato genérico que todo parser debe cumplir, (2) la adaptación específica de cada banco al modelo común, y (3) el formato de salida estandarizado que persiste en Firestore. Este documento es la fuente de verdad para implementar la capa `data/parsers/` y `domain/models/`.

---

## 1. Filosofía del modelado

El sistema sigue tres principios:

1. **Un único modelo de salida**. Sin importar el banco o formato de entrada, toda transacción se convierte a la misma estructura `Transaccion`. La UI nunca sabe de dónde vino el dato.
2. **Adaptación en la frontera**. La transformación del formato propietario al modelo común ocurre dentro del parser de cada banco. Ninguna lógica de banco específico se filtra fuera de su parser.
3. **Extensibilidad sin reescritura**. Agregar un banco nuevo consiste en crear una clase que implemente `BankParser` y registrarla en Hilt. Cero cambios en el resto del sistema.

Esto se traduce en tres capas:

```
Capa A — Contrato genérico (interfaz BankParser + ResultadoParseo)
         ↓
Capa B — Adaptación específica (un parser por banco, mapea formato → modelo)
         ↓
Capa C — Formato estandarizado (modelos de dominio persistidos en Firestore)
```

---

## 2. Capa A — Contrato genérico

### 2.1 Interfaz `BankParser`

```kotlin
package com.jeanmarco.finanzas.data.parsers.core

/**
 * Contrato que todo parser de banco debe cumplir.
 * Implementaciones se registran via Hilt @IntoSet para descubrimiento dinámico.
 */
interface BankParser {

    /** Código único del banco. Debe coincidir con un documento en /catalogoBancos. */
    val codigoBanco: String

    /** Qué tipo de documento procesa este parser. */
    val tipoDocumento: TipoDocumento

    /** Versión del parser. Se incrementa cuando el banco cambia su formato. */
    val version: Int

    /** Extensiones de archivo que este parser acepta (sin punto). */
    val formatosArchivo: Set<String>

    /**
     * Evalúa si este parser puede manejar el archivo.
     * NO parsea el archivo completo, solo inspecciona headers/metadatos.
     * Debe ser rápido (<100ms) ya que se ejecuta para todos los parsers en paralelo.
     */
    suspend fun puedeManejar(archivo: ArchivoEntrada): ConfianzaDeteccion

    /**
     * Parsea el archivo completo y devuelve el resultado normalizado.
     * Solo se llama después de que puedeManejar() devolvió confianza > 0.0.
     */
    suspend fun parsear(archivo: ArchivoEntrada, contexto: ContextoParseo): ResultadoParseo
}
```

### 2.2 Tipos auxiliares del contrato

```kotlin
package com.jeanmarco.finanzas.data.parsers.core

/** Archivo entrante con metadata útil para detección. */
data class ArchivoEntrada(
    val nombre: String,           // "Estado_de_Cuenta_BHD.pdf"
    val extension: String,        // "pdf"
    val tamanioBytes: Long,
    val bytes: ByteArray,         // contenido en memoria, max 10 MB
    val mimeType: String?         // "application/pdf"
)

/** Resultado de la evaluación de detección. */
data class ConfianzaDeteccion(
    val confianza: Float,         // 0.0 a 1.0
    val razon: String,            // "encontró IBAN DO57BRRD en página 1"
    val pistas: Map<String, String> = emptyMap() // info auxiliar para UI
)

/** Contexto que el caller provee al parser. */
data class ContextoParseo(
    val uidUsuario: String,
    val cuentaIdSugerida: String? = null,  // si el usuario ya seleccionó cuenta destino
    val tarjetaIdSugerida: String? = null,
    val zonaHoraria: String = "America/Santo_Domingo"
)

/** Resultado del parseo. Sealed para forzar manejo exhaustivo. */
sealed class ResultadoParseo {

    data class ExitoCuenta(
        val cuenta: CuentaDetectada,
        val transacciones: List<TransaccionNormalizada>,
        val resumenPeriodo: ResumenPeriodoDetectado?,
        val advertencias: List<String> = emptyList()
    ) : ResultadoParseo()

    data class ExitoTarjeta(
        val tarjeta: TarjetaDetectada,
        val estadoTarjeta: EstadoTarjetaDetectado,
        val movimientos: List<MovimientoTarjetaNormalizado>,
        val advertencias: List<String> = emptyList()
    ) : ResultadoParseo()

    /** El parser detectó el formato pero necesita input del usuario. */
    data class RequiereConfirmacion(
        val mensaje: String,
        val opcionesBanco: List<String>,
        val datosParciales: Any
    ) : ResultadoParseo()

    /** No se pudo parsear. */
    data class Error(
        val mensaje: String,
        val excepcion: Throwable? = null,
        val recuperable: Boolean = false
    ) : ResultadoParseo()
}

enum class TipoDocumento {
    CUENTA_CORRIENTE,
    CUENTA_AHORRO,
    TARJETA_CREDITO,
    TARJETA_DEBITO,
    PRESTAMO,
    INVERSION
}
```

### 2.3 Modelos intermedios de detección

Estos son los DTOs que devuelve el parser, antes de mapearse a modelos de dominio:

```kotlin
data class CuentaDetectada(
    val numeroCuenta: String,              // últimos 4-10 dígitos, lo que esté disponible
    val numeroCuentaCompleto: String?,     // IBAN si está disponible
    val titular: String,
    val tipoCuenta: TipoCuenta,
    val moneda: Moneda,
    val balanceAlCorte: java.math.BigDecimal?,
    val balanceAnterior: java.math.BigDecimal?
)

data class TransaccionNormalizada(
    val fecha: java.time.LocalDate,
    val fechaPosteo: java.time.LocalDate?,
    val descripcionCorta: String,          // categoría nativa del banco
    val descripcionOriginal: String,       // raw del estado
    val monto: java.math.BigDecimal,       // siempre positivo
    val tipo: TipoTransaccion,             // DEBITO | CREDITO
    val moneda: Moneda,
    val balanceDespues: java.math.BigDecimal?,
    val referencia: String?,
    val serial: String?,
    val esDerivada: Boolean = false,       // ej: impuesto DGII 0.15%
    val referenciaPadre: String? = null,   // si esDerivada, ref de la transacción origen
    val metadataBanco: Map<String, String> = emptyMap()
)

data class ResumenPeriodoDetectado(
    val periodoInicio: java.time.LocalDate,
    val periodoFin: java.time.LocalDate,
    val cantidadDebitos: Int,
    val cantidadCreditos: Int,
    val totalDebitos: java.math.BigDecimal,
    val totalCreditos: java.math.BigDecimal,
    val balanceFinal: java.math.BigDecimal
)

data class TarjetaDetectada(
    val ultimos4: String,
    val titular: String,
    val tipoRed: String?,                   // "VISA", "MASTERCARD"
    val limiteCredito: java.math.BigDecimal,
    val moneda: Moneda,
    val diaCorte: Int?,                     // día del mes
    val diaPago: Int?,
    val tasaInteresAnual: Double?
)

data class EstadoTarjetaDetectado(
    val fechaCorte: java.time.LocalDate,
    val fechaLimitePago: java.time.LocalDate,
    val balanceAlCorte: java.math.BigDecimal,
    val balanceAnterior: java.math.BigDecimal?,
    val pagoMinimo: java.math.BigDecimal,
    val pagoTotal: java.math.BigDecimal,
    val montoVencido: java.math.BigDecimal,
    val balancePromedioDiario: java.math.BigDecimal?,
    val interesPorFinanciamiento: java.math.BigDecimal?,
    val cashbackGanado: java.math.BigDecimal?
)

data class MovimientoTarjetaNormalizado(
    val fechaTransaccion: java.time.LocalDate,
    val fechaPosteo: java.time.LocalDate?,
    val descripcionOriginal: String,
    val monto: java.math.BigDecimal,        // siempre positivo
    val tipoMovimiento: TipoMovimientoTarjeta,
    val moneda: Moneda,
    val numeroAutorizacion: String?,
    val metadataBanco: Map<String, String> = emptyMap()
)

enum class TipoCuenta { CORRIENTE, AHORRO, CREDITO, INVERSION }
enum class TipoTransaccion { DEBITO, CREDITO }
enum class Moneda { DOP, USD }

enum class TipoMovimientoTarjeta {
    COMPRA,               // consumo regular
    PAGO,                 // pago a la tarjeta
    INTERES,              // cargo por interés
    COMISION,             // comisiones varias
    CASHBACK,             // recompensa
    AJUSTE,               // ajustes manuales
    AVANCE_EFECTIVO
}
```

### 2.4 Registry y orquestación

```kotlin
package com.jeanmarco.finanzas.data.parsers.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParserRegistry @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards BankParser>
) {

    companion object {
        const val UMBRAL_ALTA_CONFIANZA = 0.8f
        const val UMBRAL_MEDIA_CONFIANZA = 0.4f
    }

    suspend fun detectar(archivo: ArchivoEntrada): ResultadoDeteccion {
        val candidatos = parsers
            .filter { archivo.extension.lowercase() in it.formatosArchivo }
            .map { parser ->
                val deteccion = parser.puedeManejar(archivo)
                parser to deteccion
            }
            .filter { it.second.confianza > 0f }
            .sortedByDescending { it.second.confianza }

        if (candidatos.isEmpty()) {
            return ResultadoDeteccion.NoDetectado(
                mensaje = "Ningún parser registrado pudo identificar el archivo.",
                bancosDisponibles = parsers.map { it.codigoBanco }.distinct()
            )
        }

        val (mejorParser, mejorConfianza) = candidatos.first()

        return when {
            mejorConfianza.confianza >= UMBRAL_ALTA_CONFIANZA ->
                ResultadoDeteccion.AltaConfianza(mejorParser, mejorConfianza)

            mejorConfianza.confianza >= UMBRAL_MEDIA_CONFIANZA ->
                ResultadoDeteccion.MediaConfianza(
                    candidatos = candidatos.take(3).map { it.first to it.second }
                )

            else ->
                ResultadoDeteccion.BajaConfianza(
                    candidatos = candidatos.map { it.first to it.second },
                    bancosDisponibles = parsers.map { it.codigoBanco }.distinct()
                )
        }
    }
}

sealed class ResultadoDeteccion {
    data class AltaConfianza(val parser: BankParser, val confianza: ConfianzaDeteccion) : ResultadoDeteccion()
    data class MediaConfianza(val candidatos: List<Pair<BankParser, ConfianzaDeteccion>>) : ResultadoDeteccion()
    data class BajaConfianza(val candidatos: List<Pair<BankParser, ConfianzaDeteccion>>, val bancosDisponibles: List<String>) : ResultadoDeteccion()
    data class NoDetectado(val mensaje: String, val bancosDisponibles: List<String>) : ResultadoDeteccion()
}
```

### 2.5 Registro en Hilt

```kotlin
package com.jeanmarco.finanzas.core.di

import com.jeanmarco.finanzas.data.parsers.banreservas.BanReservasPdfParser
import com.jeanmarco.finanzas.data.parsers.core.BankParser
import com.jeanmarco.finanzas.data.parsers.popular.PopularCsvParser
import com.jeanmarco.finanzas.data.parsers.qik.QikPdfParser
import com.jeanmarco.finanzas.data.parsers.generico_tarjeta.GenericoTarjetaXlsParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

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
    abstract fun bindGenericoTarjeta(impl: GenericoTarjetaXlsParser): BankParser

    // Agregar BHD aquí cuando esté listo:
    // @Binds @IntoSet
    // abstract fun bindBhd(impl: BhdParser): BankParser
}
```

---

## 3. Capa B — Adaptación por banco

Cada parser hereda de `BankParser` y mapea su formato propietario al modelo común. Aquí se especifica el comportamiento de los 4 parsers del MVP.

### 3.1 `BanReservasPdfParser`

**Formato de entrada:** PDF tabular con columnas `Fecha | Referencia | Concepto | Cheques y cargos | Depósitos y abonos | Balance`.

**Detección:**

| Señal | Peso | Confianza acumulada |
|-------|-----:|--------------------:|
| Contiene texto `"BANRESERVAS"` o `"banreservas.com.do"` | 0.4 | 0.4 |
| Contiene IBAN que coincide con regex `^DO\d{2}BRRD` | 0.4 | 0.8 |
| Encabezados `"Cheques y cargos"` y `"Depósitos y abonos"` presentes | 0.2 | 1.0 |

**Lógica de parseo:**

1. Extraer texto del PDF con PdfBox-Android, preservando estructura columnar.
2. Localizar header del bloque de transacciones (línea con `"Fecha"`, `"Referencia"`, `"Concepto"`...).
3. Parsear cada línea posterior hasta encontrar `"CHEQUES PAGADOS:"` o `"Débitos"` (inicio del resumen).
4. Para cada fila:
   - **Fecha**: formato `dd/MM/yyyy` → `LocalDate`.
   - **Tipo**: si "Cheques y cargos" tiene monto → `DEBITO`. Si "Depósitos y abonos" tiene monto → `CREDITO`.
   - **Monto**: el valor de la columna correspondiente, parseado como `BigDecimal` removiendo comas.
   - **Balance después**: columna Balance.
5. **Transacciones derivadas DGII**: detectar filas con concepto `"COBRO IMP 0.15% DGII CTA CTE"`. Marcar `esDerivada = true` y `referenciaPadre = referencia` (en BanReservas, el impuesto comparte referencia con su transacción origen).
6. Parsear bloque de resumen final para construir `ResumenPeriodoDetectado`.

**Moneda:** siempre `DOP` (BanReservas emite estados por moneda; no se mezclan).

**Mapeo de descripciones:**

| `Concepto` en el PDF | `descripcionCorta` normalizada | `tipo` |
|---------------------|-------------------------------|--------|
| `CONSUMO POS CTA CTE` | `CONSUMO POS` | DEBITO |
| `RETIRO ATM REGIONAL-CTA CTE` | `RETIRO ATM` | DEBITO |
| `RETIRO SAB CTA. CTE.` | `RETIRO SUCURSAL` | DEBITO |
| `TRANS. CREDITO A CTA. CTE.` | `TRANSFERENCIA SALIENTE` | DEBITO |
| `CR transferencia a cta cte` | `TRANSFERENCIA ENTRANTE` | CREDITO |
| `TRANSF. PROPIA A CTA. AHORRO` | `TRANSFERENCIA PROPIA` | DEBITO |
| `NOMINAS ACH` | `NOMINA` | CREDITO |
| `DEBITO CTA CORRIENTES - PAGOS` | `PAGO DEBITADO` | DEBITO |
| `COBRO IMP 0.15% DGII CTA CTE` | `IMPUESTO DGII` | DEBITO (derivada) |
| `COMISION CTA. CORRIENTE` | `COMISION` | DEBITO |
| `COMISION MENSUAL USO ATMS` | `COMISION ATM` | DEBITO |
| `Transf LBTR DB CTA CTE` | `TRANSFERENCIA LBTR` | DEBITO |
| `COBRO DE PENDIENTES` | `CARGO PENDIENTE` | DEBITO |

**Metadata específica almacenada:**
- `numeroCuentaEstandar`: el IBAN completo.
- `paginas`: número de páginas del PDF.

---

### 3.2 `PopularCsvParser`

**Formato de entrada:** CSV con BOM UTF-8, 10 líneas de metadata + header + N transacciones.

**Detección:**

| Señal | Peso | Confianza acumulada |
|-------|-----:|--------------------:|
| Línea 7 contiene `"Banco Popular Dominicano"` | 0.5 | 0.5 |
| Header línea 11 = `"Fecha Posteo,Descripción Corta,Monto Transacción,Balance ,No. Referencia,No. Serial,Descripción"` | 0.5 | 1.0 |

**Lógica de parseo:**

1. Leer todas las líneas, ignorar BOM inicial.
2. Líneas 1-10: extraer metadata (titular en línea 4, rango en línea 5, cuenta en línea 8).
3. Línea 11: validar header. Si no coincide exactamente, devolver `Error("Formato CSV cambió")`.
4. Líneas 12 en adelante con OpenCSV (respetando comas dentro de descripciones).
5. Para cada fila:
   - **Fecha**: formato `dd/MM/yyyy` → `LocalDate`.
   - **Descripción Corta**: determina el `tipo` (ver tabla abajo).
   - **Monto Transacción**: parsear como `BigDecimal`, siempre positivo (Popular no usa signo).
   - **Balance**: columna `Balance ` (con espacio al final, ¡importante!).
   - **No. Referencia** + **No. Serial**: ambos opcionales.
   - **Descripción**: texto largo con merchant + ciudad + fecha + referencia (guardado completo según decisión del usuario).

**Mapeo `Descripción Corta` → `TipoTransaccion`:**

| Descripción Corta | `tipo` |
|-------------------|--------|
| `Crédito Transferencia` | CREDITO |
| `Crédito ACH` | CREDITO |
| `Crédito` | CREDITO |
| `Depósito Efectivo` | CREDITO |
| `Cashback` | CREDITO |
| `Débito Cuenta` | DEBITO |
| `Débito ATM` | DEBITO |
| `Débito POS` | DEBITO |
| `Débito Transferencia` | DEBITO |
| `Débito ACH` | DEBITO |
| `DB Comisiones` | DEBITO |
| `Pago Tarjeta` | DEBITO |

> Si aparece una `Descripción Corta` no listada, asumir `DEBITO` y agregar advertencia.

**Moneda:** detectar de la metadata línea 8-9. Por defecto `DOP`.

**Metadata específica almacenada:**
- `noReferencia`, `noSerial`
- `descripcionLarga`: el texto completo de la columna `Descripción`.

---

### 3.3 `QikPdfParser`

**Formato de entrada:** PDF narrativo con bloque de datos de tarjeta + tabla de movimientos.

**Detección:**

| Señal | Peso | Confianza acumulada |
|-------|-----:|--------------------:|
| Contiene `"www.qik.com.do"` | 0.4 | 0.4 |
| Contiene `"Qik Banco Digital Dominicano"` | 0.3 | 0.7 |
| Contiene `"RNC: 1-32-49841-2"` | 0.3 | 1.0 |

**Lógica de parseo:**

**Fase 1 — Bloque de datos de tarjeta:**

Extraer mediante regex sobre el texto:

```
"Período:\s+(\d{1,2}\s+\w{3})\s+-\s+(\d{1,2}\s+\w{3}\s+\d{4})"
"Fecha de corte:\s+(\d{1,2}\s+\w{3}\s+\d{4})"
"Límite Aprobado:\s+RD\$\s+([\d,\.]+)"
"Número de tarjeta:\s+\*+(\d{4})"
"Fecha Límite de Pago\s+(\d{1,2}\s+\w{3}\s+\d{4})"
"Balance al corte\s+RD\$\s+([\d,\.]+)"
"Monto mínimo a pagar\s+RD\$\s+([\d,\.]+)"
"Balance al corte anterior\s+([\d,\.]+)"
"Tu tasa de interés anual es de (\d+)%"
"Balance promedio diario del mes:\s+RD\$\s+([\d,\.]+)"
"Intereses por financiamiento de meses anteriores:\s+RD\$\s+([\d,\.]+)"
```

Calcular `diaCorte` y `diaPago` extrayendo el día del mes de `Fecha de corte` y `Fecha Límite de Pago` respectivamente.

**Fase 2 — Tabla de movimientos:**

Localizar el header `"Fecha Entrada Descripción Monto"`. Cada fila siguiente:

1. Primer token (`dd/MM/yyyy`) → `fechaTransaccion`.
2. Segundo token (`dd/MM/yyyy`) → `fechaPosteo`.
3. Texto medio (variable) → `descripcionOriginal`.
4. Último token con `RD$` o `-` `RD$` → `monto`.
   - Si tiene `-` al inicio → `tipo = PAGO` (entra a la tarjeta).
   - Si no → `tipo = COMPRA` (sale de la tarjeta).

**Clasificación adicional por texto de descripción:**

| Patrón en descripción | `tipoMovimiento` |
|-----------------------|------------------|
| `Interes Por Financiamiento` | INTERES |
| `Recompensas Qik Rebate` | CASHBACK |
| `Pago A Tarjeta` | PAGO |
| `Comision` | COMISION |
| `Avance de Efectivo` | AVANCE_EFECTIVO |
| (cualquier otro) | COMPRA |

**Moneda:** detectada por el símbolo `RD$` o `US$` en el monto. Qik emite solo en DOP en la mayoría de tarjetas.

**Salida especial:**

Qik devuelve **siempre** `ResultadoParseo.ExitoTarjeta` (no cuenta), con:
- `tarjeta`: datos completos auto-extraídos.
- `estadoTarjeta`: snapshot del corte.
- `movimientos`: lista de movimientos del período.

**Sugerencia de creación automática:** si el usuario no tiene una tarjeta con esos `ultimos4` registrada, crear automáticamente con los datos extraídos. Si existe, comparar campos y avisar si la tasa o el límite cambiaron.

---

### 3.4 `GenericoTarjetaXlsParser`

**Formato de entrada:** XLS legacy (.xls, OLE Compound) sin identificación de banco. Caso de Asociación Cibao y posiblemente otros.

**Detección:**

| Señal | Peso | Confianza acumulada |
|-------|-----:|--------------------:|
| Extensión `.xls` (no `.xlsx`) | 0.1 | 0.1 |
| Primera hoja se llama `"CreditCardDetail"` o similar | 0.1 | 0.2 |
| Estructura: header con `"Número de tarjeta"`, `"Límite de crédito"` en columnas paralelas USD/DOP | 0.1 | 0.3 |

**Confianza máxima:** 0.3 (siempre caerá en zona baja → mostrar catálogo al usuario).

**Lógica de parseo:**

1. Abrir con Apache POI HSSF (legacy XLS).
2. Localizar el bloque de cabecera (filas 1-15 aprox):
   - Titular, últimos 4, alias de tarjeta
   - Fecha de corte, fecha de vencimiento
   - Columnas paralelas: límite USD / límite DOP
   - Saldo anterior USD / DOP
   - Pago mínimo USD / DOP
   - Pago total USD / DOP
3. Detectar qué moneda está en uso: la columna que tenga valores no-cero define la moneda activa de la tarjeta.
4. Localizar el bloque de movimientos (después de una fila vacía o header `"Fecha"`):
   - Columnas: Fecha, Número de tarjeta, Número de autorización, Descripción, Monto local, Monto en dólares
5. Para cada fila de movimiento:
   - Determinar moneda: si "Monto local" > 0 → DOP. Si "Monto en dólares" > 0 → USD.
   - **Tipo:** clasificar por descripción (mismas reglas que QIK).

**Salida:** `ResultadoParseo.RequiereConfirmacion` la primera vez, con `opcionesBanco = ["CIBAO", "BHD", "POPULAR", "BANRESERVAS", "OTRO"]`. Una vez el usuario confirma, se asocia ese banco al archivo y se procesa.

> **Aprendizaje del sistema:** guardar la asociación `nombreArchivo → bancoConfirmado` localmente para sugerir el mismo banco la próxima vez que vea un archivo similar.

---

### 3.5 Tabla comparativa de mapeos

Esta tabla cristaliza cómo cada banco se adapta al modelo común:

| Campo modelo común | BanReservas | Popular | Qik | Genérico XLS |
|---|---|---|---|---|
| `fecha` | Columna "Fecha" `dd/MM/yyyy` | Columna "Fecha Posteo" | Primera columna tabla | Columna "Fecha" |
| `fechaPosteo` | (no aplica, una sola fecha) | (igual a fecha) | Columna "Entrada" | (no presente) |
| `descripcionOriginal` | Columna "Concepto" | Columna "Descripción" (larga) | Texto central de la fila | Columna "Descripción" |
| `descripcionCorta` | "Concepto" normalizado por tabla | Columna "Descripción Corta" | Inferido del patrón | Inferido del patrón |
| `monto` | "Cheques y cargos" o "Depósitos y abonos" | Columna "Monto Transacción" | Último token, sin signo | Columna "Monto local" o "Monto en dólares" |
| `tipo` | Por columna donde aparece el monto | Por "Descripción Corta" | Por signo del monto | Por descripción |
| `moneda` | DOP (fijo) | DOP (detectado) | DOP (detectado por `RD$`) | DOP o USD (por columna) |
| `balanceDespues` | Columna "Balance" | Columna "Balance " (con espacio) | (no en movimientos) | (no presente) |
| `referencia` | Columna "Referencia" | Columna "No. Referencia" | (no en formato Qik) | Columna "Número de autorización" |
| `esDerivada` | true si concepto = `COBRO IMP 0.15% DGII` | siempre false | siempre false | siempre false |

---

## 4. Capa C — Formato de salida estandarizado

Una vez los parsers producen modelos intermedios (`TransaccionNormalizada`, etc.), un mapper los convierte al **modelo de dominio final** que se persiste en Firestore.

### 4.1 Modelo de dominio `Transaccion`

```kotlin
package com.jeanmarco.finanzas.domain.models

import java.math.BigDecimal
import java.time.Instant

data class Transaccion(
    val id: String,                          // SHA-256 hash determinístico, 20 chars
    val uidUsuario: String,
    val cuentaId: String,                    // ref a /usuarios/{uid}/cuentas/{id}
    val bancoCodigo: String,                 // "BANRESERVAS", "POPULAR", ...
    val fecha: Instant,                      // siempre UTC en almacenamiento
    val fechaPosteo: Instant?,
    val descripcionCorta: String,            // normalizada por el parser
    val descripcionOriginal: String,         // raw del estado de cuenta
    val descripcionNormalizada: String,      // limpia, mayúsculas, sin acentos (para matching)
    val monto: BigDecimal,                   // siempre positivo
    val tipo: TipoTransaccion,
    val moneda: Moneda,
    val balanceDespues: BigDecimal?,
    val referencia: String?,
    val serial: String?,
    val categoriaId: String?,                // ref a /catalogoCategorias o /usuarios/{uid}/reglasCategorias
    val categoriaAutomatica: Boolean,        // true si vino de motor, false si manual
    val esDerivada: Boolean = false,
    val transaccionPadreId: String? = null,  // ID de la transacción padre si esDerivada
    val cargaId: String,                     // ref a /usuarios/{uid}/cargas/{id}
    val notaUsuario: String? = null,
    val metadataBanco: Map<String, String> = emptyMap(),
    val creadoEn: Instant
)
```

### 4.2 Modelo de dominio `Cuenta`

```kotlin
data class Cuenta(
    val id: String,                          // hash(uid + bancoCodigo + numeroCuenta)
    val uidUsuario: String,
    val bancoCodigo: String,
    val numeroCuenta: String,                // últimos 4-10 dígitos visibles
    val numeroCuentaCompleto: String?,       // IBAN si está disponible
    val alias: String,                       // editable por usuario, ej: "Cuenta nómina"
    val tipoCuenta: TipoCuenta,
    val moneda: Moneda,
    val balanceActual: BigDecimal?,          // último balance conocido
    val balanceAlCorte: BigDecimal?,
    val titular: String,
    val activa: Boolean = true,
    val ultimaSincronizacion: Instant?,
    val creadoEn: Instant
)
```

### 4.3 Modelo de dominio `Tarjeta`

```kotlin
data class Tarjeta(
    val id: String,                          // hash(uid + bancoCodigo + ultimos4)
    val uidUsuario: String,
    val bancoCodigo: String,
    val ultimos4: String,
    val alias: String,                       // editable, ej: "Qik Visa Clásica"
    val tipoRed: String?,                    // "VISA", "MASTERCARD"
    val limiteCredito: BigDecimal,
    val moneda: Moneda,
    val diaCorte: Int,                       // 1-31
    val diaPago: Int,                        // 1-31
    val tasaInteresAnual: Double,            // ej: 60.0
    val titular: String,
    val activa: Boolean = true,
    val ultimaSincronizacion: Instant?,
    val creadoEn: Instant
)

data class EstadoTarjeta(
    val id: String,                          // hash(tarjetaId + fechaCorte)
    val uidUsuario: String,
    val tarjetaId: String,
    val fechaCorte: Instant,
    val fechaLimitePago: Instant,
    val periodoInicio: Instant,
    val periodoFin: Instant,
    val balanceAlCorte: BigDecimal,
    val balanceAnterior: BigDecimal?,
    val pagoMinimo: BigDecimal,
    val pagoTotal: BigDecimal,
    val montoVencido: BigDecimal,
    val balancePromedioDiario: BigDecimal?,
    val interesFinanciamiento: BigDecimal?,
    val cashbackGanado: BigDecimal?,
    val moneda: Moneda,
    val cargaId: String,
    val creadoEn: Instant
)

data class MovimientoTarjeta(
    val id: String,                          // hash determinístico
    val uidUsuario: String,
    val tarjetaId: String,
    val bancoCodigo: String,
    val fechaTransaccion: Instant,
    val fechaPosteo: Instant?,
    val descripcionOriginal: String,
    val descripcionNormalizada: String,
    val monto: BigDecimal,                   // siempre positivo
    val tipoMovimiento: TipoMovimientoTarjeta,
    val moneda: Moneda,
    val numeroAutorizacion: String?,
    val categoriaId: String?,
    val categoriaAutomatica: Boolean,
    val cargaId: String,
    val metadataBanco: Map<String, String> = emptyMap(),
    val creadoEn: Instant
)
```

### 4.4 Modelo de dominio `Carga`

```kotlin
data class Carga(
    val id: String,
    val uidUsuario: String,
    val nombreArchivo: String,
    val tamanioBytes: Long,
    val mimeType: String?,
    val bancoCodigo: String,
    val bancoDetectadoAutomaticamente: Boolean,
    val confianzaDeteccion: Float,
    val parserVersion: Int,
    val tipoDocumento: TipoDocumento,
    val cuentaId: String?,
    val tarjetaId: String?,
    val periodoInicio: Instant?,
    val periodoFin: Instant?,
    val transaccionesInsertadas: Int,
    val transaccionesDuplicadas: Int,
    val advertencias: List<String> = emptyList(),
    val estado: EstadoCarga,
    val procesadoEn: Instant
)

enum class EstadoCarga { EXITOSO, PARCIAL, FALLIDO }
```

### 4.5 Modelo `ReglaCategoria`

```kotlin
data class ReglaCategoria(
    val id: String,
    val uidUsuario: String?,                 // null si es global
    val patron: String,                      // texto a buscar, ya normalizado
    val tipoMatch: TipoMatch,
    val categoriaId: String,
    val prioridad: Int,                      // mayor = se aplica primero
    val confianza: Int,                      // cuántas veces ha matcheado
    val activa: Boolean = true,
    val creadoPor: String,                   // "SISTEMA" o uid del usuario
    val creadoEn: Instant
)

enum class TipoMatch { IGUAL, CONTIENE, EMPIEZA_CON, REGEX }
```

### 4.6 Generación del ID determinístico

```kotlin
package com.jeanmarco.finanzas.core.crypto

import java.security.MessageDigest
import java.math.BigDecimal

object HashGenerator {

    private val md = MessageDigest.getInstance("SHA-256")

    fun hashTransaccion(
        uidUsuario: String,
        cuentaId: String,
        fecha: java.time.Instant,
        monto: BigDecimal,
        tipo: String,
        descripcionNormalizada: String
    ): String {
        val input = "$uidUsuario|$cuentaId|${fecha.epochSecond}|${monto.toPlainString()}|$tipo|$descripcionNormalizada"
        return md.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(20)
    }

    fun hashCuenta(uidUsuario: String, bancoCodigo: String, numeroCuenta: String): String {
        val input = "$uidUsuario|$bancoCodigo|$numeroCuenta"
        return md.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(20)
    }

    fun hashTarjeta(uidUsuario: String, bancoCodigo: String, ultimos4: String): String {
        val input = "$uidUsuario|$bancoCodigo|$ultimos4"
        return md.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(20)
    }
}
```

### 4.7 Normalización de descripciones

```kotlin
package com.jeanmarco.finanzas.core.extensions

fun String.normalizarDescripcion(): String {
    return this
        .trim()
        .uppercase()
        .replace(Regex("[ÁÀÄÂ]"), "A")
        .replace(Regex("[ÉÈËÊ]"), "E")
        .replace(Regex("[ÍÌÏÎ]"), "I")
        .replace(Regex("[ÓÒÖÔ]"), "O")
        .replace(Regex("[ÚÙÜÛ]"), "U")
        .replace("Ñ", "N")
        .replace(Regex("[^A-Z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
```

---

## 5. Estructura de almacenamiento en Firestore

```
/catalogoBancos/{codigoBanco}
/catalogoCategorias/{categoriaId}
/reglasCategorizacionGlobales/{reglaId}
/tasasCambio/{fechaISO}                ej: /tasasCambio/2026-05-14
/usuarios/{uid}
    ├── /cuentas/{cuentaId}
    ├── /tarjetas/{tarjetaId}
    ├── /transacciones/{txId}
    ├── /movimientosTarjeta/{movId}
    ├── /estadosTarjeta/{estadoId}
    ├── /reglasCategorias/{reglaId}
    └── /cargas/{cargaId}
```

### 5.1 Decisiones de almacenamiento

| Aspecto | Decisión |
|---------|----------|
| Tipo de datos numéricos | `BigDecimal` se serializa como `Double` con escala 2. Para precisión exacta usar `Long` representando centavos. **Decisión MVP: `Double` con 2 decimales** (suficiente para uso personal). |
| Timestamps | `Instant` se serializa como `Timestamp` nativo de Firestore. |
| Enums | Se serializan como `String` con el nombre del valor. |
| Mapas | `metadataBanco` se serializa nativamente como Firestore map. |
| Subcolecciones vs campos | Subcolecciones para colecciones independientes; mapas para metadata embebida. |

### 5.2 Batches de escritura

Firestore permite máximo 500 operaciones por batch. La estrategia es:

```kotlin
suspend fun persistirCarga(
    cuenta: Cuenta,
    transacciones: List<Transaccion>,
    carga: Carga
) {
    val firestore = Firebase.firestore
    val refUsuario = firestore.collection("usuarios").document(uid)

    transacciones.chunked(450).forEachIndexed { idx, chunk ->
        val batch = firestore.batch()

        // En el primer chunk también persistimos cuenta y carga
        if (idx == 0) {
            batch.set(refUsuario.collection("cuentas").document(cuenta.id), cuenta.toDto())
            batch.set(refUsuario.collection("cargas").document(carga.id), carga.toDto())
        }

        chunk.forEach { tx ->
            batch.set(refUsuario.collection("transacciones").document(tx.id), tx.toDto())
        }

        batch.commit().await()
    }
}
```

---

## 6. Flujo end-to-end de una transacción

Ejemplo concreto: el usuario sube el PDF de BanReservas con la transacción `06/04/2026 2055112341429 CONSUMO POS CTA CTE 226.00`.

```
Capa A — Contrato genérico
    ArchivoEntrada(nombre="EstadoDeCuenta.pdf", bytes=..., extension="pdf")
    ↓
    ParserRegistry.detectar() evalúa todos los parsers
    BanReservasPdfParser.puedeManejar() → ConfianzaDeteccion(0.95, "IBAN + texto BANRESERVAS")
    BanReservasPdfParser elegido (alta confianza)
    ↓
Capa B — Adaptación BanReservas
    BanReservasPdfParser.parsear() extrae:
        TransaccionNormalizada(
            fecha = LocalDate.of(2026, 4, 6),
            descripcionCorta = "CONSUMO POS",
            descripcionOriginal = "CONSUMO POS CTA CTE",
            monto = BigDecimal("226.00"),
            tipo = DEBITO,
            moneda = DOP,
            balanceDespues = BigDecimal("1561.75"),
            referencia = "2055112341429"
        )
    ↓
Mapper aplica:
    - Hash determinístico → id = "a3f8e1c92b4d..."
    - Normalización de descripción → "CONSUMO POS CTA CTE"
    - Categorización automática → match con regla global "CONSUMO POS" → categoriaId = "compras"
    - Conversión LocalDate → Instant UTC
    ↓
Capa C — Modelo de dominio
    Transaccion(
        id = "a3f8e1c92b4d12345abc",
        uidUsuario = "google_user_xyz",
        cuentaId = "cuenta_hash_abc",
        bancoCodigo = "BANRESERVAS",
        fecha = Instant.parse("2026-04-06T00:00:00Z"),
        descripcionCorta = "CONSUMO POS",
        descripcionOriginal = "CONSUMO POS CTA CTE",
        descripcionNormalizada = "CONSUMO POS CTA CTE",
        monto = BigDecimal("226.00"),
        tipo = TipoTransaccion.DEBITO,
        moneda = Moneda.DOP,
        balanceDespues = BigDecimal("1561.75"),
        referencia = "2055112341429",
        categoriaId = "compras",
        categoriaAutomatica = true,
        cargaId = "carga_xyz_123",
        creadoEn = Instant.now()
    )
    ↓
Persistencia en Firestore
    /usuarios/google_user_xyz/transacciones/a3f8e1c92b4d12345abc
```

---

## 7. Cómo agregar BHD (u otro banco) en el futuro

Cuando llegue la muestra de BHD, los pasos son:

1. **Analizar el formato** del estado de cuenta (PDF, CSV, XLS).
2. **Crear** `data/parsers/bhd/BhdParser.kt` implementando `BankParser`.
3. **Definir patrones de detección** en `puedeManejar()` (logo, texto, IBAN, etc.).
4. **Implementar `parsear()`** que mapee el formato propietario al modelo común usando los `TransaccionNormalizada` / `CuentaDetectada` / etc.
5. **Registrar** en `ParserModule.kt`:
   ```kotlin
   @Binds @IntoSet
   abstract fun bindBhd(impl: BhdParser): BankParser
   ```
6. **Actualizar** `/catalogoBancos/BHD` poniendo `tieneParser = true`.
7. **Agregar fixture** en `app/src/test/resources/fixtures/bhd_v1.{ext}` y escribir tests.
8. **Compilar y deployar.** El resto del sistema funciona sin tocar.

Si BHD trae un formato similar a otro banco existente (ej: CSV similar a Popular), considerar compartir un parser base abstracto en `parsers/common/`.

---

## 8. Resumen visual de las tres capas

```
┌──────────────────────────────────────────────────────────┐
│ Capa A — Contrato genérico                               │
│   interface BankParser                                   │
│   ResultadoParseo (sealed)                               │
│   ParserRegistry                                         │
└──────────────────────────────────────────────────────────┘
                          ↑
        Cada parser implementa el contrato
                          ↑
┌──────────────────────────────────────────────────────────┐
│ Capa B — Adaptación por banco                            │
│   BanReservasPdfParser                                   │
│   PopularCsvParser                                       │
│   QikPdfParser                                           │
│   GenericoTarjetaXlsParser                               │
│   (futuro: BhdParser, ScotiaBankParser, etc.)            │
└──────────────────────────────────────────────────────────┘
                          ↓
       Cada parser produce TransaccionNormalizada
                          ↓
┌──────────────────────────────────────────────────────────┐
│ Capa C — Formato de salida estandarizado                 │
│   Transaccion (dominio)                                  │
│   Cuenta, Tarjeta, EstadoTarjeta, MovimientoTarjeta     │
│   Carga (auditoría)                                      │
│   Persistido en Firestore con IDs determinísticos        │
└──────────────────────────────────────────────────────────┘
```

Este modelado garantiza que **toda la app trabaja con un único formato** (capa C) sin importar cuántos bancos se agreguen. La complejidad del mundo real queda contenida exclusivamente en la capa B, donde puede crecer sin contaminar nada.
