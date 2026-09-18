package com.prayersilent.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

class SilentModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (!prefs.masterEnabled) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (nm.isNotificationPolicyAccessGranted) {
            try {
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (e: SecurityException) {
                // Access was revoked between the check and the call - nothing more we can do here.
            }
        }

        val prayerName = intent.getStringExtra("prayer_name") ?: "prayer"
        NotificationHelper.showToggleNotification(context, prayerName, silencing = true)

        // Restore the ringer after the user-configured duration.
        AlarmScheduler.scheduleRestore(context, prefs.durationMinutes)
    }
}
