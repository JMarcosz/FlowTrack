package com.example.flowtrack.data.firestore.mappers

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.crypto.HashGenerator
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.firestore.dto.ConfiguracionUsuarioDto
import com.example.flowtrack.data.parsers.core.EstadoCuentaNormalizado
import com.example.flowtrack.data.parsers.core.MovimientoNormalizado
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.data.firestore.dto.CuentaDto
import com.example.flowtrack.data.firestore.dto.DispositivoPushDto
import com.example.flowtrack.data.firestore.dto.EstadoTarjetaSnapDto
import com.example.flowtrack.data.firestore.dto.MovimientoTarjetaDto
import com.example.flowtrack.data.firestore.dto.NotificacionConfigDto
import com.example.flowtrack.data.firestore.dto.ReglaCategoriaDto
import com.example.flowtrack.data.firestore.dto.ReglaSugeridaDto
import com.example.flowtrack.data.firestore.dto.TarjetaDto
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.parsers.core.CuentaDetectada
import com.example.flowtrack.data.parsers.core.EstadoTarjetaDetectado
import com.example.flowtrack.data.parsers.core.MovimientoTarjetaNormalizado
import com.example.flowtrack.data.parsers.core.TarjetaDetectada
import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.ConfiguracionUsuario
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.DispositivoPush
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.NotificacionConfig
import com.example.flowtrack.domain.model.OrigenTasa
import com.example.flowtrack.domain.model.OrigenTransaccion
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.ReglaSugerida
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.EstadoTransaccion
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ZONA_RD = ZoneId.of("America/Santo_Domingo")

// --- Métodos de Ayuda para Conversión ---
private fun BigDecimal.toFirestoreString(): String = setScale(2).toPlainString()
private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    runCatching { enumValueOf<T>(this?.takeIf { it.isNotBlank() } ?: default.name) }.getOrDefault(default)

fun Any?.toBigDecimalCompat(): BigDecimal? = when (this) {
    null -> null
    is BigDecimal -> setScale(2)
    is Number -> BigDecimal.valueOf(toDouble()).setScale(2)
    is String -> toBigDecimalOrNull()?.setScale(2)
    else -> null
}

fun DocumentSnapshot.money(field: String): BigDecimal? = get(field).toBigDecimalCompat()

fun Instant.toTimestamp(): Timestamp = Timestamp(this.epochSecond, this.nano)
fun LocalDate.toInstantRD(): Instant = atStartOfDay(ZONA_RD).toInstant()
fun LocalDate.toTimestamp(): Timestamp = toInstantRD().toTimestamp()

private val HORA_NOTIFICACION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalTime.toFirestoreString(): String = format(HORA_NOTIFICACION_FORMATTER)

fun String?.toLocalTimeCompat(): LocalTime =
    if (this == null) LocalTime.of(8, 0)
    else runCatching { LocalTime.parse(if (contains(":")) this else "$this:00", HORA_NOTIFICACION_FORMATTER) }
        .getOrDefault(LocalTime.of(8, 0))


// ─── Domain → DTO ─────────────────────────────────────────────────────────────

fun DispositivoPush.toDto(): DispositivoPushDto =
    DispositivoPushDto(
        id = id,
        uidUsuario = uidUsuario,
        tokenFcm = tokenFcm,
        activo = activo,
        actualizadoEn = actualizadoEn.toTimestamp(),
        ultimoUsuarioUid = ultimoUsuarioUid,
        versionApp = versionApp,
        modeloDispositivo = modeloDispositivo,
    )

fun ConfiguracionUsuario.toDto(): ConfiguracionUsuarioDto =
    ConfiguracionUsuarioDto(
        uidUsuario = uidUsuario,
        idioma = idioma,
        formatoFecha = formatoFecha,
        formatoMoneda = formatoMoneda,
        monedaPredeterminada = monedaPredeterminada.name,
        temaOscuro = temaOscuro,
        ultimoBackup = ultimoBackup?.toTimestamp(),
    )

fun ConfiguracionUsuarioDto.toDomain(): ConfiguracionUsuario =
    ConfiguracionUsuario(
        uidUsuario = uidUsuario,
        idioma = idioma,
        formatoFecha = formatoFecha,
        formatoMoneda = formatoMoneda,
        monedaPredeterminada = runCatching { Moneda.valueOf(monedaPredeterminada) }.getOrDefault(Moneda.DOP),
        temaOscuro = temaOscuro,
        ultimoBackup = ultimoBackup?.toDate()?.toInstant(),
    )

fun DocumentSnapshot.toConfiguracionUsuarioDto(): ConfiguracionUsuarioDto? =
    toObject(ConfiguracionUsuarioDto::class.java)

