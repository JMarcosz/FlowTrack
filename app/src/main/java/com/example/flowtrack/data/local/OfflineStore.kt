package com.example.flowtrack.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.ConfiguracionUsuario
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.NotificacionConfig
import com.example.flowtrack.domain.model.Presupuesto
import com.example.flowtrack.domain.model.ReglaCategoria
import com.example.flowtrack.domain.model.ReglaSugerida
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.TasaCambio
import com.example.flowtrack.domain.model.Transaccion
import com.example.flowtrack.presentation.components.CategoriaUI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val DB_NAME = "flowtrack_offline.db"
private const val DB_VERSION = 2

private const val TABLE_RECORDS = "records"

internal const val ENTITY_TRANSACCION = "TRANSACCION"
internal const val ENTITY_CUENTA = "CUENTA"
internal const val ENTITY_TARJETA = "TARJETA"
internal const val ENTITY_ESTADO_TARJETA = "ESTADO_TARJETA"
internal const val ENTITY_MOVIMIENTO_TARJETA = "MOVIMIENTO_TARJETA"
internal const val ENTITY_CARGA = "CARGA"
internal const val ENTITY_CONFIGURACION = "CONFIGURACION"
internal const val ENTITY_NOTIFICACION = "NOTIFICACION_CONFIG"
internal const val ENTITY_META = "META"
internal const val ENTITY_PRESUPUESTO = "PRESUPUESTO"
internal const val ENTITY_REGLA_CATEGORIA = "REGLA_CATEGORIA"
internal const val ENTITY_REGLA_SUGERIDA = "REGLA_SUGERIDA"
internal const val ENTITY_CATEGORIA_PERSONAL = "CATEGORIA_PERSONAL"
internal const val ENTITY_TASA_CAMBIO = "TASA_CAMBIO"

data class TransaccionesCursor(
    val fechaMillis: Long,
    val entityId: String,
)

