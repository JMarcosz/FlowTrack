package com.example.flowtrack.data.local

import android.content.ContentValues
import androidx.compose.ui.graphics.Color
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.CategoriaMeta
import com.example.flowtrack.domain.model.ConfiguracionUsuario
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.EstadoCarga
import com.example.flowtrack.domain.model.EstadoMeta
import com.example.flowtrack.domain.model.EstadoTarjeta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.model.Moneda
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.NotificacionConfig
import com.example.flowtrack.domain.model.OrigenTasa
import com.example.flowtrack.domain.model.PeriodoPresupuesto
import com.example.flowtrack.domain.model.Presupuesto
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.ReglaSugerida
import com.example.flowtrack.domain.model.TipoCuenta
import com.example.flowtrack.domain.model.TipoMatch
import com.example.flowtrack.domain.model.TipoMovimientoTarjeta
import com.example.flowtrack.domain.model.TipoDocumento
import com.example.flowtrack.domain.model.TipoTransaccion
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.presentation.components.CategoriaUI
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject
import androidx.core.graphics.toColorInt

private val ZONA_RD: ZoneId = ZoneId.of("America/Santo_Domingo")

private fun baseRecordValues(
    entityType: String,
    uid: String,
    entityId: String,
    payload: String,
    fechaMillis: Long? = null,
    fechaPosteoMillis: Long? = null,
    fechaCorteMillis: Long? = null,
    cuentaId: String? = null,
    tarjetaId: String? = null,
    bancoCodigo: String? = null,
    cargaId: String? = null,
    categoriaId: String? = null,
    transaccionPadreId: String? = null,
    tipo: String? = null,
    estado: String? = null,
    activo: Boolean = true,
    mostrarEnDashboard: Boolean = true,
    deletedAtMillis: Long? = null,
): ContentValues = ContentValues().apply {
    put("entity_type", entityType)
    put("uid_usuario", uid)
    put("entity_id", entityId)
    if (fechaMillis != null) put("fecha_millis", fechaMillis) else putNull("fecha_millis")
    if (fechaPosteoMillis != null) put("fecha_posteo_millis", fechaPosteoMillis) else putNull("fecha_posteo_millis")
    if (fechaCorteMillis != null) put("fecha_corte_millis", fechaCorteMillis) else putNull("fecha_corte_millis")
    if (cuentaId != null) put("cuenta_id", cuentaId) else putNull("cuenta_id")
    if (tarjetaId != null) put("tarjeta_id", tarjetaId) else putNull("tarjeta_id")
    if (bancoCodigo != null) put("banco_codigo", bancoCodigo) else putNull("banco_codigo")
    if (cargaId != null) put("carga_id", cargaId) else putNull("carga_id")
    if (categoriaId != null) put("categoria_id", categoriaId) else putNull("categoria_id")
    if (transaccionPadreId != null) put("transaccion_padre_id", transaccionPadreId) else putNull("transaccion_padre_id")
    if (tipo != null) put("tipo", tipo) else putNull("tipo")
    if (estado != null) put("estado", estado) else putNull("estado")
    put("activo", if (activo) 1 else 0)
    put("mostrar_en_dashboard", if (mostrarEnDashboard) 1 else 0)
    if (deletedAtMillis != null) put("deleted_at_millis", deletedAtMillis) else putNull("deleted_at_millis")
    put("updated_at_millis", System.currentTimeMillis())
    put("payload", payload)
}

private fun Instant.toUtcMillis(): Long = toEpochMilli()
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZONA_RD).toInstant().toEpochMilli()
private fun LocalTime.toStoredString(): String = toString().take(5)
private fun String.toLocalTimeSafe(): LocalTime =
    runCatching { LocalTime.parse(if (length == 5) "$this:00" else this) }.getOrDefault(LocalTime.of(8, 0))

private fun JSONObject.putBigDecimal(name: String, value: BigDecimal?) {
    put(name, value?.toPlainString())
}

private fun JSONObject.optBigDecimal(name: String): BigDecimal? =
    if (!has(name) || isNull(name)) null else optString(name, null)?.toBigDecimalOrNull()

private fun JSONObject.putInstant(name: String, value: Instant?) {
    put(name, value?.toString())
}