fun NotificacionConfig.toDto(): NotificacionConfigDto =
    NotificacionConfigDto(
        uidUsuario = uidUsuario,
        activa = activa,
        pago7dias = pago7dias,
        pago3dias = pago3dias,
        pago1dia = pago1dia,
        pagoMismoDia = pagoMismoDia,
        resumenMensual = resumenMensual,
        alertasGastosAltos = alertasGastosAltos,
        umbralGastoAlto = umbralGastoAlto.toFirestoreString(),
        horaNotificacion = horaNotificacion.toFirestoreString(),
        zonaHoraria = zonaHoraria,
    )

fun NotificacionConfigDto.toDomain(): NotificacionConfig =
    NotificacionConfig(
        uidUsuario = uidUsuario,
        activa = activa,
        pago7dias = pago7dias,
        pago3dias = pago3dias,
        pago1dia = pago1dia,
        pagoMismoDia = pagoMismoDia,
        resumenMensual = resumenMensual,
        alertasGastosAltos = alertasGastosAltos,
        umbralGastoAlto = umbralGastoAlto.toBigDecimalCompat() ?: BigDecimal("5000.00"),
        horaNotificacion = horaNotificacion.toLocalTimeCompat(),
        zonaHoraria = if (zonaHoraria.isNullOrBlank()) "America/Santo_Domingo" else zonaHoraria,
    )

fun DocumentSnapshot.toNotificacionConfigDto(): NotificacionConfigDto? =
    toObject(NotificacionConfigDto::class.java)

fun ReglaCategoria.toDto(): ReglaCategoriaDto =
    ReglaCategoriaDto(
        id = id,
        uidUsuario = uidUsuario.orEmpty(),
        patron = patron,
        tipoMatch = tipoMatch.name,
        categoriaId = categoriaId,
        prioridad = prioridad,
        confianza = confianza,
        activa = activa,
        creadoPor = creadoPor,
        creadoEn = creadoEn.toTimestamp(),
    )

fun ReglaCategoriaDto.toDomain(): ReglaCategoria =
    ReglaCategoria(
        id = id,
        uidUsuario = uidUsuario.takeUnless { it.isBlank() },
        patron = patron,
        tipoMatch = runCatching { com.example.flowtrack.domain.model.TipoMatch.valueOf(tipoMatch) }.getOrDefault(com.example.flowtrack.domain.model.TipoMatch.CONTIENE),
        categoriaId = categoriaId,
        prioridad = prioridad,
        confianza = confianza,
        activa = activa,
        creadoPor = creadoPor,
        creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
    )

fun DocumentSnapshot.toReglaCategoriaDto(): ReglaCategoriaDto? =
    toObject(ReglaCategoriaDto::class.java)

fun ReglaSugerida.toDto(): ReglaSugeridaDto =
    ReglaSugeridaDto(
        id = id,
        uidUsuario = uidUsuario,
        patronDetectado = patronDetectado,
        categoriaSugerida = categoriaSugerida,
        muestras = muestras,
        confianzaCluster = confianzaCluster,
        creadaEn = creadaEn.toTimestamp(),
        aceptada = aceptada,
        resueltaEn = resueltaEn?.toTimestamp(),
    )

fun ReglaSugeridaDto.toDomain(): ReglaSugerida =
    ReglaSugerida(
        id = id,
        uidUsuario = uidUsuario,
        patronDetectado = patronDetectado,
        categoriaSugerida = categoriaSugerida,
        muestras = muestras,
        confianzaCluster = confianzaCluster,
        creadaEn = creadaEn?.toDate()?.toInstant() ?: Instant.now(),
        aceptada = aceptada,
        resueltaEn = resueltaEn?.toDate()?.toInstant(),
    )

fun DocumentSnapshot.toReglaSugeridaDto(): ReglaSugeridaDto? =
    toObject(ReglaSugeridaDto::class.java)


// ─── Transaccion mapper ───────────────────────────────────────────────────────

