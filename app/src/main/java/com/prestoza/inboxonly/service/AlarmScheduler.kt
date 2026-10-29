// AlarmScheduler: wakes the app at the next Focus edge (timer end, break end, window start/end).
// AlarmManager is Android's system timer service; an alarm fires even if our process is dead, by
// delivering a broadcast to AlarmReceiver. We never keep a timer running ourselves.
package com.prestoza.inboxonly.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/** Registers (or clears) the single pending Focus alarm. An interface so the coordinator is testable. */
fun interface AlarmScheduler {
    /** Fire [AlarmReceiver] at [at], replacing any earlier alarm. Null cancels it. */
    fun scheduleAt(at: Instant?)
}

/**
 * Real [AlarmScheduler] backed by exact alarms.
 *
 * "Exact" matters: Doze (Android's deep-sleep battery saver) delays normal alarms by minutes or more,
 * which would make a 5-minute break or an 18:00 schedule end visibly late. `setExactAndAllowWhileIdle`
 * fires on time even in Doze.
 *
 * Permission choice: we declare `USE_EXACT_ALARM` (Android 13+). It's granted automatically and the
 * user can't revoke it. Google Play restricts it to alarm/calendar apps, but this app is sideloaded
 * and never goes through Play review. `SCHEDULE_EXACT_ALARM` is declared only for Android 12/12L
 * (maxSdkVersion 32), where `USE_EXACT_ALARM` doesn't exist yet. Android 10/11 need no permission.
 */
class AndroidAlarmScheduler(
    context: Context,
) : AlarmScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun scheduleAt(at: Instant?) {
        val pendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(appContext, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        if (at == null) {
            alarmManager.cancel(pendingIntent)
            return
        }
        // RTC_WAKEUP = wall-clock time, and wake the CPU if asleep. If the user changes the clock we
        // get TIME_SET and re-register (see BootReceiver), so wall-clock is the right base.
        val trigger = at.toEpochMilli()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        } else {
            // Only reachable on Android 12/12L if the user revoked the permission: a late edge is still
            // corrected the next time anything reads Focus state (see FocusController.reconcile).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