private fun JSONObject.optInstant(name: String): Instant? =
    if (!has(name) || isNull(name)) null else runCatching { Instant.parse(optString(name)) }.getOrNull()

private fun JSONObject.putLocalDate(name: String, value: LocalDate?) {
    put(name, value?.toString())
}

private fun JSONObject.optLocalDate(name: String): LocalDate? =
    if (!has(name) || isNull(name)) null else runCatching { LocalDate.parse(optString(name)) }.getOrNull()

private fun JSONObject.putLocalTime(name: String, value: LocalTime?) {
    put(name, value?.toStoredString())
}

private fun JSONObject.optLocalTime(name: String): LocalTime? =
    if (!has(name) || isNull(name)) null else optString(name, null)?.toLocalTimeSafe()

private fun JSONObject.putStringList(name: String, value: List<String>) {
    put(name, value)
}

private fun JSONObject.optStringList(name: String): List<String> =
    if (!has(name) || isNull(name)) emptyList() else optJSONArray(name)?.let { array ->
        buildList {
            for (i in 0 until array.length()) add(array.optString(i))
        }
    }.orEmpty()

private fun JSONObject.putStringMap(name: String, value: Map<String, String>) {
    val obj = JSONObject()
    value.forEach { (k, v) -> obj.put(k, v) }
    put(name, obj)
}

private fun JSONObject.optStringMap(name: String): Map<String, String> {
    if (!has(name) || isNull(name)) return emptyMap()
    val obj = optJSONObject(name) ?: return emptyMap()
    return buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, obj.optString(key, ""))
        }
    }
}

private fun JSONObject.putEnum(name: String, value: Enum<*>?) {
    put(name, value?.name)
}

private inline fun <reified T : Enum<T>> JSONObject.optEnum(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(optString(name, fallback.name)) }.getOrDefault(fallback)

private fun colorToHex(color: Color): String = String.format("#%06X", (0xFFFFFF and color.value.toLong().toInt()))

private fun parseColor(hex: String): Color {
    return try {
        val colorStr = if (hex.startsWith("#")) hex.substring(1) else hex
        Color("#FF$colorStr".toColorInt())
    } catch (_: Exception) {
        Color.Gray
    }
}

fun Transaccion.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("cuentaId", cuentaId)
        put("bancoCodigo", bancoCodigo)
        putInstant("fecha", fecha)
        putInstant("fechaPosteo", fechaPosteo)
        put("descripcionCorta", descripcionCorta)
        put("descripcionOriginal", descripcionOriginal)
        put("descripcionNormalizada", descripcionNormalizada)
        putBigDecimal("monto", monto)
        putEnum("tipo", tipo)
        putEnum("moneda", moneda)
        putBigDecimal("balanceDespues", balanceDespues)
        put("referencia", referencia)
        put("serial", serial)
        put("categoriaId", categoriaId)
        put("categoriaAutomatica", categoriaAutomatica)
        put("esDerivada", esDerivada)
        put("transaccionPadreId", transaccionPadreId)
        putStringList("derivadasIds", derivadasIds)
        put("cargaId", cargaId)
        put("notaUsuario", notaUsuario)
        putStringMap("metadataBanco", metadataBanco)
        putInstant("creadoEn", creadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_TRANSACCION,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = fecha.toUtcMillis(),
        fechaPosteoMillis = fechaPosteo?.toUtcMillis(),
        cuentaId = cuentaId,
        bancoCodigo = bancoCodigo,
        cargaId = cargaId,
        categoriaId = categoriaId,
        transaccionPadreId = transaccionPadreId,
        tipo = tipo.name,
        deletedAtMillis = null,
    )
}

