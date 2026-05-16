package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.firestore.dto.CuentaDto
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.firestore.mappers.toDomain
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio de Firestore para la capa de importación.
 * Usa WriteBatch con chunks de 450 (margen bajo el límite de 500 de Firestore).
 * La escritura es idempotente: set() con merge en transacciones (mismo ID = no duplica).
 */
@Singleton
class ImportacionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Persiste una carga completa en Firestore de forma atómica por chunks.
     * - Cuenta: se escribe en /usuarios/{uid}/cuentas/{cuentaId}
     * - Carga: se escribe en /usuarios/{uid}/cargas/{cargaId}
     * - Transacciones: se escriben en /usuarios/{uid}/transacciones/{txId}
     *
     * La protección de balance evita que re-importar un estado viejo pise el
     * `balanceActual` de una importación más reciente. Solo se actualiza
     * `balanceActual` si la nueva carga tiene `periodoFin >= fechaUltimoCorte`.
     */
    suspend fun persistirCarga(
        uid: String,
        cuenta: Cuenta,
        transacciones: List<Transaccion>,
        carga: Carga,
    ): AppResult<Unit> {
        return try {
            val refUsuario = firestore.collection("usuarios").document(uid)

            transacciones.chunked(450).forEachIndexed { chunkIdx, chunk ->
                val batch = firestore.batch()

                if (chunkIdx == 0) {
                    val refCuenta = refUsuario.collection("cuentas").document(cuenta.id)
                    val cuentaDto = construirDtoCuentaConBalanceProtegido(refCuenta, cuenta, carga)
                    batch.set(refCuenta, cuentaDto, SetOptions.merge())

                    val refCarga = refUsuario.collection("cargas").document(carga.id)
                    if (!refCarga.get().await().exists()) {
                        batch.set(refCarga, carga.toDto())
                    }
                }

                chunk.forEach { tx ->
                    val ref = refUsuario.collection("transacciones").document(tx.id)
                    batch.set(ref, tx.toDto())
                }

                batch.commit().await()
            }

            // Si no hay transacciones, el bucle no se ejecuta — persistir cuenta y carga igual
            if (transacciones.isEmpty()) {
                val batch = firestore.batch()
                val refCuenta = refUsuario.collection("cuentas").document(cuenta.id)
                val cuentaDto = construirDtoCuentaConBalanceProtegido(refCuenta, cuenta, carga)
                batch.set(refCuenta, cuentaDto, SetOptions.merge())
                val refCarga = refUsuario.collection("cargas").document(carga.id)
                if (!refCarga.get().await().exists()) {
                    batch.set(refCarga, carga.toDto())
                }
                batch.commit().await()
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(
                ErrorApp.FirestoreError(
                    mensaje = "Error al guardar en Firestore: ${e.message}",
                    causa = e,
                )
            )
        }
    }

    /**
     * Construye el DTO de Cuenta protegiendo `balanceActual`:
     * solo lo incluye en el merge si la nueva carga es más reciente que
     * el último corte ya guardado en Firestore.
     */
    private suspend fun construirDtoCuentaConBalanceProtegido(
        refCuenta: com.google.firebase.firestore.DocumentReference,
        nuevaCuenta: Cuenta,
        nuevaCarga: Carga,
    ): CuentaDto {
        val snap = refCuenta.get().await()
        val cuentaExistente: Cuenta? = if (snap.exists()) {
            runCatching { snap.toObject(CuentaDto::class.java)?.toDomain() }.getOrNull()
        } else null

        val nuevoPeriodoFin: Instant? = nuevaCarga.periodoFin
        val ultimoCorte: Instant? = cuentaExistente?.fechaUltimoCorte

        // Proteger balance: no pisar si ya existe un corte más reciente
        val esImportacionMasReciente = nuevoPeriodoFin != null &&
            (ultimoCorte == null || !nuevoPeriodoFin.isBefore(ultimoCorte))

        return if (esImportacionMasReciente) {
            nuevaCuenta.toDto()
        } else {
            // Merge sin tocar balanceActual ni fechaUltimoCorte — solo sincronización
            nuevaCuenta.toDto().copy(
                balanceActual = cuentaExistente?.balanceActual?.toDouble(),
                fechaUltimoCorte = cuentaExistente?.fechaUltimoCorte
                    ?.let { com.google.firebase.Timestamp(it.epochSecond, it.nano) },
            )
        }
    }

    /**
     * Persiste una carga de tarjeta de crédito en Firestore.
     * - Tarjeta: /usuarios/{uid}/tarjetas/{tarjetaId} (merge — no sobreescribe alias editados)
     * - EstadoTarjetaSnap: /usuarios/{uid}/estadosTarjeta/{estadoId}
     * - MovimientosTarjeta: /usuarios/{uid}/movimientosTarjeta/{movId}
     * - Carga: /usuarios/{uid}/cargas/{cargaId}
     */
    suspend fun persistirCargaTarjeta(
        uid: String,
        tarjeta: Tarjeta,
        estadoTarjeta: EstadoTarjetaSnap,
        movimientos: List<MovimientoTarjeta>,
        carga: Carga,
    ): AppResult<Unit> {
        return try {
            val refUsuario = firestore.collection("usuarios").document(uid)

            movimientos.chunked(448).forEachIndexed { chunkIdx, chunk ->
                val batch = firestore.batch()

                if (chunkIdx == 0) {
                    val refTarjeta = refUsuario.collection("tarjetas").document(tarjeta.id)
                    batch.set(refTarjeta, tarjeta.toDto(), SetOptions.merge())

                    val refEstado = refUsuario.collection("estadosTarjeta").document(estadoTarjeta.id)
                    batch.set(refEstado, estadoTarjeta.toDto())

                    // Cargas son inmutables en Firestore; solo escribir si no existe aún
                    val refCarga = refUsuario.collection("cargas").document(carga.id)
                    if (!refCarga.get().await().exists()) {
                        batch.set(refCarga, carga.toDto())
                    }
                }

                chunk.forEach { mov ->
                    val ref = refUsuario.collection("movimientosTarjeta").document(mov.id)
                    batch.set(ref, mov.toDto())
                }

                batch.commit().await()
            }

            // Si no hay movimientos, el bucle no se ejecuta — persistir de todas formas
            if (movimientos.isEmpty()) {
                val batch = firestore.batch()
                batch.set(refUsuario.collection("tarjetas").document(tarjeta.id), tarjeta.toDto(), SetOptions.merge())
                batch.set(refUsuario.collection("estadosTarjeta").document(estadoTarjeta.id), estadoTarjeta.toDto())
                val refCarga = refUsuario.collection("cargas").document(carga.id)
                if (!refCarga.get().await().exists()) {
                    batch.set(refCarga, carga.toDto())
                }
                batch.commit().await()
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(
                ErrorApp.FirestoreError(
                    mensaje = "Error al guardar tarjeta en Firestore: ${e.message}",
                    causa = e,
                )
            )
        }
    }

    /**
     * Verifica cuántas transacciones de una lista ya existen en Firestore.
     * Se usa para calcular el conteo de duplicados antes de la escritura.
     * Limitado a 30 verificaciones para no exceder las lecturas gratuitas.
     */
    suspend fun contarDuplicados(uid: String, txIds: List<String>): Int {
        return try {
            val refTransacciones = firestore.collection("usuarios")
                .document(uid)
                .collection("transacciones")

            // Solo verificar una muestra (máx 30) para estimar
            var count = 0
            txIds.take(30).forEach { id ->
                val doc = refTransacciones.document(id).get().await()
                if (doc.exists()) count++
            }
            count
        } catch (e: Exception) {
            0 // Si falla, asumir 0 duplicados (lo peor que pasa es sobreescritura idempotente)
        }
    }
}
