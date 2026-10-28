package com.example.salesonboarding

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class SalesMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        realtimeDatabase()
    }

    companion object {
        private const val DATABASE_URL =
            "https://salesmesh-default-rtdb.asia-southeast1.firebasedatabase.app"

        private val database: FirebaseDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            FirebaseDatabase.getInstance(DATABASE_URL).also {
                it.setPersistenceEnabled(true)
            }
        }

        fun realtimeDatabase(): FirebaseDatabase = database
    }
}
