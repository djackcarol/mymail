package com.personal.mymail.data

import android.content.Context

/** Simple app-wide settings kept in SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("mymail_settings", Context.MODE_PRIVATE)

    /** Check for new mail in the background and show notifications. */
    var backgroundSync: Boolean
        get() = prefs.getBoolean("background_sync", true)
        set(value) = prefs.edit().putBoolean("background_sync", value).apply()

    /** How often the background check runs. Android's minimum is 15 minutes. */
    var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 30)
        set(value) = prefs.edit().putInt("interval_minutes", value).apply()
}
