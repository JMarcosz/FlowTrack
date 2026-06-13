package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.local.OfflineStore
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.EstadoTarjetaSnap
import com.example.flowtrack.domain.model.MovimientoTarjeta
import com.example.flowtrack.domain.model.Tarjeta
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
    private val offlineStore: OfflineStore,
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
            val cuentaLocal = offlineStore.getCuentas(uid).firstOrNull { it.id == cuenta.id }
            val cuentaAGuardar = construirCuentaParaImportacion(cuentaLocal, cuenta, carga)
            offlineStore.upsertCuenta(cuentaAGuardar)
            offlineStore.upsertCarga(carga)
            offlineStore.upsertTransacciones(transacciones)
            val refUsuario = firestore.collection("usuarios").document(uid)

            transacciones.chunked(450).forEachIndexed { chunkIdx, chunk ->
                val batch = firestore.batch()

                if (chunkIdx == 0) {
                    val refCuenta = refUsuario.collection("cuentas").document(cuenta.id)
                    batch.set(refCuenta, cuentaAGuardar.toDto(), SetOptions.merge())

                    val refCarga = refUsuario.collection("cargas").document(carga.id)
                    batch.set(refCarga, carga.toDto(), SetOptions.merge())
                }

                chunk.forEach { tx ->
                    val ref = refUsuario.collection("transacciones").document(tx.id)
                    batch.set(ref, tx.toDto(), SetOptions.merge())
                }

                batch.commit().await()
            }

            // Si no hay transacciones, el bucle no se ejecuta — persistir cuenta y carga igual
            if (transacciones.isEmpty()) {
                val batch = firestore.batch()
                val refCuenta = refUsuario.collection("cuentas").document(cuenta.id)
                batch.set(refCuenta, cuentaAGuardar.toDto(), SetOptions.merge())
                val refCarga = refUsuario.collection("cargas").document(carga.id)
                batch.set(refCarga, carga.toDto(), SetOptions.merge())
                batch.commit().await()
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            if (e.esOfflineFirestore()) {
                return AppResult.Success(Unit)
            }
            AppResult.Error(
                ErrorApp.FirestoreError(
                    mensaje = e.mensajeImportacionFirestore("guardar el estado de cuenta"),
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
    private fun construirCuentaParaImportacion(
        cuentaExistente: Cuenta?,
        nuevaCuenta: Cuenta,
        nuevaCarga: Carga,
    ): Cuenta {
        val nuevoPeriodoFin: Instant? = nuevaCarga.periodoFin
        val ultimoCorte: Instant? = cuentaExistente?.fechaUltimoCorte

        // Proteger balance: no pisar si ya existe un corte más reciente
        val esImportacionMasReciente = nuevoPeriodoFin != null &&
            (ultimoCorte == null || !nuevoPeriodoFin.isBefore(ultimoCorte))

        return if (esImportacionMasReciente || cuentaExistente == null) nuevaCuenta
        else nuevaCuenta.copy(
            balanceActual = cuentaExistente.balanceActual,
            fechaUltimoCorte = cuentaExistente.fechaUltimoCorte,
            alias = cuentaExistente.alias.ifBlank { nuevaCuenta.alias },
        )
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
            val tarjetaExistenteLocal = offlineStore.getTarjetas(uid).firstOrNull { it.id == tarjeta.id }
            val tarjetaAGuardar = tarjetaExistenteLocal?.copy(
                alias = tarjetaExistenteLocal.alias.ifBlank { tarjeta.alias },
                tipoRed = tarjeta.tipoRed ?: tarjetaExistenteLocal.tipoRed,
                limiteCredito = tarjeta.limiteCredito,
                moneda = tarjeta.moneda,
                diaCorte = tarjeta.diaCorte,
                diaPago = tarjeta.diaPago,
                tasaInteresAnual = tarjeta.tasaInteresAnual,
                tasaInteresOrigen = tarjeta.tasaInteresOrigen,
                estado = tarjeta.estado,
                titular = tarjeta.titular.ifBlank { tarjetaExistenteLocal.titular },
                activa = tarjeta.activa || tarjetaExistenteLocal.activa,
                ultimaSincronizacion = tarjeta.ultimaSincronizacion ?: tarjetaExistenteLocal.ultimaSincronizacion,
                creadoEn = tarjetaExistenteLocal.creadoEn,
            ) ?: tarjeta

            val estadoExistenteLocal = offlineStore.getEstadosTarjeta(uid, tarjeta.id).firstOrNull { it.id == estadoTarjeta.id }
            val estadoAGuardar = estadoExistenteLocal?.copy(
                fechaCorte = estadoTarjeta.fechaCorte,
                fechaLimitePago = estadoTarjeta.fechaLimitePago,
                periodoInicio = estadoTarjeta.periodoInicio,
                periodoFin = estadoTarjeta.periodoFin,
                balanceAlCorte = estadoTarjeta.balanceAlCorte,
                balanceAnterior = estadoTarjeta.balanceAnterior ?: estadoExistenteLocal.balanceAnterior,
                pagoMinimo = estadoTarjeta.pagoMinimo,
                pagoTotal = estadoTarjeta.pagoTotal,
                montoVencido = estadoTarjeta.montoVencido,
                balancePromedioDiario = estadoTarjeta.balancePromedioDiario ?: estadoExistenteLocal.balancePromedioDiario,
                interesFinanciamiento = estadoTarjeta.interesFinanciamiento ?: estadoExistenteLocal.interesFinanciamiento,
                cashbackGanado = estadoTarjeta.cashbackGanado ?: estadoExistenteLocal.cashbackGanado,
                moneda = estadoTarjeta.moneda,
                cargaId = estadoTarjeta.cargaId.ifBlank { estadoExistenteLocal.cargaId },
                creadoEn = estadoExistenteLocal.creadoEn,
            ) ?: estadoTarjeta

            offlineStore.upsertTarjeta(tarjetaAGuardar)
            offlineStore.upsertEstadoTarjeta(estadoAGuardar)
            offlineStore.upsertMovimientosTarjeta(movimientos)
            offlineStore.upsertCarga(carga)
            val refUsuario = firestore.collection("usuarios").document(uid)

            movimientos.chunked(448).forEachIndexed { chunkIdx, chunk ->
                val batch = firestore.batch()

                if (chunkIdx == 0) {
                    val refTarjeta = refUsuario.collection("tarjetas").document(tarjeta.id)
                    batch.set(refTarjeta, tarjetaAGuardar.toDto(), SetOptions.merge())

                    val refEstado = refUsuario.collection("estadosTarjeta").document(estadoTarjeta.id)
                    batch.set(refEstado, estadoAGuardar.toDto(), SetOptions.merge())

                    val refCarga = refUsuario.collection("cargas").document(carga.id)
                    batch.set(refCarga, carga.toDto(), SetOptions.merge())
                }

                chunk.forEach { mov ->
                    val ref = refUsuario.collection("movimientosTarjeta").document(mov.id)
                    batch.set(ref, mov.toDto(), SetOptions.merge())
                }

                batch.commit().await()
            }

            // Si no hay movimientos, el bucle no se ejecuta — persistir de todas formas
            if (movimientos.isEmpty()) {
                val batch = firestore.batch()
                val refTarjeta = refUsuario.collection("tarjetas").document(tarjeta.id)
                batch.set(refTarjeta, tarjetaAGuardar.toDto(), SetOptions.merge())

                val refEstado = refUsuario.collection("estadosTarjeta").document(estadoTarjeta.id)
                batch.set(refEstado, estadoAGuardar.toDto(), SetOptions.merge())
                val refCarga = refUsuario.collection("cargas").document(carga.id)
                batch.set(refCarga, carga.toDto(), SetOptions.merge())
                batch.commit().await()
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            if (e.esOfflineFirestore()) {
                return AppResult.Success(Unit)
            }
            AppResult.Error(
                ErrorApp.FirestoreError(
                    mensaje = e.mensajeImportacionFirestore("guardar el estado de tarjeta"),
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

private fun Throwable.esOfflineFirestore(): Boolean {
    if (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE) return true
    val message = message?.lowercase().orEmpty()
    return message.contains("client is offline") || message.contains("unavailable")
}

private fun Throwable.mensajeImportacionFirestore(operacion: String): String {
    if (this is FirebaseFirestoreException &&
        code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    ) {
        return "Firestore rechazó la importación. Verifica que la sesión siga activa y que " +
            "firestore.rules esté desplegado en el proyecto flowtrack-bfd4b."
    }
    return "Error al $operacion en Firestore: ${message.orEmpty()}"
}
