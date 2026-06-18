package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.MetaDto
import com.example.flowtrack.data.firestore.dto.MovimientoMetaDto
import com.example.flowtrack.data.firestore.mappers.money
import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.domain.model.CategoriaMeta
import com.example.flowtrack.domain.model.EstadoMeta
import com.example.flowtrack.domain.model.Meta
import com.example.flowtrack.domain.model.MovimientoMeta
import com.example.flowtrack.domain.model.TipoMovimientoMeta
import com.example.flowtrack.domain.repository.IMetaRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val offlineStore: OfflineStore,
) : IMetaRepository {

    private fun usuarioRef(uid: String) = firestore.collection("usuarios").document(uid)

    private fun metasRef(uid: String) = usuarioRef(uid).collection("metas")

    override fun observarMetas(uid: String): Flow<List<Meta>> =
        offlineStore.observeMetas(uid)
            .onStart {
                if (!offlineStore.hasRecords("META", uid)) {
                    syncRemote(uid)
                }
            }

    override suspend fun obtenerMetas(uid: String): AppResult<List<Meta>> = try {
        val local = offlineStore.getMetas(uid)
        if (local.isNotEmpty()) {
            AppResult.Success(local)
        } else {
            syncRemote(uid)
            AppResult.Success(offlineStore.getMetas(uid))
        }
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error cargando metas: ${e.message}", e))
    }

    override suspend fun obtenerMeta(uid: String, metaId: String): AppResult<Meta?> = try {
        val local = offlineStore.getMetas(uid).firstOrNull { it.id == metaId }
        if (local != null) {
            AppResult.Success(local)
        } else {
            val remote = metasRef(uid).document(metaId).get().await().toMetaCompat(uid)
            if (remote != null) offlineStore.upsertMeta(remote)
            AppResult.Success(remote)
        }
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error cargando meta: ${e.message}", e))
    }

    override suspend fun guardarMeta(meta: Meta): AppResult<Unit> = try {
        val normalizada = meta.normalizarEstado()
        metasRef(normalizada.uidUsuario).document(normalizada.id)
            .set(normalizada.toMetaDto(), SetOptions.merge())
            .await()
        offlineStore.upsertMeta(normalizada)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error guardando meta: ${e.message}", e))
    }

    override suspend fun cancelarMeta(uid: String, metaId: String): AppResult<Unit> = try {
        val ahora = Instant.now()
        metasRef(uid).document(metaId).set(
            mapOf(
                "activa" to false,
                "estado" to EstadoMeta.CANCELADA.name,
                "actualizadaEn" to timestamp(ahora),
            ),
            SetOptions.merge(),
        ).await()
        offlineStore.getMetas(uid).firstOrNull { it.id == metaId }?.let {
            offlineStore.upsertMeta(it.copy(activa = false, estado = EstadoMeta.CANCELADA, actualizadaEn = ahora))
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error cancelando meta: ${e.message}", e))
    }

    override suspend fun depositar(
        uid: String,
        metaId: String,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String,
    ): AppResult<Meta> = ejecutarMovimiento(
        uid = uid,
        metaId = metaId,
        cuentaId = cuentaId,
        monto = monto,
        requestId = requestId,
        tipo = TipoMovimientoMeta.DEPOSIT,
    )

    override suspend fun retirar(
        uid: String,
        metaId: String,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String,
    ): AppResult<Meta> = ejecutarMovimiento(
        uid = uid,
        metaId = metaId,
        cuentaId = cuentaId,
        monto = monto,
        requestId = requestId,
        tipo = TipoMovimientoMeta.WITHDRAW,
    )

    override fun observarMovimientos(uid: String, metaId: String): Flow<List<MovimientoMeta>> = callbackFlow {
        val listener = metasRef(uid).document(metaId)
            .collection("movimientos")
            .orderBy("creadoEn", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val movimientos = snapshot?.documents
                    ?.mapNotNull { it.toMovimientoMetaCompat() }
                    .orEmpty()
                trySend(movimientos)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun ejecutarMovimiento(
        uid: String,
        metaId: String,
        cuentaId: String,
        monto: BigDecimal,
        requestId: String,
        tipo: TipoMovimientoMeta,
    ): AppResult<Meta> = try {
        require(monto > BigDecimal.ZERO) { "El monto debe ser mayor que cero" }

        val metaActualizada = firestore.runTransaction { tx ->
            val metaRef = metasRef(uid).document(metaId)
            val operacionRef = usuarioRef(uid).collection("operacionesMeta").document(requestId)
            val operacionSnapshot = tx.get(operacionRef)
            val metaSnapshot = tx.get(metaRef)
            val meta = metaSnapshot.toMetaCompat(uid) ?: error("Meta no encontrada")

            if (operacionSnapshot.exists()) {
                return@runTransaction meta
            }

            if (meta.estado == EstadoMeta.CANCELADA || !meta.activa) {
                error("La meta no admite movimientos")
            }
            if (meta.cuentaId != null && meta.cuentaId != cuentaId) {
                error("La meta pertenece a otra cuenta")
            }

            val balanceAntes = meta.montoActual
            val balanceDespues = when (tipo) {
                TipoMovimientoMeta.DEPOSIT -> balanceAntes + monto
                TipoMovimientoMeta.WITHDRAW -> {
                    if (monto > balanceAntes) error("Fondos insuficientes en la meta")
                    balanceAntes - monto
                }
                else -> error("Tipo de movimiento no soportado")
            }
            val ahora = Instant.now()
            val estado = when {
                balanceDespues >= meta.montoObjetivo && meta.montoObjetivo > BigDecimal.ZERO -> EstadoMeta.COMPLETADA
                else -> EstadoMeta.ACTIVA
            }
            val actualizada = meta.copy(
                cuentaId = meta.cuentaId ?: cuentaId,
                montoActual = balanceDespues,
                estado = estado,
                activa = estado != EstadoMeta.CANCELADA,
                actualizadaEn = ahora,
            )
            val movimiento = MovimientoMeta(
                id = UUID.randomUUID().toString(),
                uidUsuario = uid,
                metaId = metaId,
                cuentaId = cuentaId,
                tipo = tipo,
                monto = monto,
                balanceAntes = balanceAntes,
                balanceDespues = balanceDespues,
                requestId = requestId,
                creadoEn = ahora,
            )

            tx.set(metaRef, actualizada.toMetaDto(), SetOptions.merge())
            tx.set(
                metaRef.collection("movimientos").document(movimiento.id),
                movimiento.toDto(),
            )
            tx.set(
                operacionRef,
                mapOf(
                    "requestId" to requestId,
                    "metaId" to metaId,
                    "cuentaId" to cuentaId,
                    "tipo" to tipo.name,
                    "monto" to monto.toPlainString(),
                    "creadoEn" to timestamp(ahora),
                ),
            )
            tx.set(
                usuarioRef(uid).collection("auditoria").document(UUID.randomUUID().toString()),
                mapOf(
                    "accion" to "META_${tipo.name}",
                    "uidUsuario" to uid,
                    "metaId" to metaId,
                    "cuentaId" to cuentaId,
                    "monto" to monto.toPlainString(),
                    "requestId" to requestId,
                    "ip" to null,
                    "dispositivo" to "android",
                    "creadoEn" to timestamp(ahora),
                ),
            )
            actualizada
        }.await()

        offlineStore.upsertMeta(metaActualizada)
        AppResult.Success(metaActualizada)
    } catch (e: Exception) {
        AppResult.Error(ErrorApp.FirestoreError("Error registrando movimiento de meta: ${e.message}", e))
    }

    private suspend fun syncRemote(uid: String) {
        runCatching {
            val snapshot = metasRef(uid)
                .whereEqualTo("activa", true)
                .get()
                .await()
            val metas = snapshot.documents.mapNotNull { doc -> doc.toMetaCompat(uid) }
            if (metas.isNotEmpty()) offlineStore.upsertMetas(metas)
        }
    }
}

private fun DocumentSnapshot.toMetaCompat(uidFallback: String): Meta? = runCatching {
    val montoObjetivo = money("montoObjetivo") ?: BigDecimal.ZERO.setScale(2)
    val montoActual = money("montoActual") ?: BigDecimal.ZERO.setScale(2)
    val activa = getBoolean("activa") ?: true
    val fechaLimite = getTimestamp("fechaLimite")?.toDate()?.toInstant()
    val creadoEn = getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now()
    val estado = getString("estado")
        ?.let { runCatching { EstadoMeta.valueOf(it) }.getOrNull() }
        ?: when {
            !activa -> EstadoMeta.CANCELADA
            montoObjetivo > BigDecimal.ZERO && montoActual >= montoObjetivo -> EstadoMeta.COMPLETADA
            else -> EstadoMeta.ACTIVA
        }

    Meta(
        id = id,
        uidUsuario = getString("uidUsuario") ?: uidFallback,
        nombre = getString("nombre") ?: "",
        emoji = getString("emoji") ?: "",
        montoObjetivo = montoObjetivo,
        montoActual = montoActual,
        fechaLimite = fechaLimite,
        activa = activa,
        creadoEn = creadoEn,
        descripcion = getString("descripcion"),
        categoria = getString("categoria")
            ?.let { runCatching { CategoriaMeta.valueOf(it) }.getOrNull() }
            ?: CategoriaMeta.OTRO,
        cuentaId = getString("cuentaId"),
        fechaObjetivo = getTimestamp("fechaObjetivo")?.toDate()?.toInstant() ?: fechaLimite,
        estado = estado,
        actualizadaEn = getTimestamp("actualizadaEn")?.toDate()?.toInstant() ?: creadoEn,
    )
}.getOrNull()

private fun DocumentSnapshot.toMovimientoMetaCompat(): MovimientoMeta? = runCatching {
    MovimientoMeta(
        id = id,
        uidUsuario = getString("uidUsuario") ?: return null,
        metaId = getString("metaId") ?: return null,
        cuentaId = getString("cuentaId"),
        tipo = TipoMovimientoMeta.valueOf(getString("tipo") ?: return null),
        monto = money("monto") ?: BigDecimal.ZERO.setScale(2),
        balanceAntes = money("balanceAntes") ?: BigDecimal.ZERO.setScale(2),
        balanceDespues = money("balanceDespues") ?: BigDecimal.ZERO.setScale(2),
        metaDestinoId = getString("metaDestinoId"),
        requestId = getString("requestId") ?: "",
        creadoEn = getTimestamp("creadoEn")?.toDate()?.toInstant() ?: Instant.now(),
    )
}.getOrNull()

private fun Meta.normalizarEstado(): Meta {
    val estadoNormalizado = when {
        !activa -> EstadoMeta.CANCELADA
        montoObjetivo > BigDecimal.ZERO && montoActual >= montoObjetivo -> EstadoMeta.COMPLETADA
        else -> estado
    }
    return copy(estado = estadoNormalizado)
}

private fun Meta.toMetaDto() = MetaDto(
    id = id,
    uidUsuario = uidUsuario,
    nombre = nombre,
    emoji = emoji,
    montoObjetivo = montoObjetivo.toPlainString(),
    montoActual = montoActual.toPlainString(),
    fechaLimite = fechaLimite?.let { timestamp(it) },
    activa = activa,
    creadoEn = timestamp(creadoEn),
    descripcion = descripcion,
    categoria = categoria.name,
    cuentaId = cuentaId,
    fechaObjetivo = fechaObjetivo?.let { timestamp(it) },
    estado = estado.name,
    actualizadaEn = timestamp(actualizadaEn),
)

private fun MovimientoMeta.toDto() = MovimientoMetaDto(
    id = id,
    uidUsuario = uidUsuario,
    metaId = metaId,
    cuentaId = cuentaId,
    tipo = tipo.name,
    monto = monto.toPlainString(),
    balanceAntes = balanceAntes.toPlainString(),
    balanceDespues = balanceDespues.toPlainString(),
    metaDestinoId = metaDestinoId,
    requestId = requestId,
    creadoEn = timestamp(creadoEn),
)

private fun timestamp(instant: Instant): Timestamp = Timestamp(Date.from(instant))
