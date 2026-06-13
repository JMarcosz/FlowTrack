package com.example.flowtrack.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirestorePermissionsE2ETest {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    @Before
    fun setup() = runBlocking {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    @Test
    fun test_read_cuentas() = runBlocking {
        val uid = auth.currentUser!!.uid
        try {
            db.collection("usuarios").document(uid).collection("cuentas")
                .orderBy("creadoEn", Query.Direction.ASCENDING)
                .get()
                .await()
            assertTrue(true)
        } catch (e: Exception) {
            throw AssertionError("Cuentas read failed: ${e.message}", e)
        }
    }

    @Test
    fun test_read_tarjetas() = runBlocking {
        val uid = auth.currentUser!!.uid
        try {
            db.collection("usuarios").document(uid).collection("tarjetas")
                .orderBy("creadoEn", Query.Direction.ASCENDING)
                .get()
                .await()
            assertTrue(true)
        } catch (e: Exception) {
            throw AssertionError("Tarjetas read failed: ${e.message}", e)
        }
    }

    @Test
    fun test_read_movimientos_where() = runBlocking {
        val uid = auth.currentUser!!.uid
        try {
            db.collection("usuarios").document(uid).collection("movimientosTarjeta")
                .whereGreaterThanOrEqualTo("fechaTransaccion", com.google.firebase.Timestamp.now())
                .orderBy("fechaTransaccion", Query.Direction.ASCENDING)
                .get()
                .await()
            assertTrue(true)
        } catch (e: Exception) {
            throw AssertionError("MovimientosTarjeta read failed: ${e.message}", e)
        }
    }

    @Test
    fun test_write_dispositivo() = runBlocking {
        val uid = auth.currentUser!!.uid
        try {
            db.collection("usuarios").document(uid).collection("dispositivos").document("test_device")
                .set(mapOf("test" to true))
                .await()
            assertTrue(true)
        } catch (e: Exception) {
            throw AssertionError("Dispositivos write failed: ${e.message}", e)
        }
    }
}