fun TransaccionNormalizada.toDomain(
    uidUsuario: String,
    cuentaId: String,
    bancoCodigo: String,
    cargaId: String,
    categoriaId: String? = null,
): Transaccion {
    val fechaInstant = fecha.toInstantRD()
    val descNorm = descripcionOriginal.normalizarDescripcion()
    val id = HashGenerator.hashTransaccion(
        uidUsuario = uidUsuario,
        cuentaId = cuentaId,
        fecha = fechaInstant,
        monto = monto,
        tipo = tipo.name,
        descripcionNormalizada = descNorm,
    )
    return Transaccion(
        id = id,
        uidUsuario = uidUsuario,
        cuentaId = cuentaId,
        bancoCodigo = bancoCodigo,
        fecha = fechaInstant,
        fechaPosteo = fechaPosteo?.toInstantRD(),
        descripcionCorta = descripcionCorta,
        descripcionOriginal = descripcionOriginal,
        descripcionNormalizada = descNorm,
        monto = monto,
        tipo = tipo,
        moneda = moneda,
        balanceDespues = balanceDespues,
        referencia = referencia,
        serial = serial,
        categoriaId = categoriaId,
        categoriaAutomatica = categoriaId != null,
        esDerivada = esDerivada,
        transaccionPadreId = null, // se vincula en una segunda pasada
        derivadasIds = emptyList(),
        origen = OrigenTransaccion.IMPORTACION_ARCHIVO,
        sourceEventId = null,
        sourceMessageId = null,
        sourceTransactionId = null,
        actualizadoEn = Instant.now(),
        estado = EstadoTransaccion.APROBADA,
        afectaBalance = true,
        posibleDuplicado = false,
        motivoRechazo = null,
        cargaId = cargaId,
        metadataBanco = metadataBanco,
        creadoEn = Instant.now(),
    )
}

fun Transaccion.toDto(): TransaccionDto = TransaccionDto(
    id = id,
    uidUsuario = uidUsuario,
    cuentaId = cuentaId,
    bancoCodigo = bancoCodigo,
    fecha = fecha.toTimestamp(),
    fechaPosteo = fechaPosteo?.toTimestamp(),
    descripcionCorta = descripcionCorta,
    descripcionOriginal = descripcionOriginal,
    descripcionNormalizada = descripcionNormalizada,
    monto = monto.toFirestoreString(),
    tipo = tipo.name,
    moneda = moneda.name,
    balanceDespues = balanceDespues?.toFirestoreString(),
    referencia = referencia,
    serial = serial,
    categoriaId = categoriaId,
    categoriaAutomatica = categoriaAutomatica,
    esDerivada = esDerivada,
    transaccionPadreId = transaccionPadreId,
    derivadasIds = derivadasIds,
    origen = origen.name,
    sourceEventId = sourceEventId,
    sourceMessageId = sourceMessageId,
    sourceTransactionId = sourceTransactionId,
    actualizadoEn = actualizadoEn.toTimestamp(),
    estado = estado.name,
    afectaBalance = afectaBalance,
    posibleDuplicado = posibleDuplicado,
    motivoRechazo = motivoRechazo,
    cargaId = cargaId,
    notaUsuario = notaUsuario,
    metadataBanco = metadataBanco,
    creadoEn = creadoEn.toTimestamp(),
)

fun TransaccionDto.toDomain(): Transaccion = Transaccion(
    id = id,
    uidUsuario = uidUsuario,
    cuentaId = cuentaId,
    bancoCodigo = bancoCodigo,
    fecha = fecha?.toDate()?.toInstant() ?: Instant.now(),
    fechaPosteo = fechaPosteo?.toDate()?.toInstant(),
    descripcionCorta = descripcionCorta,
    descripcionOriginal = descripcionOriginal,
    descripcionNormalizada = descripcionNormalizada,
    monto = monto.toBigDecimalCompat() ?: BigDecimal.ZERO.setScale(2),
    tipo = TipoTransaccion.valueOf(tipo),
    moneda = Moneda.valueOf(moneda),
    balanceDespues = balanceDespues.toBigDecimalCompat(),
    referencia = referencia,
    serial = serial,
    categoriaId = categoriaId,
    categoriaAutomatica = categoriaAutomatica,
    esDerivada = esDerivada,
    transaccionPadreId = transaccionPadreId,
    derivadasIds = derivadasIds,
    origen = origen.toEnumOrDefault(OrigenTransaccion.IMPORTACION_ARCHIVO),
    sourceEventId = sourceEventId,
    sourceMessageId = sourceMessageId,
    sourceTransactionId = sourceTransactionId,
    actualizadoEn = actualizadoEn?.toDate()?.toInstant() ?: creadoEn?.toDate()?.toInstant() ?: Instant.now(),
    estado = estado.toEnumOrDefault(EstadoTransaccion.APROBADA),
    afectaBalance = afectaBalance,
    posibleDuplicado = posibleDuplicado,
    motivoRechazo = motivoRechazo,
    cargaId = cargaId,
    notaUsuario = notaUsuario,
    metadataBanco = metadataBanco,
    creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
)

// ─── Cuenta mapper ────────────────────────────────────────────────────────────

