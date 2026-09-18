package com.prayersilent.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

class RestoreModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (nm.isNotificationPolicyAccessGranted) {
            try {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } catch (e: SecurityException) {
                // Nothing more we can do if access was revoked.
            }
        }

        NotificationHelper.showToggleNotification(context, prayerName = "", silencing = false)
    }
}