data class LocalRecord(
    val entityType: String,
    val uidUsuario: String,
    val entityId: String,
    val fechaMillis: Long? = null,
    val fechaPosteoMillis: Long? = null,
    val fechaCorteMillis: Long? = null,
    val cuentaId: String? = null,
    val tarjetaId: String? = null,
    val bancoCodigo: String? = null,
    val cargaId: String? = null,
    val categoriaId: String? = null,
    val transaccionPadreId: String? = null,
    val tipo: String? = null,
    val estado: String? = null,
    val activo: Boolean = true,
    val mostrarEnDashboard: Boolean = true,
    val deletedAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val payload: String,
) {
    fun toContentValues(): ContentValues = contentValuesOf(
        "entity_type" to entityType,
        "uid_usuario" to uidUsuario,
        "entity_id" to entityId,
        "fecha_millis" to fechaMillis,
        "fecha_posteo_millis" to fechaPosteoMillis,
        "fecha_corte_millis" to fechaCorteMillis,
        "cuenta_id" to cuentaId,
        "tarjeta_id" to tarjetaId,
        "banco_codigo" to bancoCodigo,
        "carga_id" to cargaId,
        "categoria_id" to categoriaId,
        "transaccion_padre_id" to transaccionPadreId,
        "tipo" to tipo,
        "estado" to estado,
        "activo" to if (activo) 1 else 0,
        "mostrar_en_dashboard" to if (mostrarEnDashboard) 1 else 0,
        "deleted_at_millis" to deletedAtMillis,
        "updated_at_millis" to updatedAtMillis,
        "payload" to payload,
    )
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val helper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_RECORDS (
                    entity_type TEXT NOT NULL,
                    uid_usuario TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    fecha_millis INTEGER,
                    fecha_posteo_millis INTEGER,
                    fecha_corte_millis INTEGER,
                    cuenta_id TEXT,
                    tarjeta_id TEXT,
                    banco_codigo TEXT,
                    carga_id TEXT,
                    categoria_id TEXT,
                    transaccion_padre_id TEXT,
                    tipo TEXT,
                    estado TEXT,
                    activo INTEGER NOT NULL DEFAULT 1,
                    mostrar_en_dashboard INTEGER NOT NULL DEFAULT 1,
                    deleted_at_millis INTEGER,
                    updated_at_millis INTEGER NOT NULL,
                    payload TEXT NOT NULL,
                    PRIMARY KEY (entity_type, uid_usuario, entity_id)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_uid_type_fecha ON $TABLE_RECORDS(uid_usuario, entity_type, deleted_at_millis, fecha_millis DESC, entity_id DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_uid_type_cuenta ON $TABLE_RECORDS(uid_usuario, entity_type, cuenta_id, fecha_millis DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_uid_type_tarjeta ON $TABLE_RECORDS(uid_usuario, entity_type, tarjeta_id, fecha_millis DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_uid_type_carga ON $TABLE_RECORDS(uid_usuario, entity_type, carga_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_uid_type_categoria ON $TABLE_RECORDS(uid_usuario, entity_type, categoria_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_uid_type_padre ON $TABLE_RECORDS(uid_usuario, entity_type, transaccion_padre_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_uid_type_estado ON $TABLE_RECORDS(uid_usuario, entity_type, estado)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_RECORDS")
            onCreate(db)
        }
    }

    private val revision = MutableStateFlow(0L)
    private val revisionCounter = AtomicLong(0L)

    private fun signalChange() {
        revision.value = revisionCounter.incrementAndGet()
    }

    private suspend fun <T> io(block: SQLiteDatabase.() -> T): T = withContext(Dispatchers.IO) {
        helper.writableDatabase.block()
    }

    private suspend fun <T> query(block: SQLiteDatabase.() -> T): T = withContext(Dispatchers.IO) {
        helper.readableDatabase.block()
    }

    private fun Cursor.record(): LocalRecord = LocalRecord(
        entityType = getString(getColumnIndexOrThrow("entity_type")),
        uidUsuario = getString(getColumnIndexOrThrow("uid_usuario")),
        entityId = getString(getColumnIndexOrThrow("entity_id")),
        fechaMillis = getLongOrNull("fecha_millis"),
        fechaPosteoMillis = getLongOrNull("fecha_posteo_millis"),
        fechaCorteMillis = getLongOrNull("fecha_corte_millis"),
        cuentaId = getStringOrNull("cuenta_id"),
        tarjetaId = getStringOrNull("tarjeta_id"),
        bancoCodigo = getStringOrNull("banco_codigo"),
        cargaId = getStringOrNull("carga_id"),
        categoriaId = getStringOrNull("categoria_id"),
        transaccionPadreId = getStringOrNull("transaccion_padre_id"),
        tipo = getStringOrNull("tipo"),
        estado = getStringOrNull("estado"),
        activo = getInt(getColumnIndexOrThrow("activo")) != 0,
        mostrarEnDashboard = getInt(getColumnIndexOrThrow("mostrar_en_dashboard")) != 0,
        deletedAtMillis = getLongOrNull("deleted_at_millis"),
        updatedAtMillis = getLong(getColumnIndexOrThrow("updated_at_millis")),
        payload = getString(getColumnIndexOrThrow("payload")),
    )

    private fun Cursor.getStringOrNull(column: String): String? =
        if (isNull(getColumnIndexOrThrow(column))) null else getString(getColumnIndexOrThrow(column))

    private fun Cursor.getLongOrNull(column: String): Long? =
        if (isNull(getColumnIndexOrThrow(column))) null else getLong(getColumnIndexOrThrow(column))

    suspend fun getTransacciones(
        uid: String,
        inicio: Instant? = null,
        fin: Instant? = null,
        limite: Int = 0,
    ): List<Transaccion> = query {
        val args = mutableListOf<String>()
        val sql = buildString {
            append("SELECT payload FROM $TABLE_RECORDS WHERE entity_type=? AND uid_usuario=? AND deleted_at_millis IS NULL")
            args += ENTITY_TRANSACCION
            args += uid
            if (inicio != null) {
                append(" AND fecha_millis >= ?")
                args += inicio.toEpochMilli().toString()
            }
            if (fin != null) {
                append(" AND fecha_millis <= ?")
                args += fin.toEpochMilli().toString()
            }
            append(" ORDER BY fecha_millis DESC, entity_id DESC")
            if (limite > 0) append(" LIMIT $limite")
        }
        rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0).toTransaccion())
            }
        }
    }

    suspend fun getDerivadas(uid: String, padreId: String): List<Transaccion> = query {
        queryPayloads(
            ENTITY_TRANSACCION,
            uid,
            "transaccion_padre_id=? AND deleted_at_millis IS NULL",
            arrayOf(padreId),
            "fecha_millis ASC, entity_id ASC",
        ).map { it.toTransaccion() }
    }

    suspend fun getTransaccionesPage(
        uid: String,
        inicio: Instant? = null,
        fin: Instant? = null,
        cursor: TransaccionesCursor? = null,
        pageSize: Int = 30,
    ): Pair<List<Transaccion>, TransaccionesCursor?> = query {
        val args = mutableListOf<String>()
        val sql = buildString {
            append("SELECT entity_id, fecha_millis, payload FROM $TABLE_RECORDS WHERE entity_type=? AND uid_usuario=? AND deleted_at_millis IS NULL")
            args += ENTITY_TRANSACCION
            args += uid
            if (inicio != null) {
                append(" AND fecha_millis >= ?")
                args += inicio.toEpochMilli().toString()
            }
            if (fin != null) {
                append(" AND fecha_millis <= ?")
                args += fin.toEpochMilli().toString()
            }
            if (cursor != null) {
                append(" AND (fecha_millis < ? OR (fecha_millis = ? AND entity_id < ?))")
                args += cursor.fechaMillis.toString()
                args += cursor.fechaMillis.toString()
                args += cursor.entityId
            }
            append(" ORDER BY fecha_millis DESC, entity_id DESC LIMIT ${pageSize + 1}")
        }
        rawQuery(sql, args.toTypedArray()).use { c ->
            val rows = mutableListOf<Transaccion>()
            var nextCursor: TransaccionesCursor? = null
            while (c.moveToNext()) {
                val entityId = c.getString(0)
                val fechaMillis = c.getLong(1)
                val payload = c.getString(2)
                rows += payload.toTransaccion()
                nextCursor = TransaccionesCursor(fechaMillis = fechaMillis, entityId = entityId)
            }
            val hasMore = rows.size > pageSize
            val page = if (hasMore) rows.dropLast(1) else rows
            Pair(page, if (hasMore) nextCursor else null)
        }
    }

    suspend fun getCuentas(uid: String): List<Cuenta> = query {
        queryPayloads(ENTITY_CUENTA, uid, "activo=1", emptyArray(), "updated_at_millis DESC, entity_id ASC")
            .map { it.toCuenta() }
    }

    suspend fun getTarjetas(uid: String): List<Tarjeta> = query {
        queryPayloads(ENTITY_TARJETA, uid, "activo=1", emptyArray(), "updated_at_millis DESC, entity_id ASC")
            .map { it.toTarjeta() }
    }

    suspend fun getEstadosTarjeta(uid: String, tarjetaId: String): List<EstadoTarjetaSnap> = query {
        queryPayloads(
            ENTITY_ESTADO_TARJETA,
            uid,
            "tarjeta_id=? AND deleted_at_millis IS NULL",
            arrayOf(tarjetaId),
            "fecha_corte_millis DESC, entity_id DESC",
        ).map { it.toEstadoTarjetaSnap() }
    }

    suspend fun getMovimientosTarjeta(uid: String, inicio: Instant? = null, fin: Instant? = null, limite: Int = 0): List<MovimientoTarjeta> = query {
        val args = mutableListOf<String>()
        val sql = buildString {
            append("SELECT payload FROM $TABLE_RECORDS WHERE entity_type=? AND uid_usuario=? AND deleted_at_millis IS NULL")
            args += ENTITY_MOVIMIENTO_TARJETA
            args += uid
            if (inicio != null) {
                append(" AND fecha_millis >= ?")
                args += inicio.toEpochMilli().toString()
            }
            if (fin != null) {
                append(" AND fecha_millis <= ?")
                args += fin.toEpochMilli().toString()
            }
            append(" ORDER BY fecha_millis DESC, entity_id DESC")
            if (limite > 0) append(" LIMIT $limite")
        }
        rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0).toMovimientoTarjeta())
            }
        }
    }

    suspend fun getCargas(uid: String, limite: Int = 50): List<Carga> = query {
        queryPayloads(ENTITY_CARGA, uid, "deleted_at_millis IS NULL", emptyArray(), "updated_at_millis DESC, entity_id DESC", limite)
            .map { it.toCarga() }
    }

    suspend fun getConfiguracion(uid: String): ConfiguracionUsuario? = query {
        queryPayloads(ENTITY_CONFIGURACION, uid, null, emptyArray(), null, 1).firstOrNull()?.toConfiguracionUsuario()
    }

    suspend fun getNotificacionConfig(uid: String): NotificacionConfig? = query {
        queryPayloads(ENTITY_NOTIFICACION, uid, null, emptyArray(), null, 1).firstOrNull()?.toNotificacionConfig()
    }

    suspend fun getMetas(uid: String): List<Meta> = query {
        queryPayloads(ENTITY_META, uid, "deleted_at_millis IS NULL", emptyArray(), "updated_at_millis DESC, entity_id DESC")
            .map { it.toMeta() }
    }

    suspend fun getPresupuestos(uid: String): List<Presupuesto> = query {
        queryPayloads(ENTITY_PRESUPUESTO, uid, "deleted_at_millis IS NULL", emptyArray(), "updated_at_millis DESC, entity_id DESC")
            .map { it.toPresupuesto() }
    }

    suspend fun getReglasCategoria(uid: String): List<ReglaCategoria> = query {
        queryPayloads(ENTITY_REGLA_CATEGORIA, uid, "deleted_at_millis IS NULL", emptyArray(), "prioridad DESC, entity_id ASC")
            .map { it.toReglaCategoria() }
    }

    suspend fun getReglasSugeridas(uid: String): List<ReglaSugerida> = query {
        queryPayloads(ENTITY_REGLA_SUGERIDA, uid, "deleted_at_millis IS NULL", emptyArray(), "updated_at_millis DESC, entity_id DESC")
            .map { it.toReglaSugerida() }
    }

    suspend fun getCategoriasPersonales(uid: String): List<CategoriaUI> = query {
        queryPayloads(ENTITY_CATEGORIA_PERSONAL, uid, null, emptyArray(), "entity_id ASC")
            .map { it.toCategoriaUI() }
    }

    suspend fun getTasasCambio(diasAtras: Int = 30): List<TasaCambio> = query {
        val cutoff = LocalDate.now(ZoneId.of("UTC")).minusDays(diasAtras.toLong())
        rawQuery(
            "SELECT payload FROM $TABLE_RECORDS WHERE entity_type=? AND fecha_millis >= ? ORDER BY fecha_millis DESC, entity_id DESC",
            arrayOf(ENTITY_TASA_CAMBIO, cutoff.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli().toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0).toTasaCambio())
            }
        }
    }

    suspend fun hasRecords(entityType: String, uid: String): Boolean = query {
        rawQuery(
            "SELECT 1 FROM $TABLE_RECORDS WHERE entity_type=? AND uid_usuario=? AND deleted_at_millis IS NULL LIMIT 1",
            arrayOf(entityType, uid)
        ).use { cursor -> cursor.moveToFirst() }
    }

    suspend fun upsertTransaccion(tx: Transaccion) = upsertRecord(tx.toRecordValues())
    suspend fun upsertTransacciones(items: List<Transaccion>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertCuenta(cuenta: Cuenta) = upsertRecord(cuenta.toRecordValues())
    suspend fun upsertCuentas(items: List<Cuenta>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertTarjeta(tarjeta: Tarjeta) = upsertRecord(tarjeta.toRecordValues())
    suspend fun upsertTarjetas(items: List<Tarjeta>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertEstadoTarjeta(item: EstadoTarjetaSnap) = upsertRecord(item.toRecordValues())
    suspend fun upsertEstadosTarjeta(items: List<EstadoTarjetaSnap>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertMovimientoTarjeta(item: MovimientoTarjeta) = upsertRecord(item.toRecordValues())
    suspend fun upsertMovimientosTarjeta(items: List<MovimientoTarjeta>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertCarga(item: Carga) = upsertRecord(item.toRecordValues())
    suspend fun upsertCargas(items: List<Carga>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertConfiguracion(item: ConfiguracionUsuario) = upsertRecord(item.toRecordValues())
    suspend fun upsertNotificacionConfig(item: NotificacionConfig) = upsertRecord(item.toRecordValues())
    suspend fun upsertMeta(item: Meta) = upsertRecord(item.toRecordValues())
    suspend fun upsertMetas(items: List<Meta>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertPresupuesto(item: Presupuesto) = upsertRecord(item.toRecordValues())
    suspend fun upsertPresupuestos(items: List<Presupuesto>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertReglaCategoria(item: ReglaCategoria) = upsertRecord(item.toRecordValues())
    suspend fun upsertReglasCategoria(items: List<ReglaCategoria>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertReglaSugerida(item: ReglaSugerida) = upsertRecord(item.toRecordValues())
    suspend fun upsertReglasSugeridas(items: List<ReglaSugerida>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertCategoriaPersonal(uid: String, item: CategoriaUI) = upsertRecord(item.toRecordValues(uid))
    suspend fun upsertCategoriasPersonales(uid: String, items: List<CategoriaUI>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(uid), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }
    suspend fun upsertTasaCambio(item: TasaCambio) = upsertRecord(item.toRecordValues())
    suspend fun upsertTasasCambio(items: List<TasaCambio>) {
        io {
            beginTransaction()
            try {
                items.forEach { insertWithOnConflict(TABLE_RECORDS, null, it.toRecordValues(), SQLiteDatabase.CONFLICT_REPLACE) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        if (items.isNotEmpty()) signalChange()
    }

    suspend fun markTransaccionDeleted(uid: String, txId: String) {
        softDelete(ENTITY_TRANSACCION, uid, txId)
    }

    suspend fun deactivateCuenta(uid: String, cuentaId: String) {
        io {
            update(
                TABLE_RECORDS,
                contentValuesOf(
                    "activo" to 0,
                    "mostrar_en_dashboard" to 0,
                    "updated_at_millis" to System.currentTimeMillis(),
                ),
                "entity_type=? AND uid_usuario=? AND entity_id=?",
                arrayOf(ENTITY_CUENTA, uid, cuentaId)
            )
        }
        signalChange()
    }

    suspend fun deactivateTarjeta(uid: String, tarjetaId: String) {
        io {
            update(
                TABLE_RECORDS,
                contentValuesOf(
                    "activo" to 0,
                    "updated_at_millis" to System.currentTimeMillis(),
                ),
                "entity_type=? AND uid_usuario=? AND entity_id=?",
                arrayOf(ENTITY_TARJETA, uid, tarjetaId)
            )
        }
        signalChange()
    }

    suspend fun deleteById(entityType: String, uid: String, entityId: String) {
        io {
            delete(TABLE_RECORDS, "entity_type=? AND uid_usuario=? AND entity_id=?", arrayOf(entityType, uid, entityId))
        }
        signalChange()
    }

    suspend fun deleteByCargaId(entityType: String, uid: String, cargaId: String) {
        io {
            delete(TABLE_RECORDS, "entity_type=? AND uid_usuario=? AND carga_id=?", arrayOf(entityType, uid, cargaId))
        }
        signalChange()
    }

    suspend fun clearUser(uid: String) {
        io {
            delete(TABLE_RECORDS, "uid_usuario=?", arrayOf(uid))
        }
        signalChange()
    }

    private suspend fun upsertRecord(values: ContentValues) {
        io {
        insertWithOnConflict(TABLE_RECORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        signalChange()
    }

    private suspend fun softDelete(entityType: String, uid: String, entityId: String) {
        io {
            update(
                TABLE_RECORDS,
                contentValuesOf(
                    "deleted_at_millis" to System.currentTimeMillis(),
                    "updated_at_millis" to System.currentTimeMillis(),
                ),
                "entity_type=? AND uid_usuario=? AND entity_id=?",
                arrayOf(entityType, uid, entityId)
            )
        }
        signalChange()
    }

    private fun SQLiteDatabase.queryPayloads(
        entityType: String,
        uid: String,
        extraWhere: String?,
        extraArgs: Array<String>,
        orderBy: String?,
        limit: Int? = null,
    ): List<String> {
        val args = mutableListOf<String>()
        val sql = buildString {
            append("SELECT payload FROM $TABLE_RECORDS WHERE entity_type=? AND uid_usuario=?")
            args += entityType
            args += uid
            if (extraWhere != null) {
                append(" AND ")
                append(extraWhere)
                args += extraArgs
            }
            if (orderBy != null) {
                append(" ORDER BY ")
                append(orderBy)
            }
            if (limit != null && limit > 0) {
                append(" LIMIT $limit")
            }
        }
        return rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    fun observeTransacciones(
        uid: String,
        inicio: Instant? = null,
        fin: Instant? = null,
        limite: Int = 100,
    ): Flow<List<Transaccion>> = revision.mapLatest {
        getTransacciones(uid, inicio, fin, limite)
    }

    fun observeCuentas(uid: String): Flow<List<Cuenta>> = revision.mapLatest {
        getCuentas(uid)
    }

    fun observeTarjetas(uid: String): Flow<List<Tarjeta>> = revision.mapLatest {
        getTarjetas(uid)
    }

    fun observeMovimientosTarjeta(
        uid: String,
        inicio: Instant? = null,
        fin: Instant? = null,
        limite: Int = 200,
    ): Flow<List<MovimientoTarjeta>> = revision.mapLatest {
        getMovimientosTarjeta(uid, inicio, fin, limite)
    }

    fun observeConfiguracion(uid: String): Flow<ConfiguracionUsuario> = revision.mapLatest {
        getConfiguracion(uid) ?: ConfiguracionUsuario(uidUsuario = uid)
    }

    fun observeNotificacionConfig(uid: String): Flow<NotificacionConfig> = revision.mapLatest {
        getNotificacionConfig(uid) ?: NotificacionConfig(uidUsuario = uid)
    }

    fun observeMetas(uid: String): Flow<List<Meta>> = revision.mapLatest { getMetas(uid) }
    fun observePresupuestos(uid: String): Flow<List<Presupuesto>> = revision.mapLatest { getPresupuestos(uid) }
    fun observeReglasCategoria(uid: String): Flow<List<ReglaCategoria>> = revision.mapLatest { getReglasCategoria(uid) }
    fun observeReglasSugeridas(uid: String): Flow<List<ReglaSugerida>> = revision.mapLatest { getReglasSugeridas(uid) }
    fun observeCategoriasPersonales(uid: String): Flow<List<CategoriaUI>> = revision.mapLatest { getCategoriasPersonales(uid) }
    fun observeCargas(uid: String, limite: Int = 20): Flow<List<Carga>> = revision.mapLatest { getCargas(uid, limite) }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

private fun Any?.asJsonString(): String? = when (this) {
    null -> null
    is String -> this
    else -> toString()
}

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
    put(name, value?.toString())
}

private fun JSONObject.optLocalTime(name: String): LocalTime? =
    if (!has(name) || isNull(name)) null else runCatching { LocalTime.parse(optString(name)) }.getOrNull()

private fun JSONObject.putStringList(name: String, value: List<String>) {
    put(name, JSONArray(value))
}

private fun JSONObject.optStringList(name: String): List<String> =
    if (!has(name) || isNull(name)) emptyList()
    else optJSONArray(name)?.let { array ->
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

private fun contentValuesOf(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
    pairs.forEach { (key, value) ->
        when (value) {
            null -> putNull(key)
            is String -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Boolean -> put(key, if (value) 1 else 0)
            is Float -> put(key, value)
            is Double -> put(key, value)
            is BigDecimal -> put(key, value.toPlainString())
            else -> put(key, value.toString())
        }
    }
}