fun CuentaDetectada.toDomain(uidUsuario: String): Cuenta {
    val id = HashGenerator.hashCuenta(uidUsuario, "BANRESERVAS", numeroCuenta)
    return Cuenta(
        id = id,
        uidUsuario = uidUsuario,
        bancoCodigo = "BANRESERVAS",
        numeroCuenta = numeroCuenta,
        numeroCuentaCompleto = numeroCuentaCompleto,
        alias = "BanReservas $numeroCuenta",
        tipoCuenta = tipoCuenta,
        moneda = moneda,
        balanceActual = balanceAlCorte,
        balanceAlCorte = balanceAlCorte,
        titular = titular,
        activa = true,
        mostrarEnDashboard = true,
        ultimaSincronizacion = Instant.now(),
        creadoEn = Instant.now(),
    )
}

fun Cuenta.toDto(): CuentaDto = CuentaDto(
    id = id,
    uidUsuario = uidUsuario,
    bancoCodigo = bancoCodigo,
    numeroCuenta = numeroCuenta,
    numeroCuentaCompleto = numeroCuentaCompleto,
    alias = alias,
    tipoCuenta = tipoCuenta.name,
    moneda = moneda.name,
    balanceActual = balanceActual?.toFirestoreString(),
    balanceAlCorte = balanceAlCorte?.toFirestoreString(),
    fechaUltimoCorte = fechaUltimoCorte?.toTimestamp(),
    titular = titular,
    activa = activa,
    mostrarEnDashboard = mostrarEnDashboard,
    ultimaSincronizacion = ultimaSincronizacion?.toTimestamp(),
    creadoEn = creadoEn.toTimestamp(),
)

fun CuentaDto.toDomain(): Cuenta = Cuenta(
    id = id,
    uidUsuario = uidUsuario,
    bancoCodigo = bancoCodigo,
    numeroCuenta = numeroCuenta,
    numeroCuentaCompleto = numeroCuentaCompleto,
    alias = alias,
    tipoCuenta = TipoCuenta.valueOf(tipoCuenta),
    moneda = Moneda.valueOf(moneda),
    balanceActual = balanceActual.toBigDecimalCompat(),
    balanceAlCorte = balanceAlCorte.toBigDecimalCompat(),
    fechaUltimoCorte = fechaUltimoCorte?.toDate()?.toInstant(),
    titular = titular,
    activa = activa,
    mostrarEnDashboard = mostrarEnDashboard,
    ultimaSincronizacion = ultimaSincronizacion?.toDate()?.toInstant(),
    creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
)

// ─── Carga mapper ─────────────────────────────────────────────────────────────

fun Carga.toDto(): CargaDto = CargaDto(
    id = id,
    uidUsuario = uidUsuario,
    nombreArchivo = nombreArchivo,
    tamanioBytes = tamanioBytes,
    mimeType = mimeType,
    bancoCodigo = bancoCodigo,
    parserVersion = parserVersion,
    tipoDocumento = tipoDocumento.name,
    cuentaId = cuentaId,
    tarjetaId = tarjetaId,
    periodoInicio = periodoInicio?.toTimestamp(),
    periodoFin = periodoFin?.toTimestamp(),
    transaccionesInsertadas = transaccionesInsertadas,
    transaccionesDuplicadas = transaccionesDuplicadas,
    advertencias = advertencias,
    estado = estado.name,
    procesadoEn = procesadoEn.toTimestamp(),
)

fun CargaDto.toDomain(): Carga = Carga(
    id = id,
    uidUsuario = uidUsuario,
    nombreArchivo = nombreArchivo,
    tamanioBytes = tamanioBytes,
    mimeType = mimeType,
    bancoCodigo = bancoCodigo,
    parserVersion = parserVersion,
    tipoDocumento = TipoDocumento.valueOf(tipoDocumento),
    cuentaId = cuentaId,
    tarjetaId = tarjetaId,
    periodoInicio = periodoInicio?.toDate()?.toInstant(),
    periodoFin = periodoFin?.toDate()?.toInstant(),
    transaccionesInsertadas = transaccionesInsertadas,
    transaccionesDuplicadas = transaccionesDuplicadas,
    advertencias = advertencias,
    estado = runCatching { EstadoCarga.valueOf(estado) }.getOrDefault(EstadoCarga.EXITOSO),
    procesadoEn = procesadoEn?.toDate()?.toInstant() ?: Instant.now(),
    eliminadoEn = eliminadoEn?.toDate()?.toInstant(),
)

// ─── Tarjeta mapper ───────────────────────────────────────────────────────────