fun String.toTransaccion(): Transaccion = JSONObject(this).run {
    Transaccion(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        cuentaId = optString("cuentaId"),
        bancoCodigo = optString("bancoCodigo"),
        fecha = optInstant("fecha") ?: Instant.now(),
        fechaPosteo = optInstant("fechaPosteo"),
        descripcionCorta = optString("descripcionCorta"),
        descripcionOriginal = optString("descripcionOriginal"),
        descripcionNormalizada = optString("descripcionNormalizada"),
        monto = optBigDecimal("monto") ?: BigDecimal.ZERO.setScale(2),
        tipo = optEnum("tipo", TipoTransaccion.DEBITO),
        moneda = optEnum("moneda", Moneda.DOP),
        balanceDespues = optBigDecimal("balanceDespues"),
        referencia = optString("referencia", null),
        serial = optString("serial", null),
        categoriaId = optString("categoriaId", null),
        categoriaAutomatica = optBoolean("categoriaAutomatica", false),
        esDerivada = optBoolean("esDerivada", false),
        transaccionPadreId = optString("transaccionPadreId", null),
        derivadasIds = optStringList("derivadasIds"),
        cargaId = optString("cargaId"),
        notaUsuario = optString("notaUsuario", null),
        metadataBanco = optStringMap("metadataBanco"),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
    )
}

fun Cuenta.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("bancoCodigo", bancoCodigo)
        put("numeroCuenta", numeroCuenta)
        put("numeroCuentaCompleto", numeroCuentaCompleto)
        put("alias", alias)
        putEnum("tipoCuenta", tipoCuenta)
        putEnum("moneda", moneda)
        putBigDecimal("balanceActual", balanceActual)
        putBigDecimal("balanceAlCorte", balanceAlCorte)
        putInstant("fechaUltimoCorte", fechaUltimoCorte)
        put("titular", titular)
        put("activa", activa)
        put("mostrarEnDashboard", mostrarEnDashboard)
        putInstant("ultimaSincronizacion", ultimaSincronizacion)
        putInstant("creadoEn", creadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_CUENTA,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = creadoEn.toUtcMillis(),
        cuentaId = id,
        bancoCodigo = bancoCodigo,
        tipo = tipoCuenta.name,
        activo = activa,
        mostrarEnDashboard = mostrarEnDashboard,
    )
}

fun String.toCuenta(): Cuenta = JSONObject(this).run {
    Cuenta(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        bancoCodigo = optString("bancoCodigo"),
        numeroCuenta = optString("numeroCuenta"),
        numeroCuentaCompleto = optString("numeroCuentaCompleto", null),
        alias = optString("alias"),
        tipoCuenta = optEnum("tipoCuenta", TipoCuenta.CORRIENTE),
        moneda = optEnum("moneda", Moneda.DOP),
        balanceActual = optBigDecimal("balanceActual"),
        balanceAlCorte = optBigDecimal("balanceAlCorte"),
        fechaUltimoCorte = optInstant("fechaUltimoCorte"),
        titular = optString("titular"),
        activa = optBoolean("activa", true),
        mostrarEnDashboard = optBoolean("mostrarEnDashboard", true),
        ultimaSincronizacion = optInstant("ultimaSincronizacion"),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
    )
}

fun Tarjeta.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("bancoCodigo", bancoCodigo)
        put("ultimos4", ultimos4)
        put("alias", alias)
        put("tipoRed", tipoRed)
        putBigDecimal("limiteCredito", limiteCredito)
        putEnum("moneda", moneda)
        put("diaCorte", diaCorte)
        put("diaPago", diaPago)
        put("tasaInteresAnual", tasaInteresAnual)
        putEnum("tasaInteresOrigen", tasaInteresOrigen)
        putEnum("estado", estado)
        put("titular", titular)
        put("activa", activa)
        putInstant("ultimaSincronizacion", ultimaSincronizacion)
        putInstant("creadoEn", creadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_TARJETA,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = creadoEn.toUtcMillis(),
        tarjetaId = id,
        bancoCodigo = bancoCodigo,
        tipo = estado.name,
        estado = estado.name,
        activo = activa,
    )
}

fun String.toTarjeta(): Tarjeta = JSONObject(this).run {
    Tarjeta(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        bancoCodigo = optString("bancoCodigo"),
        ultimos4 = optString("ultimos4"),
        alias = optString("alias"),
        tipoRed = optString("tipoRed", null),
        limiteCredito = optBigDecimal("limiteCredito") ?: BigDecimal.ZERO.setScale(2),
        moneda = optEnum("moneda", Moneda.DOP),
        diaCorte = optInt("diaCorte", 1),
        diaPago = optInt("diaPago", 1),
        tasaInteresAnual = optDouble("tasaInteresAnual", 0.0),
        tasaInteresOrigen = optEnum("tasaInteresOrigen", OrigenTasa.AUTO_EXTRAIDA),
        estado = optEnum("estado", EstadoTarjeta.ACTIVO),
        titular = optString("titular"),
        activa = optBoolean("activa", true),
        ultimaSincronizacion = optInstant("ultimaSincronizacion"),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
    )
}

