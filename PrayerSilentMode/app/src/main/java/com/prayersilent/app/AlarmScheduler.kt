package com.prayersilent.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduler {

    private const val REQ_FAJR = 1001
    private const val REQ_DHUHR = 1002
    private const val REQ_ASR = 1003
    private const val REQ_MAGHRIB = 1004
    private const val REQ_ISHA = 1005
    private const val REQ_DAILY_RECOMPUTE = 1006
    private const val REQ_RESTORE = 1007

    /**
     * Recomputes today's prayer times from current Prefs and (re)schedules every enabled,
     * still-upcoming prayer for today, plus a just-after-midnight alarm that repeats this
     * process tomorrow. Call this after boot, after the user saves settings, and once a day.
     */
    fun rescheduleAll(context: Context) {
        val prefs = Prefs(context)
        cancelPrayerAlarms(context)

        if (!prefs.masterEnabled) return
        if (prefs.city.isNullOrEmpty()) return

        val zone = safeZone(prefs.timezoneId)
        val today = LocalDate.now(zone)
        val method = runCatching { PrayerTimeCalculator.Method.valueOf(prefs.method) }
            .getOrDefault(PrayerTimeCalculator.Method.KARACHI)
        val asr = runCatching { PrayerTimeCalculator.AsrMethod.valueOf(prefs.asrMethod) }
            .getOrDefault(PrayerTimeCalculator.AsrMethod.HANAFI)

        val times = PrayerTimeCalculator.calculate(
            today.year, today.monthValue, today.dayOfMonth,
            prefs.latitude.toDouble(), prefs.longitude.toDouble(),
            zone, method, asr
        )

        val now = ZonedDateTime.now(zone)
        val prayers = listOf(
            Triple("fajr", times.fajr, REQ_FAJR),
            Triple("dhuhr", times.dhuhr, REQ_DHUHR),
            Triple("asr", times.asr, REQ_ASR),
            Triple("maghrib", times.maghrib, REQ_MAGHRIB),
            Triple("isha", times.isha, REQ_ISHA)
        )

        for ((name, hourDecimal, reqCode) in prayers) {
            if (!prefs.isPrayerEnabled(name)) continue
            if (hourDecimal.isNaN()) continue
            val zdt = decimalHourToZonedDateTime(today, hourDecimal, zone)
            if (zdt.isBefore(now)) continue // already passed today - tomorrow's recompute will catch it
            scheduleExact(context, zdt, reqCode, name)
        }

        scheduleDailyRecompute(context, zone)
    }

    fun scheduleRestore(context: Context, minutesFromNow: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RestoreModeReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_RESTORE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + minutesFromNow.coerceAtLeast(1) * 60_000L
        setAlarm(context, am, triggerAt, pi)
    }

    fun cancelPrayerAlarms(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (reqCode in listOf(REQ_FAJR, REQ_DHUHR, REQ_ASR, REQ_MAGHRIB, REQ_ISHA)) {
            val intent = Intent(context, SilentModeReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }

    private fun scheduleExact(context: Context, zdt: ZonedDateTime, requestCode: Int, prayerName: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SilentModeReceiver::class.java).apply {
            putExtra("prayer_name", prayerName)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, am, zdt.toInstant().toEpochMilli(), pi)
    }

    private fun scheduleDailyRecompute(context: Context, zone: ZoneId) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyRecomputeReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_DAILY_RECOMPUTE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = ZonedDateTime.now(zone)
        val next = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, 5)).atZone(zone)
        setAlarm(context, am, next.toInstant().toEpochMilli(), pi)
    }

    /** Uses an exact alarm when allowed, otherwise falls back to an inexact-but-Doze-safe one. */
    private fun setAlarm(context: Context, am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        try {
            val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canBeExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun decimalHourToZonedDateTime(date: LocalDate, hourDecimal: Double, zone: ZoneId): ZonedDateTime {
        val totalMinutes = Math.round(hourDecimal * 60.0)
        val h = ((totalMinutes / 60) % 24).toInt()
        val m = (totalMinutes % 60).toInt()
        return ZonedDateTime.of(date, LocalTime.of(h, m), zone)
    }

    private fun safeZone(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())
}
