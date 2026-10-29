// PermissionStatus: checks the three things Inbox Only needs from the system, and builds the intents
// that open the right system screen to fix each one. Shown in onboarding and the settings footer.
package com.prestoza.inboxonly.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/** A snapshot of the app's system permissions. */
data class PermissionStatus(
    val accessibilityEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val exactAlarmsAllowed: Boolean,
) {
    /** True when everything Focus needs is in place. */
    val allGood: Boolean get() = accessibilityEnabled && batteryOptimizationIgnored && exactAlarmsAllowed

    companion object {
        /** Reads the current status. Cheap; call it whenever a screen resumes. */
        fun read(context: Context): PermissionStatus {
            val power = context.getSystemService(PowerManager::class.java)
            val alarms = context.getSystemService(AlarmManager::class.java)
            return PermissionStatus(
                accessibilityEnabled = AccessibilityStatus.isServiceEnabled(context),
                batteryOptimizationIgnored = power.isIgnoringBatteryOptimizations(context.packageName),
                // USE_EXACT_ALARM (13+) can't be revoked and 10/11 need nothing; only 12/12L can say no.
                exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms(),
            )
        }
    }
}

/** Intents that open the system screen for each fix. All need FLAG_ACTIVITY_NEW_TASK only from non-activities. */
object PermissionIntents {
    /** Settings → Accessibility, where the user switches our service on. */
    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** This app's App info page; its ⋮ menu has "Allow restricted settings" for sideloaded apps. */
    fun appInfo(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /**
     * The system dialog asking to exempt us from battery optimization. Google Play limits which apps may
     * use this direct prompt; a sideloaded personal blocker is exactly the case it exists for.
     */
    @SuppressLint("BatteryLife")
    fun batteryOptimizationRequest(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    /** Fallback: the full battery optimization list. */
    fun batteryOptimizationList(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Android 12/12L only: the "Alarms & reminders" permission page. */
    fun exactAlarmSettings(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
        } else {
            null
        }
}
