package com.wheeliebin.newport

import android.content.Context

/**
 * Small SharedPreferences wrapper for the two bits of state this app needs:
 * the household's UPRN, and the date we last sent a "bins out" notification for
 * (so a background check that runs several times a day doesn't notify repeatedly).
 */
class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wheelie_bin_prefs", Context.MODE_PRIVATE)

    var uprn: String
        get() = prefs.getString(KEY_UPRN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UPRN, value.trim()).apply()

    var lastNotifiedForDate: String
        get() = prefs.getString(KEY_LAST_NOTIFIED, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_NOTIFIED, value).apply()

    companion object {
        private const val KEY_UPRN = "uprn"
        private const val KEY_LAST_NOTIFIED = "last_notified_for_date"
    }
}
