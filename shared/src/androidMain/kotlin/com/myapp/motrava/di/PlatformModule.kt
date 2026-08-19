package com.myapp.motrava.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.dsl.module
import org.koin.core.module.Module

actual fun platformModule(): Module = module {
    single<Settings> { 
        val context: Context = get()
        SharedPreferencesSettings(context.getSharedPreferences("motrava_prefs", Context.MODE_PRIVATE))
    }
    
    single<com.myapp.motrava.data.local.MotravaDatabase> {
        val context: Context = get()
        val dbFile = context.getDatabasePath("motrava.db")
        androidx.room.Room.databaseBuilder<com.myapp.motrava.data.local.MotravaDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath
        )
        .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }
}
