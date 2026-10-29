// FocusStateStore: persists the single source of truth for Focus (FocusState) in DataStore, so it
// survives process death and reboots. The tile, the accessibility service and the UI all observe it.
package com.prestoza.inboxonly.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prestoza.inboxonly.domain.FocusState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Reads and writes the raw stored [FocusState]. Callers resolve it with `FocusController.reconcile`. */
class FocusStateStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** The stored state, re-emitted on every change. */
    val state: Flow<FocusState> = dataStore.data.map(::decode)

    /** The stored state right now. */
    suspend fun current(): FocusState = state.first()

    /** Saves [newState], replacing whatever was stored. */
    suspend fun save(newState: FocusState) {
        dataStore.edit { prefs -> encode(newState, prefs) }
    }

    /** Converts between [FocusState] and DataStore keys. Internal so unit tests can call it directly. */
    internal companion object {
        private val KIND = stringPreferencesKey("kind")
        private val UNTIL = longPreferencesKey("until_epoch_ms")
        private val RESUME_AT = longPreferencesKey("resume_at_epoch_ms")

        private const val OFF = "off"
        private const val MANUAL = "manual"
        private const val SCHEDULED = "scheduled"
        private const val BREAK = "break"

        // Anything unreadable decodes to Off: failing open means the phone behaves normally.
        fun decode(prefs: Preferences): FocusState {
            val until = prefs[UNTIL]?.let(Instant::ofEpochMilli)
            val resumeAt = prefs[RESUME_AT]?.let(Instant::ofEpochMilli)
            return when (prefs[KIND]) {
                MANUAL -> FocusState.Manual(until)
                SCHEDULED -> until?.let { FocusState.Scheduled(it) }
                BREAK -> if (until != null && resumeAt != null) FocusState.Break(until, resumeAt) else null
                else -> null
            } ?: FocusState.Off
        }

        fun encode(
            state: FocusState,
            prefs: MutablePreferences,
        ) {
            prefs.remove(UNTIL)
            prefs.remove(RESUME_AT)
            when (state) {
                FocusState.Off -> {
                    prefs[KIND] = OFF
                }

                is FocusState.Manual -> {
                    prefs[KIND] = MANUAL
                    state.until?.let { prefs[UNTIL] = it.toEpochMilli() }
                }

                is FocusState.Scheduled -> {
                    prefs[KIND] = SCHEDULED
                    prefs[UNTIL] = state.until.toEpochMilli()
                }

                is FocusState.Break -> {
                    prefs[KIND] = BREAK
                    prefs[UNTIL] = state.until.toEpochMilli()
                    prefs[RESUME_AT] = state.resumeAt.toEpochMilli()
                }
            }
        }
    }
}
