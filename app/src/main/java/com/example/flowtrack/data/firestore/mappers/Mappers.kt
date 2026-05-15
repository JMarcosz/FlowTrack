package com.example.flowtrack.data.firestore.mappers

import com.example.flowtrack.core.extensions.normalizarDescripcion
import com.example.flowtrack.core.crypto.HashGenerator
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.firestore.dto.CuentaDto
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.parsers.core.CuentaDetectada
import com.example.flowtrack.data.parsers.core.ResultadoParseo
import com.example.flowtrack.data.parsers.core.TransaccionNormalizada
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoDocumento
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
    bancoDetectadoAutomaticamente = bancoDetectadoAutomaticamente,
    confianzaDeteccion = confianzaDeteccion,
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
    bancoDetectadoAutomaticamente = bancoDetectadoAutomaticamente,
    confianzaDeteccion = confianzaDeteccion,
    parserVersion = parserVersion,
    tipoDocumento = TipoDocumento.valueOf(tipoDocumento),
    cuentaId = cuentaId,
    tarjetaId = tarjetaId,
    periodoInicio = periodoInicio?.toDate()?.toInstant(),
    periodoFin = periodoFin?.toDate()?.toInstant(),
    transaccionesInsertadas = transaccionesInsertadas,
    transaccionesDuplicadas = transaccionesDuplicadas,
    advertencias = advertencias,
    estado = EstadoCarga.valueOf(estado),
    procesadoEn = procesadoEn?.toDate()?.toInstant() ?: Instant.now(),
)
