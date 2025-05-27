package com.example.projectmdp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class MyApplication : Application() {
    companion object{
//        lateinit var db: AppDatabase
    }
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Enable persistence for offline support (optional)
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
//        db = AppDatabase.invoke(baseContext)
    }
}