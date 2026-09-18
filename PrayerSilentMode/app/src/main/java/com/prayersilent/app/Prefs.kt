package com.prayersilent.app

import android.content.Context
import android.content.SharedPreferences

data class CityInfo(
    val country: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val timezoneId: String
)

/** Thin wrapper around SharedPreferences holding all user-configurable settings. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("prayer_silent_prefs", Context.MODE_PRIVATE)

    var masterEnabled: Boolean
        get() = sp.getBoolean(KEY_MASTER, true)
        set(v) = sp.edit().putBoolean(KEY_MASTER, v).apply()

    var country: String?
        get() = sp.getString(KEY_COUNTRY, null)
        set(v) = sp.edit().putString(KEY_COUNTRY, v).apply()

    var city: String?
        get() = sp.getString(KEY_CITY, null)
        set(v) = sp.edit().putString(KEY_CITY, v).apply()

    var latitude: Float
        get() = sp.getFloat(KEY_LAT, 0f)
        set(v) = sp.edit().putFloat(KEY_LAT, v).apply()

    var longitude: Float
        get() = sp.getFloat(KEY_LNG, 0f)
        set(v) = sp.edit().putFloat(KEY_LNG, v).apply()

    var timezoneId: String
        get() = sp.getString(KEY_TZ, "UTC") ?: "UTC"
        set(v) = sp.edit().putString(KEY_TZ, v).apply()

    /** Name of a PrayerTimeCalculator.Method enum constant. */
    var method: String
        get() = sp.getString(KEY_METHOD, "KARACHI") ?: "KARACHI"
        set(v) = sp.edit().putString(KEY_METHOD, v).apply()

    /** Name of a PrayerTimeCalculator.AsrMethod enum constant. */
    var asrMethod: String
        get() = sp.getString(KEY_ASR, "HANAFI") ?: "HANAFI"
        set(v) = sp.edit().putString(KEY_ASR, v).apply()

    var durationMinutes: Int
        get() = sp.getInt(KEY_DURATION, 15)
        set(v) = sp.edit().putInt(KEY_DURATION, v).apply()

    fun isPrayerEnabled(name: String): Boolean = sp.getBoolean("enabled_$name", true)

    fun setPrayerEnabled(name: String, enabled: Boolean) {
        sp.edit().putBoolean("enabled_$name", enabled).apply()
    }

    companion object {
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_COUNTRY = "country"
        private const val KEY_CITY = "city"
        private const val KEY_LAT = "latitude"
        private const val KEY_LNG = "longitude"
        private const val KEY_TZ = "timezone_id"
        private const val KEY_METHOD = "method"
        private const val KEY_ASR = "asr_method"
        private const val KEY_DURATION = "duration_minutes"
    }
}
