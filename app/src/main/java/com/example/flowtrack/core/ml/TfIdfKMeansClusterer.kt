package com.example.flowtrack.core.ml

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Motor simple de TF-IDF y K-Means para agrupar transacciones no categorizadas
 * por similitud de descripción.
 */
class TfIdfKMeansClusterer {

    data class Documento(val id: String, val tokens: List<String>)
    
    data class Cluster(val centroid: DoubleArray, val docIds: MutableList<String>)

    fun agrupar(textos: Map<String, String>, numClusters: Int = 5, maxIters: Int = 10): Map<Int, List<String>> {
        if (textos.isEmpty()) return emptyMap()

        // 1. Tokenización (usamos palabras de longitud >= 3)
        val docs = textos.map { (id, desc) ->
            val tokens = desc.split(Regex("\\s+")).filter { it.length >= 3 }
            Documento(id, tokens)
        }

        // 2. Construir vocabulario
        val vocabulario = docs.flatMap { it.tokens }.distinct().sorted()
        if (vocabulario.isEmpty()) return emptyMap()

        // 3. TF-IDF
        val idf = vocabulario.map { termino ->
            val docCount = docs.count { it.tokens.contains(termino) }
            log10(docs.size.toDouble() / (docCount + 1))
        }

        val tfIdfVectors = docs.associate { doc ->
            val totalTokens = doc.tokens.size.coerceAtLeast(1)
            val vector = DoubleArray(vocabulario.size) { i ->
                val tf = doc.tokens.count { it == vocabulario[i] }.toDouble() / totalTokens
                tf * idf[i]
            }
            doc.id to vector
        }

        // 4. K-Means
        val ids = tfIdfVectors.keys.toList()
        val k = minOf(numClusters, ids.size)
        
        // Init centroids al azar
        val random = Random(42) // Semilla fija para consistencia
        val centroids = ids.shuffled(random).take(k).map { tfIdfVectors[it]!!.clone() }.toMutableList()

        var clusters = emptyList<Cluster>()

        for (iter in 0 until maxIters) {
            // Asignar al centroide más cercano (distancia euclidiana)
            clusters = centroids.map { Cluster(it, mutableListOf()) }
            
            for (id in ids) {
                val vec = tfIdfVectors[id]!!
                var minIndex = 0
                var minDist = Double.MAX_VALUE
                
                centroids.forEachIndexed { idx, cent ->
                    val dist = distanciaEuclidiana(vec, cent)
                    if (dist < minDist) {
                        minDist = dist
                        minIndex = idx
                    }
                }
                clusters[minIndex].docIds.add(id)
            }

            // Recalcular centroides
            for (i in 0 until k) {
                val clusterIds = clusters[i].docIds
                if (clusterIds.isEmpty()) continue
                
                val newCentroid = DoubleArray(vocabulario.size)
                for (id in clusterIds) {
                    val vec = tfIdfVectors[id]!!
                    for (j in newCentroid.indices) {
                        newCentroid[j] += vec[j]
                    }
                }
                for (j in newCentroid.indices) {
                    newCentroid[j] /= clusterIds.size
                }
                centroids[i] = newCentroid
            }
        }

        // Retornar mapa de (Index -> Lista de IDs de transacciones en el cluster)
        return clusters.mapIndexed { index, cluster -> 
            index to cluster.docIds 
        }.toMap().filter { it.value.isNotEmpty() }
    }

    private fun distanciaEuclidiana(v1: DoubleArray, v2: DoubleArray): Double {
        var sum = 0.0
        for (i in v1.indices) {
            sum += (v1[i] - v2[i]).pow(2)
        }
        return sqrt(sum)
    }
}
