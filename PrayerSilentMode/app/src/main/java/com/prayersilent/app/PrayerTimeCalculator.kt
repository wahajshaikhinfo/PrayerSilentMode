package com.prayersilent.app

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Computes the five daily prayer times for a given date + location using the standard
 * solar-position / hour-angle method used by most prayer-time calculators.
 *
 * All returned times are decimal hours (e.g. 5.5 = 05:30) in the *local* time of the
 * supplied [java.time.ZoneId], already adjusted for that zone's UTC offset (including DST)
 * on the given date.
 */
object PrayerTimeCalculator {

    data class PrayerTimes(
        val fajr: Double,
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val maghrib: Double,
        val isha: Double
    )

    /**
     * Angle conventions used by various regional authorities.
     * fajrAngle / ishaAngle are the sun's angle below the horizon (degrees) that define
     * the start of Fajr and Isha. Some methods (Umm al-Qura, Gulf) instead define Isha
     * as a fixed number of minutes after Maghrib.
     */
    enum class Method(
        val fajrAngle: Double,
        val ishaAngle: Double,
        val ishaIsMinutesAfterMaghrib: Boolean = false,
        val ishaMinutes: Int = 0
    ) {
        MWL(18.0, 17.0),                       // Muslim World League
        ISNA(15.0, 15.0),                      // Islamic Society of North America
        EGYPT(19.5, 17.5),                     // Egyptian General Authority of Survey
        KARACHI(18.0, 18.0),                   // University of Islamic Sciences, Karachi
        UMM_AL_QURA(18.5, 0.0, true, 90),      // Umm al-Qura University, Makkah
        GULF(19.5, 0.0, true, 90)              // Gulf region convention
    }

    /** Juristic method used to define the length of shadow that marks Asr. */
    enum class AsrMethod(val shadowFactor: Int) {
        STANDARD(1), // Shafi'i, Maliki, Hanbali
        HANAFI(2)
    }

    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
    private fun arcsinD(x: Double) = Math.toDegrees(asin(x.coerceIn(-1.0, 1.0)))
    private fun arccosD(x: Double) = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun arccotD(x: Double) = Math.toDegrees(atan(1.0 / x))

    private fun fixAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        return a
    }

    private fun fixHour(hour: Double): Double {
        var h = hour % 24.0
        if (h < 0) h += 24.0
        return h
    }

    /** Julian date for a Gregorian calendar date at 0h UT. */
    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Sun's declination (degrees) and equation of time (hours) for a Julian date. */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)   // mean anomaly
        val q = fixAngle(280.459 + 0.98564736 * d)   // mean longitude
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g)) // apparent ecliptic longitude
        val e = 23.439 - 0.00000036 * d              // obliquity of the ecliptic

        val ra = fixHour(Math.toDegrees(atan2(cosD(e) * sinD(l), cosD(l))) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = arcsinD(sinD(e) * sinD(l))
        return Pair(decl, eqt)
    }

    /**
     * Hour angle (hours) at which the sun reaches [altitude] degrees above the horizon
     * (negative altitude = below horizon, e.g. -18 for astronomical twilight).
     * Returns NaN if the sun never reaches that altitude on this day at this latitude
     * (only relevant near the poles).
     */
    private fun hourAngle(altitude: Double, decl: Double, lat: Double): Double {
        val term = (sinD(altitude) - sinD(lat) * sinD(decl)) / (cosD(lat) * cosD(decl))
        if (term < -1.0 || term > 1.0) return Double.NaN
        return arccosD(term) / 15.0
    }

    fun calculate(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        method: Method,
        asrMethod: AsrMethod
    ): PrayerTimes {
        val jd = julianDate(year, month, day) - longitude / (15.0 * 24.0)
        val (decl, eqt) = sunPosition(jd)

        val tzOffsetHours = zone.rules
            .getOffset(LocalDateTime.of(year, month, day, 12, 0))
            .totalSeconds / 3600.0

        val dhuhr = fixHour(12.0 - eqt - longitude / 15.0 + tzOffsetHours)

        val fajrH = hourAngle(-method.fajrAngle, decl, latitude)
        val sunriseH = hourAngle(-0.833, decl, latitude)
        val asrAltitude = arccotD(asrMethod.shadowFactor + tanD(abs(latitude - decl)))
        val asrH = hourAngle(asrAltitude, decl, latitude)
        val maghribH = sunriseH
        val ishaH = if (method.ishaIsMinutesAfterMaghrib) Double.NaN
                    else hourAngle(-method.ishaAngle, decl, latitude)

        val fajr = dhuhr - fajrH
        val sunrise = dhuhr - sunriseH
        val asr = dhuhr + asrH
        val maghrib = dhuhr + maghribH
        val isha = if (method.ishaIsMinutesAfterMaghrib) maghrib + method.ishaMinutes / 60.0
                   else dhuhr + ishaH

        return PrayerTimes(fajr, sunrise, dhuhr, asr, maghrib, isha)
    }
}
