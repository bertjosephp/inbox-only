// SettingsRepository: loads and saves Settings with Jetpack DataStore.
// DataStore is Android's modern replacement for SharedPreferences: an async key-value file whose
// contents you read as a Flow (a stream that emits the new value whenever it changes).
package com.prestoza.inboxonly.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.prestoza.inboxonly.domain.AppBlocking
import com.prestoza.inboxonly.domain.BlockCatalog
import com.prestoza.inboxonly.domain.Schedule
import com.prestoza.inboxonly.domain.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime

/** Reads and writes [Settings]. Missing or unreadable values fall back to the defaults in [Settings]. */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    /** The current settings, re-emitted whenever they change. */
    val settings: Flow<Settings> = dataStore.data.map(::decode)

    /** Applies [transform] to the current settings and saves the result atomically. */
    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> encode(transform(decode(prefs)), prefs) }
    }

    /** Converts between [Settings] and DataStore keys. Internal so unit tests can call it directly. */
    internal companion object {
        private val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        private val SCHEDULE_START = intPreferencesKey("schedule_start_minute")
        private val SCHEDULE_END = intPreferencesKey("schedule_end_minute")
        private val SCHEDULE_DAYS = stringSetPreferencesKey("schedule_days")
        private val TILE_DURATION = stringPreferencesKey("tile_duration")
        private val BREAK_LENGTH = stringPreferencesKey("break_length")
        private val REELS_FROM_CHATS = stringPreferencesKey("reels_from_chats")
        private val STRICT_MODE = booleanPreferencesKey("strict_mode")

        private fun appEnabledKey(appId: String) = booleanPreferencesKey("app_${appId}_enabled")

        private fun disabledSurfacesKey(appId: String) = stringSetPreferencesKey("app_${appId}_disabled_surfaces")

        fun decode(prefs: Preferences): Settings {
            val defaults = Settings()
            val apps =
                BlockCatalog.apps.associate { app ->
                    app.id to
                        AppBlocking(
                            appEnabled = prefs[appEnabledKey(app.id)] ?: true,
                            disabledSurfaces = prefs[disabledSurfacesKey(app.id)].orEmpty(),
                        )
                }
            val schedule =
                Schedule(
                    enabled = prefs[SCHEDULE_ENABLED] ?: defaults.schedule.enabled,
                    start = prefs[SCHEDULE_START]?.let(::minuteToTime) ?: defaults.schedule.start,
                    end = prefs[SCHEDULE_END]?.let(::minuteToTime) ?: defaults.schedule.end,
                    days =
                        prefs[SCHEDULE_DAYS]
                            ?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                            ?.toSet()
                            ?: defaults.schedule.days,
                )
            return Settings(
                apps = apps,
                schedule = schedule,
                tileDuration = enumOrDefault(prefs[TILE_DURATION], defaults.tileDuration),
                breakLength = enumOrDefault(prefs[BREAK_LENGTH], defaults.breakLength),
                reelsFromChats = enumOrDefault(prefs[REELS_FROM_CHATS], defaults.reelsFromChats),
                strictMode = prefs[STRICT_MODE] ?: defaults.strictMode,
            )
        }

        fun encode(
            settings: Settings,
            prefs: MutablePreferences,
        ) {
            BlockCatalog.apps.forEach { app ->
                val blocking = settings.blockingFor(app.id)
                prefs[appEnabledKey(app.id)] = blocking.appEnabled
                prefs[disabledSurfacesKey(app.id)] = blocking.disabledSurfaces
            }
            prefs[SCHEDULE_ENABLED] = settings.schedule.enabled
            prefs[SCHEDULE_START] = settings.schedule.start.toSecondOfDay() / 60
            prefs[SCHEDULE_END] = settings.schedule.end.toSecondOfDay() / 60
            prefs[SCHEDULE_DAYS] =
                settings.schedule.days
                    .map { it.name }
                    .toSet()
            prefs[TILE_DURATION] = settings.tileDuration.name
            prefs[BREAK_LENGTH] = settings.breakLength.name
            prefs[REELS_FROM_CHATS] = settings.reelsFromChats.name
            prefs[STRICT_MODE] = settings.strictMode
        }

        private fun minuteToTime(minuteOfDay: Int): LocalTime? =
            if (minuteOfDay in 0 until 24 * 60) LocalTime.of(minuteOfDay / 60, minuteOfDay % 60) else null

        // Stored by name so reordering the enum never changes meaning; an unknown name means "default".
        private inline fun <reified T : Enum<T>> enumOrDefault(
            name: String?,
            default: T,
        ): T = enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