fun TarjetaDetectada.toDomain(
    uidUsuario: String,
    bancoCodigo: String,
): Tarjeta {
    val id = HashGenerator.hashTarjeta(uidUsuario, bancoCodigo, ultimos4)
    return Tarjeta(
        id = id,
        uidUsuario = uidUsuario,
        bancoCodigo = bancoCodigo,
        ultimos4 = ultimos4,
        alias = "$bancoCodigo ****$ultimos4",
        tipoRed = tipoRed,
        limiteCredito = limiteCredito,
        moneda = moneda,
        diaCorte = diaCorte ?: 1,
        diaPago = diaPago ?: 1,
        tasaInteresAnual = tasaInteresAnual ?: 0.0,
        tasaInteresOrigen = if (tasaInteresAnual != null) OrigenTasa.AUTO_EXTRAIDA else OrigenTasa.MANUAL,
        estado = EstadoTarjeta.ACTIVO,
        titular = titular,
        activa = true,
        ultimaSincronizacion = Instant.now(),
        creadoEn = Instant.now(),
    )
}

fun Tarjeta.toDto(): TarjetaDto = TarjetaDto(
    id = id,
    uidUsuario = uidUsuario,
    bancoCodigo = bancoCodigo,
    ultimos4 = ultimos4,
    alias = alias,
    tipoRed = tipoRed,
    limiteCredito = limiteCredito.toFirestoreString(),
    moneda = moneda.name,
    diaCorte = diaCorte,
    diaPago = diaPago,
    tasaInteresAnual = BigDecimal.valueOf(tasaInteresAnual).toFirestoreString(),
    tasaInteresOrigen = tasaInteresOrigen.name,
    estado = estado.name,
    titular = titular,
    activa = activa,
    ultimaSincronizacion = ultimaSincronizacion?.toTimestamp(),
    creadoEn = creadoEn.toTimestamp(),
)

fun TarjetaDto.toDomain(): Tarjeta = Tarjeta(
    id = id,
    uidUsuario = uidUsuario,
    bancoCodigo = bancoCodigo,
    ultimos4 = ultimos4,
    alias = alias,
    tipoRed = tipoRed,
    limiteCredito = limiteCredito.toBigDecimalCompat() ?: BigDecimal.ZERO.setScale(2),
    moneda = Moneda.valueOf(moneda),
    diaCorte = diaCorte,
    diaPago = diaPago,
    tasaInteresAnual = tasaInteresAnual.toDoubleOrNull() ?: 0.0,
    tasaInteresOrigen = OrigenTasa.valueOf(tasaInteresOrigen),
    estado = EstadoTarjeta.valueOf(estado),
    titular = titular,
    activa = activa,
    ultimaSincronizacion = ultimaSincronizacion?.toDate()?.toInstant(),
    creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
)

// ─── EstadoTarjetaSnap mapper ─────────────────────────────────────────────────

fun EstadoTarjetaDetectado.toDomain(
    uidUsuario: String,
    tarjetaId: String,
    cargaId: String,
    moneda: Moneda,
    periodoInicio: Instant,
    periodoFin: Instant,
): EstadoTarjetaSnap {
    val fechaCorteInstant = fechaCorte.toInstantRD()
    val id = HashGenerator.hashEstadoTarjeta(tarjetaId, fechaCorteInstant)
    return EstadoTarjetaSnap(
        id = id,
        uidUsuario = uidUsuario,
        tarjetaId = tarjetaId,
        fechaCorte = fechaCorteInstant,
        fechaLimitePago = fechaLimitePago.toInstantRD(),
        periodoInicio = periodoInicio,
        periodoFin = periodoFin,
        balanceAlCorte = balanceAlCorte,
        balanceAnterior = balanceAnterior,
        pagoMinimo = pagoMinimo,
        pagoTotal = pagoTotal,
        montoVencido = montoVencido,
        balancePromedioDiario = balancePromedioDiario,
        interesFinanciamiento = interesPorFinanciamiento,
        cashbackGanado = cashbackGanado,
        moneda = moneda,
        cargaId = cargaId,
        creadoEn = Instant.now(),
    )
}

fun EstadoTarjetaSnap.toDto(): EstadoTarjetaSnapDto = EstadoTarjetaSnapDto(
    id = id,
    uidUsuario = uidUsuario,
    tarjetaId = tarjetaId,
    fechaCorte = fechaCorte.toTimestamp(),
    fechaLimitePago = fechaLimitePago.toTimestamp(),
    periodoInicio = periodoInicio.toTimestamp(),
    periodoFin = periodoFin.toTimestamp(),
    balanceAlCorte = balanceAlCorte.toFirestoreString(),
    balanceAnterior = balanceAnterior?.toFirestoreString(),
    pagoMinimo = pagoMinimo.toFirestoreString(),
    pagoTotal = pagoTotal.toFirestoreString(),
    montoVencido = montoVencido.toFirestoreString(),
    balancePromedioDiario = balancePromedioDiario?.toFirestoreString(),
    interesFinanciamiento = interesFinanciamiento?.toFirestoreString(),
    cashbackGanado = cashbackGanado?.toFirestoreString(),
    moneda = moneda.name,
    cargaId = cargaId,
    creadoEn = creadoEn.toTimestamp(),
)