fun EstadoTarjetaSnap.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("tarjetaId", tarjetaId)
        putInstant("fechaCorte", fechaCorte)
        putInstant("fechaLimitePago", fechaLimitePago)
        putInstant("periodoInicio", periodoInicio)
        putInstant("periodoFin", periodoFin)
        putBigDecimal("balanceAlCorte", balanceAlCorte)
        putBigDecimal("balanceAnterior", balanceAnterior)
        putBigDecimal("pagoMinimo", pagoMinimo)
        putBigDecimal("pagoTotal", pagoTotal)
        putBigDecimal("montoVencido", montoVencido)
        putBigDecimal("balancePromedioDiario", balancePromedioDiario)
        putBigDecimal("interesFinanciamiento", interesFinanciamiento)
        putBigDecimal("cashbackGanado", cashbackGanado)
        putEnum("moneda", moneda)
        put("cargaId", cargaId)
        putInstant("creadoEn", creadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_ESTADO_TARJETA,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = fechaCorte.toUtcMillis(),
        fechaCorteMillis = fechaCorte.toUtcMillis(),
        tarjetaId = tarjetaId,
        cargaId = cargaId,
        bancoCodigo = null,
    )
}

fun String.toEstadoTarjetaSnap(): EstadoTarjetaSnap = JSONObject(this).run {
    EstadoTarjetaSnap(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        tarjetaId = optString("tarjetaId"),
        fechaCorte = optInstant("fechaCorte") ?: Instant.now(),
        fechaLimitePago = optInstant("fechaLimitePago") ?: Instant.now(),
        periodoInicio = optInstant("periodoInicio") ?: Instant.now(),
        periodoFin = optInstant("periodoFin") ?: Instant.now(),
        balanceAlCorte = optBigDecimal("balanceAlCorte") ?: BigDecimal.ZERO.setScale(2),
        balanceAnterior = optBigDecimal("balanceAnterior"),
        pagoMinimo = optBigDecimal("pagoMinimo") ?: BigDecimal.ZERO.setScale(2),
        pagoTotal = optBigDecimal("pagoTotal") ?: BigDecimal.ZERO.setScale(2),
        montoVencido = optBigDecimal("montoVencido") ?: BigDecimal.ZERO.setScale(2),
        balancePromedioDiario = optBigDecimal("balancePromedioDiario"),
        interesFinanciamiento = optBigDecimal("interesFinanciamiento"),
        cashbackGanado = optBigDecimal("cashbackGanado"),
        moneda = optEnum("moneda", Moneda.DOP),
        cargaId = optString("cargaId"),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
    )
}

fun MovimientoTarjeta.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("tarjetaId", tarjetaId)
        put("bancoCodigo", bancoCodigo)
        putInstant("fechaTransaccion", fechaTransaccion)
        putInstant("fechaPosteo", fechaPosteo)
        put("descripcionOriginal", descripcionOriginal)
        put("descripcionNormalizada", descripcionNormalizada)
        putBigDecimal("monto", monto)
        putBigDecimal("montoUsd", montoUsd)
        putEnum("tipoMovimiento", tipoMovimiento)
        putEnum("moneda", moneda)
        put("numeroAutorizacion", numeroAutorizacion)
        put("categoriaId", categoriaId)
        put("categoriaAutomatica", categoriaAutomatica)
        put("cargaId", cargaId)
        putStringMap("metadataBanco", metadataBanco)
        putInstant("creadoEn", creadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_MOVIMIENTO_TARJETA,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = fechaTransaccion.toUtcMillis(),
        fechaPosteoMillis = fechaPosteo?.toUtcMillis(),
        tarjetaId = tarjetaId,
        bancoCodigo = bancoCodigo,
        cargaId = cargaId,
        categoriaId = categoriaId,
        tipo = tipoMovimiento.name,
    )
}

