package de.k1direkt

import android.app.Application
import de.k1direkt.data.LibraryRepository
import de.k1direkt.data.SettingsRepository

class K1Application : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var library: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        library = LibraryRepository(this)
    }
}