fun EstadoTarjetaSnapDto.toDomain(): EstadoTarjetaSnap = EstadoTarjetaSnap(
    id = id,
    uidUsuario = uidUsuario,
    tarjetaId = tarjetaId,
    fechaCorte = fechaCorte?.toDate()?.toInstant() ?: Instant.now(),
    fechaLimitePago = fechaLimitePago?.toDate()?.toInstant() ?: Instant.now(),
    periodoInicio = periodoInicio?.toDate()?.toInstant() ?: Instant.now(),
    periodoFin = periodoFin?.toDate()?.toInstant() ?: Instant.now(),
    balanceAlCorte = balanceAlCorte.toBigDecimalCompat() ?: BigDecimal.ZERO.setScale(2),
    balanceAnterior = balanceAnterior.toBigDecimalCompat(),
    pagoMinimo = pagoMinimo.toBigDecimalCompat() ?: BigDecimal.ZERO.setScale(2),
    pagoTotal = pagoTotal.toBigDecimalCompat() ?: BigDecimal.ZERO.setScale(2),
    montoVencido = montoVencido.toBigDecimalCompat() ?: BigDecimal.ZERO.setScale(2),
    balancePromedioDiario = balancePromedioDiario.toBigDecimalCompat(),
    interesFinanciamiento = interesFinanciamiento.toBigDecimalCompat(),
 cashbackGanado = cashbackGanado.toBigDecimalCompat(),
    moneda = Moneda.valueOf(moneda),
    cargaId = cargaId,
    creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
)

// ─── MovimientoTarjeta mapper ─────────────────────────────────────────────────

fun MovimientoTarjetaNormalizado.toDomain(
    uidUsuario: String,
    tarjetaId: String,
    bancoCodigo: String,
    cargaId: String,
): MovimientoTarjeta {
    val fechaInstant = fechaTransaccion.toInstantRD()
    val descNorm = descripcionOriginal.normalizarDescripcion()
    val id = HashGenerator.hashMovimientoTarjeta(
        uidUsuario = uidUsuario,
        tarjetaId = tarjetaId,
        fecha = fechaInstant,
        monto = monto,
        tipo = tipoMovimiento.name,
        descripcionNormalizada = descNorm,
    )
    return MovimientoTarjeta(
        id = id,
        uidUsuario = uidUsuario,
        tarjetaId = tarjetaId,
        bancoCodigo = bancoCodigo,
        fechaTransaccion = fechaInstant,
        fechaPosteo = fechaPosteo?.toInstantRD(),
        descripcionOriginal = descripcionOriginal,
        descripcionNormalizada = descNorm,
        monto = monto,
        tipoMovimiento = tipoMovimiento,
        moneda = moneda,
        numeroAutorizacion = numeroAutorizacion,
        categoriaId = null,
        categoriaAutomatica = false,
        cargaId = cargaId,
        metadataBanco = metadataBanco,
        creadoEn = Instant.now(),
    )
}

fun MovimientoTarjeta.toDto(): MovimientoTarjetaDto = MovimientoTarjetaDto(
    id = id,
    uidUsuario = uidUsuario,
    tarjetaId = tarjetaId,
    bancoCodigo = bancoCodigo,
    fechaTransaccion = fechaTransaccion.toTimestamp(),
    fechaPosteo = fechaPosteo?.toTimestamp(),
    descripcionOriginal = descripcionOriginal,
    descripcionNormalizada = descripcionNormalizada,
    monto = monto.toFirestoreString(),
    montoUsd = montoUsd?.toFirestoreString(),
    tipoMovimiento = tipoMovimiento.name,
    moneda = moneda.name,
    numeroAutorizacion = numeroAutorizacion,
    categoriaId = categoriaId,
    categoriaAutomatica = categoriaAutomatica,
    cargaId = cargaId,
    metadataBanco = metadataBanco,
    creadoEn = creadoEn.toTimestamp(),
)

fun MovimientoTarjetaDto.toDomain(): MovimientoTarjeta = MovimientoTarjeta(
    id = id,
    uidUsuario = uidUsuario,
    tarjetaId = tarjetaId,
    bancoCodigo = bancoCodigo,
    fechaTransaccion = fechaTransaccion?.toDate()?.toInstant() ?: Instant.now(),
    fechaPosteo = fechaPosteo?.toDate()?.toInstant(),
    descripcionOriginal = descripcionOriginal,
    descripcionNormalizada = descripcionNormalizada,
    monto = monto.toBigDecimalCompat() ?: BigDecimal.ZERO.setScale(2),
    montoUsd = montoUsd.toBigDecimalCompat(),
    tipoMovimiento = TipoMovimientoTarjeta.valueOf(tipoMovimiento),
    moneda = Moneda.valueOf(moneda),
    numeroAutorizacion = numeroAutorizacion,
    categoriaId = categoriaId,
    categoriaAutomatica = categoriaAutomatica,
    cargaId = cargaId,
    metadataBanco = metadataBanco,
    creadoEn = creadoEn?.toDate()?.toInstant() ?: Instant.now(),
)

