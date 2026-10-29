// BootReceiver: re-registers the Focus alarm whenever Android may have dropped it or made it wrong.
// Alarms don't survive a reboot or an app update, and a clock or time zone change moves "18:00" to a
// different instant. Each case simply re-runs FocusCoordinator.refresh(), which recomputes everything
// from the stored state and the clock.
package com.prestoza.inboxonly.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prestoza.inboxonly.appContainer
import kotlinx.coroutines.launch

/** Handles boot, clock, time zone, app update and exact-alarm permission broadcasts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        val container = context.appContainer
        container.applicationScope.launch {
            try {
                container.focusCoordinator.refresh()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET": the user changed the clock
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED, // this app was updated
                // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, spelled out because the
                // constant only exists from Android 12. Older versions simply never send it.
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )
    }
}