fun String.toMovimientoTarjeta(): MovimientoTarjeta = JSONObject(this).run {
    MovimientoTarjeta(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        tarjetaId = optString("tarjetaId"),
        bancoCodigo = optString("bancoCodigo"),
        fechaTransaccion = optInstant("fechaTransaccion") ?: Instant.now(),
        fechaPosteo = optInstant("fechaPosteo"),
        descripcionOriginal = optString("descripcionOriginal"),
        descripcionNormalizada = optString("descripcionNormalizada"),
        monto = optBigDecimal("monto") ?: BigDecimal.ZERO.setScale(2),
        montoUsd = optBigDecimal("montoUsd"),
        tipoMovimiento = optEnum("tipoMovimiento", TipoMovimientoTarjeta.COMPRA),
        moneda = optEnum("moneda", Moneda.DOP),
        numeroAutorizacion = optString("numeroAutorizacion", null),
        categoriaId = optString("categoriaId", null),
        categoriaAutomatica = optBoolean("categoriaAutomatica", false),
        cargaId = optString("cargaId"),
        metadataBanco = optStringMap("metadataBanco"),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
    )
}

fun Carga.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("nombreArchivo", nombreArchivo)
        put("tamanioBytes", tamanioBytes)
        put("mimeType", mimeType)
        put("bancoCodigo", bancoCodigo)
        put("parserVersion", parserVersion)
        putEnum("tipoDocumento", tipoDocumento)
        put("cuentaId", cuentaId)
        put("tarjetaId", tarjetaId)
        putInstant("periodoInicio", periodoInicio)
        putInstant("periodoFin", periodoFin)
        put("transaccionesInsertadas", transaccionesInsertadas)
        put("transaccionesDuplicadas", transaccionesDuplicadas)
        putStringList("advertencias", advertencias)
        putEnum("estado", estado)
        putInstant("procesadoEn", procesadoEn)
        putInstant("eliminadoEn", eliminadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_CARGA,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = procesadoEn.toUtcMillis(),
        cargaId = id,
        bancoCodigo = bancoCodigo,
        tipo = tipoDocumento.name,
        estado = estado.name,
        deletedAtMillis = eliminadoEn?.toUtcMillis(),
    )
}

fun String.toCarga(): Carga = JSONObject(this).run {
    Carga(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        nombreArchivo = optString("nombreArchivo"),
        tamanioBytes = optLong("tamanioBytes", 0L),
        mimeType = optString("mimeType", null),
        bancoCodigo = optString("bancoCodigo"),
        parserVersion = optInt("parserVersion", 0),
        tipoDocumento = optEnum("tipoDocumento", TipoDocumento.CUENTA_CORRIENTE),
        cuentaId = optString("cuentaId", null),
        tarjetaId = optString("tarjetaId", null),
        periodoInicio = optInstant("periodoInicio"),
        periodoFin = optInstant("periodoFin"),
        transaccionesInsertadas = optInt("transaccionesInsertadas", 0),
        transaccionesDuplicadas = optInt("transaccionesDuplicadas", 0),
        advertencias = optStringList("advertencias"),
        estado = optEnum("estado", EstadoCarga.EXITOSO),
        procesadoEn = optInstant("procesadoEn") ?: Instant.now(),
        eliminadoEn = optInstant("eliminadoEn"),
    )
}

fun ConfiguracionUsuario.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("uidUsuario", uidUsuario)
        put("idioma", idioma)
        put("formatoFecha", formatoFecha)
        put("formatoMoneda", formatoMoneda)
        putEnum("monedaPredeterminada", monedaPredeterminada)
        put("temaOscuro", temaOscuro)
        putInstant("ultimoBackup", ultimoBackup)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_CONFIGURACION,
        uid = uidUsuario,
        entityId = "preferencias",
        payload = payload,
        fechaMillis = ultimoBackup?.toUtcMillis(),
    )
}

fun String.toConfiguracionUsuario(): ConfiguracionUsuario = JSONObject(this).run {
    ConfiguracionUsuario(
        uidUsuario = optString("uidUsuario"),
        idioma = optString("idioma", "es-DO"),
        formatoFecha = optString("formatoFecha", "dd/MM/yyyy"),
        formatoMoneda = optString("formatoMoneda", "RD$ 0.00"),
        monedaPredeterminada = optEnum("monedaPredeterminada", Moneda.DOP),
        temaOscuro = optBoolean("temaOscuro", false),
        ultimoBackup = optInstant("ultimoBackup"),
    )
}

