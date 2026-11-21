package eu.hellenicdev.pebbles

import android.app.Application

/** Starts Sentry only when crash reports were already opted in. */
class PebblesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (SettingsStore(this).crashReportsEnabled()) {
            CrashReports.start(this)
        }
    }
}