// ─── Mappers para BankStatementParser (EstadoCuentaNormalizado / MovimientoNormalizado) ──

fun TipoMovimiento.toTipoTransaccion(): TipoTransaccion = when (this) {
    TipoMovimiento.INGRESO,
    TipoMovimiento.CASHBACK,
    TipoMovimiento.DEVOLUCION -> TipoTransaccion.CREDITO
    else -> TipoTransaccion.DEBITO
}

fun TipoMovimiento.toTipoMovimientoTarjeta(): TipoMovimientoTarjeta = when (this) {
    TipoMovimiento.PAGO_TARJETA -> TipoMovimientoTarjeta.PAGO
    TipoMovimiento.INTERES      -> TipoMovimientoTarjeta.INTERES
    TipoMovimiento.COMISION     -> TipoMovimientoTarjeta.COMISION
    TipoMovimiento.CASHBACK     -> TipoMovimientoTarjeta.CASHBACK
    TipoMovimiento.AJUSTE       -> TipoMovimientoTarjeta.AJUSTE
    TipoMovimiento.RETIRO_ATM   -> TipoMovimientoTarjeta.AVANCE_EFECTIVO
    TipoMovimiento.DEVOLUCION   -> TipoMovimientoTarjeta.DEVOLUCION
    else                        -> TipoMovimientoTarjeta.COMPRA
}

fun MovimientoNormalizado.toDomainTransaccion(
    uidUsuario: String,
    cuentaId: String,
    bancoCodigo: String,
    cargaId: String,
): Transaccion {
    val fechaInstant = fechaTransaccion.toInstantRD()
    val id = HashGenerator.hashTransaccion(
        uidUsuario = uidUsuario,
        cuentaId = cuentaId,
        fecha = fechaInstant,
        monto = monto,
        tipo = tipo.toTipoTransaccion().name,
        descripcionNormalizada = descripcionNormalizada,
    )
    return Transaccion(
        id = id,
        uidUsuario = uidUsuario,
        cuentaId = cuentaId,
        bancoCodigo = bancoCodigo,
        fecha = fechaInstant,
        fechaPosteo = fechaPosteo?.toInstantRD(),
        descripcionCorta = descripcionCorta,
        descripcionOriginal = descripcionOriginal,
        descripcionNormalizada = descripcionNormalizada,
        monto = monto,
        tipo = tipo.toTipoTransaccion(),
        moneda = moneda,
        balanceDespues = balancePosterior,
        referencia = referencia,
        serial = null,
        categoriaId = null,
        categoriaAutomatica = false,
        esDerivada = false,
        transaccionPadreId = null,
        derivadasIds = emptyList(),
        origen = OrigenTransaccion.IMPORTACION_ARCHIVO,
        sourceEventId = null,
        sourceMessageId = null,
        sourceTransactionId = null,
        actualizadoEn = Instant.now(),
        estado = EstadoTransaccion.APROBADA,
        afectaBalance = true,
        posibleDuplicado = false,
        motivoRechazo = null,
        cargaId = cargaId,
        metadataBanco = metadata,
        creadoEn = Instant.now(),
    )
}

fun MovimientoNormalizado.toDomainMovimientoTarjeta(
    uidUsuario: String,
    tarjetaId: String,
    bancoCodigo: String,
    cargaId: String,
): MovimientoTarjeta {
    val fechaInstant = fechaTransaccion.toInstantRD()
    val id = HashGenerator.hashMovimientoTarjeta(
        uidUsuario = uidUsuario,
        tarjetaId = tarjetaId,
        fecha = fechaInstant,
        monto = monto,
        tipo = tipo.toTipoMovimientoTarjeta().name,
        descripcionNormalizada = descripcionNormalizada,
        montoUsd = montoUsd,
    )
    return MovimientoTarjeta(
        id = id,
        uidUsuario = uidUsuario,
        tarjetaId = tarjetaId,
        bancoCodigo = bancoCodigo,
        fechaTransaccion = fechaInstant,
        fechaPosteo = fechaPosteo?.toInstantRD(),
        descripcionOriginal = descripcionOriginal,
        descripcionNormalizada = descripcionNormalizada,
        monto = monto,
        montoUsd = montoUsd,
        tipoMovimiento = tipo.toTipoMovimientoTarjeta(),
        moneda = moneda,
        numeroAutorizacion = referencia,
        categoriaId = null,
        categoriaAutomatica = false,
        cargaId = cargaId,
        metadataBanco = metadata,
        creadoEn = Instant.now(),
    )
}