fun NotificacionConfig.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("uidUsuario", uidUsuario)
        put("activa", activa)
        put("pago7dias", pago7dias)
        put("pago3dias", pago3dias)
        put("pago1dia", pago1dia)
        put("pagoMismoDia", pagoMismoDia)
        put("resumenMensual", resumenMensual)
        put("alertasGastosAltos", alertasGastosAltos)
        putBigDecimal("umbralGastoAlto", umbralGastoAlto)
        putLocalTime("horaNotificacion", horaNotificacion)
        put("zonaHoraria", zonaHoraria)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_NOTIFICACION,
        uid = uidUsuario,
        entityId = "notificaciones",
        payload = payload,
    )
}

fun String.toNotificacionConfig(): NotificacionConfig = JSONObject(this).run {
    NotificacionConfig(
        uidUsuario = optString("uidUsuario"),
        activa = optBoolean("activa", true),
        pago7dias = optBoolean("pago7dias", true),
        pago3dias = optBoolean("pago3dias", true),
        pago1dia = optBoolean("pago1dia", true),
        pagoMismoDia = optBoolean("pagoMismoDia", true),
        resumenMensual = optBoolean("resumenMensual", true),
        alertasGastosAltos = optBoolean("alertasGastosAltos", false),
        umbralGastoAlto = optBigDecimal("umbralGastoAlto") ?: BigDecimal("5000"),
        horaNotificacion = optLocalTime("horaNotificacion") ?: LocalTime.of(8, 0),
        zonaHoraria = optString("zonaHoraria", "America/Santo_Domingo"),
    )
}

fun Meta.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("nombre", nombre)
        put("emoji", emoji)
        putBigDecimal("montoObjetivo", montoObjetivo)
        putBigDecimal("montoActual", montoActual)
        putInstant("fechaLimite", fechaLimite)
        put("activa", activa)
        putInstant("creadoEn", creadoEn)
        put("descripcion", descripcion)
        putEnum("categoria", categoria)
        put("cuentaId", cuentaId)
        putInstant("fechaObjetivo", fechaObjetivo)
        putEnum("estado", estado)
        putInstant("actualizadaEn", actualizadaEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_META,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = creadoEn.toUtcMillis(),
        categoriaId = null,
        estado = if (activa) "ACTIVA" else "INACTIVA",
        activo = activa,
    )
}

fun String.toMeta(): Meta = JSONObject(this).run {
    Meta(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        nombre = optString("nombre"),
        emoji = optString("emoji"),
        montoObjetivo = optBigDecimal("montoObjetivo") ?: BigDecimal.ZERO.setScale(2),
        montoActual = optBigDecimal("montoActual") ?: BigDecimal.ZERO.setScale(2),
        fechaLimite = optInstant("fechaLimite"),
        activa = optBoolean("activa", true),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
        descripcion = optString("descripcion").takeIf { it.isNotBlank() },
        categoria = optEnum("categoria", CategoriaMeta.OTRO),
        cuentaId = optString("cuentaId").takeIf { it.isNotBlank() },
        fechaObjetivo = optInstant("fechaObjetivo") ?: optInstant("fechaLimite"),
        estado = optEnum("estado", EstadoMeta.ACTIVA),
        actualizadaEn = optInstant("actualizadaEn") ?: optInstant("creadoEn") ?: Instant.now(),
    )
}

fun Presupuesto.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("categoriaId", categoriaId)
        putBigDecimal("montoLimite", montoLimite)
        putEnum("periodo", periodo)
        put("activo", activo)
        putInstant("creadoEn", creadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_PRESUPUESTO,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = creadoEn.toUtcMillis(),
        categoriaId = categoriaId,
        estado = periodo.name,
        activo = activo,
    )
}

fun String.toPresupuesto(): Presupuesto = JSONObject(this).run {
    Presupuesto(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        categoriaId = optString("categoriaId"),
        montoLimite = optBigDecimal("montoLimite") ?: BigDecimal.ZERO.setScale(2),
        periodo = optEnum("periodo", PeriodoPresupuesto.MENSUAL),
        activo = optBoolean("activo", true),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
    )
}

