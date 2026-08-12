package com.myapp.motrava.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.dsl.module
import org.koin.core.module.Module
import platform.Foundation.NSUserDefaults

actual fun platformModule(): Module = module {
    single<Settings> { 
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults())
    }
    
    single<com.myapp.motrava.data.local.MotravaDatabase> {
        val documentDirectory = platform.Foundation.NSSearchPathForDirectoriesInDomains(
            platform.Foundation.NSDocumentDirectory,
            platform.Foundation.NSUserDomainMask,
            true
        ).first() as String
        val dbFilePath = "$documentDirectory/motrava.db"
        androidx.room.Room.databaseBuilder<com.myapp.motrava.data.local.MotravaDatabase>(
            name = dbFilePath
        )
        .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }
}
