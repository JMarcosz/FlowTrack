package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.example.flowtrack.data.firestore.dto.CargaDto
import com.example.flowtrack.data.firestore.dto.CuentaDto
import com.example.flowtrack.data.firestore.dto.TransaccionDto
import com.example.flowtrack.data.firestore.mappers.toDto
import com.example.flowtrack.domain.model.Carga
import com.example.flowtrack.domain.model.Cuenta
import com.example.flowtrack.domain.model.Transaccion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
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
     * Las transacciones se escriben con set() sin merge para garantizar
     * que el mismo hash siempre produce el mismo documento (idempotente).
     */
    suspend fun persistirCarga(
        uid: String,
        cuenta: Cuenta,
        transacciones: List<Transaccion>,
        carga: Carga,
    ): AppResult<Unit> {
        return try {
            val refUsuario = firestore.collection("usuarios").document(uid)

            // Contar duplicados (documentos que ya existen)
            var duplicados = 0

            transacciones.chunked(450).forEachIndexed { chunkIdx, chunk ->
                val batch = firestore.batch()

                // En el primer chunk también persistimos cuenta y carga
                if (chunkIdx == 0) {
                    val refCuenta = refUsuario.collection("cuentas").document(cuenta.id)
                    // merge=true: si la cuenta ya existe, solo actualiza balance y sincronización
                    batch.set(refCuenta, cuenta.toDto(), SetOptions.merge())

                    val refCarga = refUsuario.collection("cargas").document(carga.id)
                    batch.set(refCarga, carga.toDto())
                }

                chunk.forEach { tx ->
                    val ref = refUsuario.collection("transacciones").document(tx.id)
                    // set() sin merge: si ya existe con el mismo ID, se sobreescribe
                    // (el resultado es idéntico, ya que el ID es determinístico)
                    batch.set(ref, tx.toDto())
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