fun ReglaCategoria.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("patron", patron)
        putEnum("tipoMatch", tipoMatch)
        put("categoriaId", categoriaId)
        put("prioridad", prioridad)
        put("confianza", confianza)
        put("activa", activa)
        put("creadoPor", creadoPor)
        putInstant("creadoEn", creadoEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_REGLA_CATEGORIA,
        uid = uidUsuario ?: "global",
        entityId = id,
        payload = payload,
        fechaMillis = creadoEn.toUtcMillis(),
        categoriaId = categoriaId,
        estado = tipoMatch.name,
        activo = activa,
    )
}

fun String.toReglaCategoria(): ReglaCategoria = JSONObject(this).run {
    ReglaCategoria(
        id = optString("id"),
        uidUsuario = optString("uidUsuario", null),
        patron = optString("patron"),
        tipoMatch = optEnum("tipoMatch", TipoMatch.CONTIENE),
        categoriaId = optString("categoriaId"),
        prioridad = optInt("prioridad", 10),
        confianza = optInt("confianza", 1),
        activa = optBoolean("activa", true),
        creadoPor = optString("creadoPor"),
        creadoEn = optInstant("creadoEn") ?: Instant.now(),
    )
}

fun ReglaSugerida.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("uidUsuario", uidUsuario)
        put("patronDetectado", patronDetectado)
        put("categoriaSugerida", categoriaSugerida)
        putStringList("muestras", muestras)
        put("confianzaCluster", confianzaCluster)
        putInstant("creadaEn", creadaEn)
        put("aceptada", aceptada)
        putInstant("resueltaEn", resueltaEn)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_REGLA_SUGERIDA,
        uid = uidUsuario,
        entityId = id,
        payload = payload,
        fechaMillis = creadaEn.toUtcMillis(),
        categoriaId = categoriaSugerida,
        estado = if (aceptada == true) "ACEPTADA" else if (aceptada == false) "RECHAZADA" else "PENDIENTE",
        activo = aceptada != false,
    )
}

fun String.toReglaSugerida(): ReglaSugerida = JSONObject(this).run {
    ReglaSugerida(
        id = optString("id"),
        uidUsuario = optString("uidUsuario"),
        patronDetectado = optString("patronDetectado"),
        categoriaSugerida = optString("categoriaSugerida"),
        muestras = optStringList("muestras"),
        confianzaCluster = optDouble("confianzaCluster", 0.0).toFloat(),
        creadaEn = optInstant("creadaEn") ?: Instant.now(),
        aceptada = if (has("aceptada") && !isNull("aceptada")) optBoolean("aceptada") else null,
        resueltaEn = optInstant("resueltaEn"),
    )
}

fun CategoriaUI.toRecordValues(uid: String): ContentValues {
    val payload = JSONObject().apply {
        put("id", id)
        put("nombre", nombre)
        put("colorHex", colorToHex(color))
        put("uidUsuario", uid)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_CATEGORIA_PERSONAL,
        uid = uid,
        entityId = id,
        payload = payload,
        fechaMillis = System.currentTimeMillis(),
    )
}

fun String.toCategoriaUI(): CategoriaUI = JSONObject(this).run {
    CategoriaUI(
        id = optString("id"),
        nombre = optString("nombre"),
        color = parseColor(optString("colorHex", "#808080")),
    )
}

fun TasaCambio.toRecordValues(): ContentValues {
    val payload = JSONObject().apply {
        putBigDecimal("compra", compra)
        putBigDecimal("venta", venta)
        putLocalDate("fecha", fecha)
        put("fuente", fuente)
    }.toString()

    return baseRecordValues(
        entityType = ENTITY_TASA_CAMBIO,
        uid = "global",
        entityId = fecha.toString(),
        payload = payload,
        fechaMillis = fecha.toUtcMillis(),
        estado = fuente,
    )
}

fun String.toTasaCambio(): TasaCambio = JSONObject(this).run {
    TasaCambio(
        compra = optBigDecimal("compra") ?: BigDecimal.ZERO.setScale(2),
        venta = optBigDecimal("venta") ?: BigDecimal.ZERO.setScale(2),
        fecha = optLocalDate("fecha") ?: LocalDate.now(),
        fuente = optString("fuente", "Firebase"),
    )
}
