package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.core.result.ErrorApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class TasaCambio(
    val compra: Double,
    val venta: Double,
    val fecha: LocalDate,
    val fuente: String // "BCRD" o "TasaReal" o "Firebase"
)

@Singleton
class TasaCambioRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun obtenerTasaDelDia(): AppResult<TasaCambio> {
        val hoy = LocalDate.now(ZoneId.of("America/Santo_Domingo"))
        val docId = hoy.toString()
        
        return try {
            // Intentar leer de Firestore (caché)
            val snapshot = firestore.collection("tasasCambio").document(docId).get().await()
            if (snapshot.exists()) {
                val compra = snapshot.getDouble("compra") ?: 0.0
                val venta = snapshot.getDouble("venta") ?: 0.0
                val fuente = snapshot.getString("fuente") ?: "Firebase"
                return AppResult.Success(TasaCambio(compra, venta, hoy, fuente))
            }
            
            // Si no existe, simulamos la llamada a la API del BCRD
            // TODO: Integrar cliente retrofilt para la API real de BCRD
            val tasaMock = TasaCambio(58.50, 59.10, hoy, "BCRD (Mock)")
            
            // Guardar en Firestore para cachear el día
            firestore.collection("tasasCambio").document(docId).set(
                mapOf(
                    "compra" to tasaMock.compra,
                    "venta" to tasaMock.venta,
                    "fecha" to tasaMock.fecha.toString(),
                    "fuente" to tasaMock.fuente
                )
            ).await()
            
            AppResult.Success(tasaMock)
        } catch (e: Exception) {
            AppResult.Error(ErrorApp.FirestoreError("Error obteniendo tasa de cambio: ${e.message}", e))
        }
    }
}
