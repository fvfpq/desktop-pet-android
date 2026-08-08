package com.example.lolipet

import android.app.Application

class PetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
