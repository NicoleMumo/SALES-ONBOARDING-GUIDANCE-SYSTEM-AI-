package com.example.salesonboarding

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class SalesMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val database = realtimeDatabase()
        database.setPersistenceEnabled(true)
    }

    companion object {
        private const val DATABASE_URL =
            "https://salesmesh-default-rtdb.asia-southeast1.firebasedatabase.app"

        fun realtimeDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL)
    }
}
