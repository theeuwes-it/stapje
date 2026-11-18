package nl.theeuwesit.stapje

import android.app.Application
import nl.theeuwesit.stapje.sync.Cloud

class StapjeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Na inloggen stuurt Supabase terug naar stapje://login (zie AndroidManifest).
        Cloud.init {
            scheme = "stapje"
            host = "login"
        }
    }
}
