package com.prayersilent.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires just after midnight each day so tomorrow's prayer times get scheduled. */
class DailyRecomputeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
