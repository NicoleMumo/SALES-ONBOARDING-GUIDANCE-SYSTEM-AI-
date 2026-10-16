package com.example.salesonboarding

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class SalesMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val database = FirebaseDatabase.getInstance()
        database.setPersistenceEnabled(true)
        database.reference.child("fieldDefinitions").keepSynced(true)
        database.reference.child("fieldRuleOverrides").keepSynced(true)
    }
}
