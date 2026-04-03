package com.smishguard.app

import android.app.Application

/*
 * SmishGuardApplication.kt — Application Class
 * ===============================================
 * The Application class is the FIRST thing created when your app starts,
 * even before any Activity. It lives for the entire lifetime of the app.
 *
 * Use it for:
 *   - One-time global initialization
 *   - Holding app-wide resources
 *
 * We declared this in AndroidManifest.xml with android:name=".SmishGuardApplication"
 * so Android knows to use OUR Application class instead of the default one.
 *
 * "class SmishGuardApplication : Application()" means:
 *   SmishGuardApplication INHERITS FROM Application
 *   "()" calls the parent's constructor
 */
class SmishGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // "super.onCreate()" calls the PARENT class's onCreate() first —
        // this is required to ensure Android's internal setup completes.

        // Future: Initialize analytics, crash reporting, etc. here
        // All initialisation here runs ONCE when the app process starts.
    }
}
