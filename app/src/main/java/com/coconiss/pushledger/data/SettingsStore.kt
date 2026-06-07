package com.coconiss.pushledger.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("push_ledger_settings", Context.MODE_PRIVATE)

    var locationCaptureEnabled: Boolean
        get() = prefs.getBoolean("location_capture_enabled", false)
        set(value) = prefs.edit().putBoolean("location_capture_enabled", value).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock_enabled", false)
        set(value) = prefs.edit().putBoolean("app_lock_enabled", value).apply()

    var pinHash: String?
        get() = prefs.getString("pin_hash", null)
        set(value) = prefs.edit().putString("pin_hash", value).apply()

    var allowedPackages: Set<String>
        get() = prefs.getStringSet("allowed_packages", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("allowed_packages", value).apply()
}