fun EstadoCuentaNormalizado.toDomainCuenta(uidUsuario: String): Cuenta {
    val numeroCuenta = (productoId ?: "DESCONOCIDA").takeLast(10)
    val id = HashGenerator.hashCuenta(uidUsuario, bancoCodigo, numeroCuenta)

    // Ordenar por fecha ascendente para que el último movimiento sea siempre el más reciente,
    // independientemente del orden en que el parser leyó las secciones del archivo.
    val movimientosOrdenados = movimientos.sortedBy { it.fechaTransaccion }
    val balancePorFecha = movimientosOrdenados
        .lastOrNull { it.balancePosterior != null }
        ?.balancePosterior
    // Preferir el balance derivado de movimientos ordenados; caer en balanceFinal del parser
    // solo cuando ningún movimiento tiene balancePosterior (ej. Qik/Cibao usan balanceCorte explícito).
    val balanceEfectivo = balancePorFecha ?: balanceFinal

    val fechaCorteInst = (movimientosOrdenados.lastOrNull()?.fechaTransaccion ?: fechaFin)
        ?.atStartOfDay(ZoneId.of("America/Santo_Domingo"))?.toInstant()

    return Cuenta(
        id = id,
        uidUsuario = uidUsuario,
        bancoCodigo = bancoCodigo,
        numeroCuenta = numeroCuenta,
        numeroCuentaCompleto = numeroCuentaCompleto,
        alias = "$bancoCodigo $numeroCuenta",
        tipoCuenta = TipoCuenta.CORRIENTE,
        moneda = moneda,
        balanceActual = balanceEfectivo,
        balanceAlCorte = balanceEfectivo,
        fechaUltimoCorte = fechaCorteInst,
        titular = titular ?: "TITULAR",
        activa = true,
        mostrarEnDashboard = true,
        ultimaSincronizacion = Instant.now(),
        creadoEn = Instant.now(),
    )
}

fun EstadoCuentaNormalizado.toDomainTarjeta(uidUsuario: String): Tarjeta {
    val ultimos4 = productoId ?: "0000"
    val id = HashGenerator.hashTarjeta(uidUsuario, bancoCodigo, ultimos4)
    return Tarjeta(
        id = id,
        uidUsuario = uidUsuario,
        bancoCodigo = bancoCodigo,
        ultimos4 = ultimos4,
        alias = "$bancoCodigo ****$ultimos4",
        tipoRed = tipoRed,
        limiteCredito = limiteCredito ?: BigDecimal.ZERO,
        moneda = moneda,
        diaCorte = diaCorte ?: 1,
        diaPago = diaPago ?: 1,
        tasaInteresAnual = tasaInteresAnual ?: 0.0,
        tasaInteresOrigen = if (tasaInteresAnual != null) OrigenTasa.AUTO_EXTRAIDA else OrigenTasa.MANUAL,
        estado = EstadoTarjeta.ACTIVO,
        titular = titular ?: "TITULAR",
        activa = true,
        ultimaSincronizacion = Instant.now(),
        creadoEn = Instant.now(),
    )
}

fun EstadoCuentaNormalizado.toDomainEstadoTarjeta(
    uidUsuario: String,
    tarjetaId: String,
    cargaId: String,
    periodoInicio: Instant,
    periodoFin: Instant,
): EstadoTarjetaSnap {
    val corteInstant = (fechaCorte ?: fechaFin ?: LocalDate.now()).toInstantRD()
    val id = HashGenerator.hashEstadoTarjeta(tarjetaId, corteInstant)
    return EstadoTarjetaSnap(
        id = id,
        uidUsuario = uidUsuario,
        tarjetaId = tarjetaId,
        fechaCorte = corteInstant,
        fechaLimitePago = fechaLimitePago?.toInstantRD() ?: corteInstant.plusSeconds(25 * 86_400L),
        periodoInicio = periodoInicio,
        periodoFin = periodoFin,
        balanceAlCorte = balanceFinal ?: BigDecimal.ZERO,
        balanceAnterior = balanceInicial,
        pagoMinimo = pagoMinimo ?: BigDecimal.ZERO,
        pagoTotal = pagoTotal ?: BigDecimal.ZERO,
        montoVencido = montoVencido ?: BigDecimal.ZERO,
        balancePromedioDiario = balancePromedioDiario,
        interesFinanciamiento = interesPorFinanciamiento,
        cashbackGanado = cashbackGanado,
        moneda = moneda,
        cargaId = cargaId,
        creadoEn = Instant.now(),
    )
}
