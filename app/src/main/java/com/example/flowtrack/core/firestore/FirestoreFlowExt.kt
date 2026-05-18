package com.example.flowtrack.core.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Convierte una [Query] de Firestore en un [Flow] reactivo usando snapshotListener.
 *  La primera emisión sale del cache local (instantánea); las siguientes solo cuando
 *  el servidor detecta cambios reales. */
fun <T : Any> Query.asListFlow(clazz: Class<T>): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) { close(error); return@addSnapshotListener }
        val items = snapshot?.documents?.mapNotNull {
            runCatching { it.toObject(clazz) }.getOrNull()
        } ?: emptyList()
        trySend(items)
    }
    awaitClose { registration.remove() }
}

/** Variante con mapper explícito para docs que no tienen un DTO 1-a-1. */
fun <T> Query.asMappedListFlow(mapper: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) { close(error); return@addSnapshotListener }
        val items = snapshot?.documents?.mapNotNull(mapper) ?: emptyList()
        trySend(items)
    }
    awaitClose { registration.remove() }
}
