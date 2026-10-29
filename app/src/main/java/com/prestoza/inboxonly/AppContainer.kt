// AppContainer: manual dependency injection. It creates the app's long-lived objects once and hands
// them out. Android components (tile, accessibility service, receivers, activity) can't take
// constructor arguments, so they fetch this from the Application object instead.
package com.prestoza.inboxonly

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.prestoza.inboxonly.data.CounterStore
import com.prestoza.inboxonly.data.FocusStateStore
import com.prestoza.inboxonly.data.SettingsRepository
import com.prestoza.inboxonly.data.SystemTimeSource
import com.prestoza.inboxonly.data.TimeSource
import com.prestoza.inboxonly.service.AndroidAlarmScheduler
import com.prestoza.inboxonly.service.FocusCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Each preferencesDataStore delegate owns one file in the app's private storage. DataStore requires
// exactly one instance per file per process, which top-level delegates guarantee.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val Context.focusStateDataStore by preferencesDataStore(name = "focus_state")
private val Context.counterDataStore by preferencesDataStore(name = "counter")

/** Holds the app's singletons. Created once in [InboxOnlyApp]. */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** Lives as long as the process. Work launched here outlives any single screen or service call. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeSource: TimeSource = SystemTimeSource
    val settingsRepository = SettingsRepository(appContext.settingsDataStore)
    val focusStateStore = FocusStateStore(appContext.focusStateDataStore)
    val counterStore = CounterStore(appContext.counterDataStore)

    val focusCoordinator =
        FocusCoordinator(
            settingsRepository = settingsRepository,
            focusStateStore = focusStateStore,
            counterStore = counterStore,
            timeSource = timeSource,
            alarmScheduler = AndroidAlarmScheduler(appContext),
        )
}
