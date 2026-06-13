package com.example.flowtrack.domain.usecase

import com.example.flowtrack.core.ml.TfIdfKMeansClusterer
import com.example.flowtrack.core.result.AppResult
import com.example.flowtrack.data.firestore.repositories.ReglaSugeridaRepository
import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import com.example.flowtrack.domain.model.ReglaSugerida
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class AnalizarTransaccionesUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
    private val sugeridaRepository: ReglaSugeridaRepository
) {
    suspend fun ejecutar(uid: String): AppResult<Int> {
        // 1. Obtener transacciones recientes (ej. ultimo mes)
        val res = transaccionRepository.obtenerTransacciones(uid, limite = 1000)
        if (res is AppResult.Error) return AppResult.Error(res.error)

        val transacciones = (res as AppResult.Success).data
        
        // 2. Filtrar no categorizadas
        val noCategorizadas = transacciones.filter { it.categoriaId == null || it.categoriaId == "sin_categorizar" }
        if (noCategorizadas.size < 5) return AppResult.Success(0) // Muy pocas para clusterizar

        // 3. Clusterizar
        val motor = TfIdfKMeansClusterer()
        val textos = noCategorizadas.associate { it.id to it.descripcionNormalizada }
        
        // Asignamos un n° de clusters dinámico (1 por cada 5 items máx)
        val numClusters = (noCategorizadas.size / 5).coerceIn(2, 10)
        
        val clusters = motor.agrupar(textos, numClusters = numClusters, maxIters = 20)
        
        val sugerencias = mutableListOf<ReglaSugerida>()
        
        // 4. Analizar clusters para extraer reglas
        for ((_, docIds) in clusters) {
            if (docIds.size < 3) continue // Ignorar clusters muy pequeños
            
            // Determinar un "patrón detectado" (usaremos la descripción del primero como base para la sugerencia)
            val txsEnCluster = noCategorizadas.filter { it.id in docIds }
            
            // Encontrar la palabra más común en el cluster
            val allWords = txsEnCluster.flatMap { it.descripcionNormalizada.split(Regex("\\s+")) }
            val wordCounts = allWords.groupingBy { it }.eachCount()
            val bestWord = wordCounts.maxByOrNull { it.value }?.key ?: continue
            
            if (bestWord.length < 3) continue

            val sugerencia = ReglaSugerida(
                id = UUID.randomUUID().toString(),
                uidUsuario = uid,
                patronDetectado = bestWord,
                categoriaSugerida = "sin_categorizar", // El usuario debe asignarla en UI
                muestras = txsEnCluster.map { it.id }.take(5), // Guardamos una muestra de hasta 5 ids
                confianzaCluster = (docIds.size.toFloat() / noCategorizadas.size.toFloat()) * 100f,
                creadaEn = Instant.now()
            )
            sugerencias.add(sugerencia)
        }

        if (sugerencias.isEmpty()) return AppResult.Success(0)

        // 5. Guardar
        val saveRes = sugeridaRepository.guardarReglas(uid, sugerencias)
        if (saveRes is AppResult.Error) return AppResult.Error(saveRes.error)

        return AppResult.Success(sugerencias.size)
    }
}
