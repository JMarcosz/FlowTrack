package com.example.flowtrack.data.firestore.repositories

import com.example.flowtrack.data.local.OfflineStore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TarjetaRepositoryTest {

    @Test
    fun `eliminarTarjeta borra estados y movimientos asociados y desactiva la tarjeta`() = runTest {
        val firestore = mock<FirebaseFirestore>()
        val offlineStore = mock<OfflineStore>()

        val usuariosCol = mock<CollectionReference>()
        val usuarioDoc = mock<DocumentReference>()
        val tarjetasCol = mock<CollectionReference>()
        val tarjetaDoc = mock<DocumentReference>()
        val movimientosCol = mock<CollectionReference>()
        val estadosCol = mock<CollectionReference>()
        val movQuery = mock<Query>()
        val estQuery = mock<Query>()
        val movSnapshot = mock<QuerySnapshot>()
        val estSnapshot = mock<QuerySnapshot>()
        val movDoc = mock<DocumentSnapshot>()
        val estDoc = mock<DocumentSnapshot>()
        val movRef = mock<DocumentReference>()
        val estRef = mock<DocumentReference>()
        val batch = mock<WriteBatch>()

        whenever(firestore.collection("usuarios")).thenReturn(usuariosCol)
        whenever(usuariosCol.document("uid-1")).thenReturn(usuarioDoc)
        whenever(usuarioDoc.collection("tarjetas")).thenReturn(tarjetasCol)
        whenever(tarjetasCol.document("tar-1")).thenReturn(tarjetaDoc)
        whenever(usuarioDoc.collection("movimientosTarjeta")).thenReturn(movimientosCol)
        whenever(usuarioDoc.collection("estadosTarjeta")).thenReturn(estadosCol)
        whenever(movimientosCol.whereEqualTo("tarjetaId", "tar-1")).thenReturn(movQuery)
        whenever(estadosCol.whereEqualTo("tarjetaId", "tar-1")).thenReturn(estQuery)
        whenever(movQuery.get()).thenReturn(Tasks.forResult(movSnapshot))
        whenever(estQuery.get()).thenReturn(Tasks.forResult(estSnapshot))
        whenever(movSnapshot.documents).thenReturn(listOf(movDoc))
        whenever(estSnapshot.documents).thenReturn(listOf(estDoc))
        whenever(movDoc.reference).thenReturn(movRef)
        whenever(estDoc.reference).thenReturn(estRef)
        whenever(firestore.batch()).thenReturn(batch)
        whenever(batch.commit()).thenReturn(Tasks.forResult(null))
        whenever(tarjetaDoc.update("activa", false)).thenReturn(Tasks.forResult(null))

        val repository = TarjetaRepository(firestore, offlineStore)

        val result = repository.eliminarTarjeta("uid-1", "tar-1")

        assertTrue(result is com.example.flowtrack.core.result.AppResult.Success)
        verify(offlineStore).deactivateTarjeta("uid-1", "tar-1")
        verify(offlineStore).deleteByTarjetaId("MOVIMIENTO_TARJETA", "uid-1", "tar-1")
        verify(offlineStore).deleteByTarjetaId("ESTADO_TARJETA", "uid-1", "tar-1")
        verify(movimientosCol).whereEqualTo("tarjetaId", "tar-1")
        verify(estadosCol).whereEqualTo("tarjetaId", "tar-1")
        verify(batch).delete(movRef)
        verify(batch).delete(estRef)
        verify(tarjetaDoc).update("activa", false)
    }
}
