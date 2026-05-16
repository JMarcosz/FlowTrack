package com.example.flowtrack.data.firestore.mappers

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.crypto.HashGenerator
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.parsers.core.EstadoCuentaNormalizado
import com.example.flowtrack.data.parsers.core.MovimientoNormalizado
import com.example.flowtrack.data.parsers.core.TipoMovimiento
import com.example.flowtrack.data.firestore.dto.CuentaDto
import com.example.flowtrack.data.firestore.dto.EstadoTarjetaSnapDto
import com.example.flowtrack.data.firestore.dto.MovimientoTarjetaDto
import com.example.flowtrack.data.firestore.dto.TarjetaDto
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.parsers.core.CuentaDetectada
import com.example.flowtrack.data.parsers.core.EstadoTarjetaDetectado
import com.example.flowtrack.data.parsers.core.MovimientoTarjetaNormalizado
import com.example.flowtrack.data.parsers.core.TarjetaDetectada
import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.OrigenTasa
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.Timestamp
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val ZONA_RD = ZoneId.of("America/Santo_Domingo")

// ─── LocalDate → Instant ──────────────────────────────────────────────────────

fun LocalDate.toInstantRD(): Instant = atStartOfDay(ZONA_RD).toInstant()
fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)
fun LocalDate.toTimestamp(): Timestamp = toInstantRD().toTimestamp()

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
    monto = monto.toDouble(),
    tipo = tipo.name,
    moneda = moneda.name,
    balanceDespues = balanceDespues?.toDouble(),
    referencia = referencia,
    serial = serial,
    categoriaId = categoriaId,
    categoriaAutomatica = categoriaAutomatica,
    esDerivada = esDerivada,
    transaccionPadreId = transaccionPadreId,
    derivadasIds = derivadasIds,
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
    monto = BigDecimal.valueOf(monto).setScale(2),
    tipo = TipoTransaccion.valueOf(tipo),
    moneda = Moneda.valueOf(moneda),
    balanceDespues = balanceDespues?.let { BigDecimal.valueOf(it).setScale(2) },
    referencia = referencia,
    serial = serial,
    categoriaId = categoriaId,
    categoriaAutomatica = categoriaAutomatica,
    esDerivada = esDerivada,
    transaccionPadreId = transaccionPadreId,
    derivadasIds = derivadasIds,
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

fun CuentaDetectada.toDomainConBanco(uidUsuario: String, bancoCodigo: String): Cuenta {
    val id = HashGenerator.hashCuenta(uidUsuario, bancoCodigo, numeroCuenta)
    return Cuenta(
        id = id,
        uidUsuario = uidUsuario,
        bancoCodigo = bancoCodigo,
        numeroCuenta = numeroCuenta,
        numeroCuentaCompleto = numeroCuentaCompleto,
        alias = "$bancoCodigo $numeroCuenta",
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
    balanceActual = balanceActual?.toDouble(),
    balanceAlCorte = balanceAlCorte?.toDouble(),
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
    balanceActual = balanceActual?.let { BigDecimal.valueOf(it).setScale(2) },
    balanceAlCorte = balanceAlCorte?.let { BigDecimal.valueOf(it).setScale(2) },
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
    limiteCredito = limiteCredito.toDouble(),
    moneda = moneda.name,
    diaCorte = diaCorte,
    diaPago = diaPago,
    tasaInteresAnual = tasaInteresAnual,
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
    limiteCredito = BigDecimal.valueOf(limiteCredito).setScale(2),
    moneda = Moneda.valueOf(moneda),
    diaCorte = diaCorte,
    diaPago = diaPago,
    tasaInteresAnual = tasaInteresAnual,
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
    balanceAlCorte = balanceAlCorte.toDouble(),
    balanceAnterior = balanceAnterior?.toDouble(),
    pagoMinimo = pagoMinimo.toDouble(),
    pagoTotal = pagoTotal.toDouble(),
    montoVencido = montoVencido.toDouble(),
    balancePromedioDiario = balancePromedioDiario?.toDouble(),
    interesFinanciamiento = interesFinanciamiento?.toDouble(),
    cashbackGanado = cashbackGanado?.toDouble(),
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
    balanceAlCorte = BigDecimal.valueOf(balanceAlCorte).setScale(2),
    balanceAnterior = balanceAnterior?.let { BigDecimal.valueOf(it).setScale(2) },
    pagoMinimo = BigDecimal.valueOf(pagoMinimo).setScale(2),
    pagoTotal = BigDecimal.valueOf(pagoTotal).setScale(2),
    montoVencido = BigDecimal.valueOf(montoVencido).setScale(2),
    balancePromedioDiario = balancePromedioDiario?.let { BigDecimal.valueOf(it).setScale(2) },
    interesFinanciamiento = interesFinanciamiento?.let { BigDecimal.valueOf(it).setScale(2) },
    cashbackGanado = cashbackGanado?.let { BigDecimal.valueOf(it).setScale(2) },
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
    monto = monto.toDouble(),
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
    monto = BigDecimal.valueOf(monto).setScale(2),
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
    val fechaCorteInstant = fechaFin?.atStartOfDay(java.time.ZoneId.of("America/Santo_Domingo"))?.toInstant()
    return Cuenta(
        id = id,
        uidUsuario = uidUsuario,
        bancoCodigo = bancoCodigo,
        numeroCuenta = numeroCuenta,
        numeroCuentaCompleto = numeroCuentaCompleto,
        alias = "$bancoCodigo $numeroCuenta",
        tipoCuenta = TipoCuenta.CORRIENTE,
        moneda = moneda,
        balanceActual = balanceFinal,
        balanceAlCorte = balanceFinal,
        fechaUltimoCorte = fechaCorteInstant,
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
    val corteInstant = (fechaCorte ?: fechaFin ?: java.time.LocalDate.now()).toInstantRD()
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
