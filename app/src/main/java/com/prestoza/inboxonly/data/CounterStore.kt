// CounterStore: persists the daily "blocked" counter shown in the tile subtitle. The reset at local
// midnight happens on read (see DailyCount), so no alarm or background work is needed for it.
package com.prestoza.inboxonly.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prestoza.inboxonly.domain.DailyCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Stores how many blocks happened today. */
class CounterStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val stored = dataStore.data.map(::decode)

    /** Today's count; zero if nothing was blocked yet today. */
    suspend fun countOn(today: LocalDate): Int = stored.first().countOn(today)

    /** Records one block event on [today]. */
    suspend fun increment(today: LocalDate) {
        dataStore.edit { prefs ->
            val next = decode(prefs).incrementedOn(today)
            prefs[DATE] = next.date.toString()
            prefs[COUNT] = next.count
        }
    }

    private companion object {
        val DATE = stringPreferencesKey("date")
        val COUNT = intPreferencesKey("count")

        fun decode(prefs: Preferences): DailyCount {
            val date = prefs[DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.MIN
            return DailyCount(date, prefs[COUNT] ?: 0)
        }
    }
}
