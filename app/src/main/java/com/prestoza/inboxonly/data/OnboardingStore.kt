// OnboardingStore: remembers setup progress that Android can't tell us itself: whether the tile was
// added (there's no API to ask) and whether onboarding was finished, after which it never shows again.
package com.prestoza.inboxonly.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Onboarding progress flags. */
class OnboardingStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** True once the user moved past the battery step (Android can't report Samsung's sleep lists). */
    val batteryStepDone: Flow<Boolean> = dataStore.data.map { it[BATTERY_STEP_DONE] ?: false }

    /** True once the user added the tile (or confirmed they did). */
    val tileAdded: Flow<Boolean> = dataStore.data.map { it[TILE_ADDED] ?: false }

    /** True once onboarding was completed; from then on the settings screen shows instead. */
    val completed: Flow<Boolean> = dataStore.data.map { it[COMPLETED] ?: false }

    /** Records that the battery step is finished. */
    suspend fun markBatteryStepDone() {
        dataStore.edit { it[BATTERY_STEP_DONE] = true }
    }

    /** Records that the tile is in Quick Settings. */
    suspend fun markTileAdded() {
        dataStore.edit { it[TILE_ADDED] = true }
    }

    /** Records that onboarding is done. */
    suspend fun markCompleted() {
        dataStore.edit { it[COMPLETED] = true }
    }

    private companion object {
        val BATTERY_STEP_DONE = booleanPreferencesKey("battery_step_done")
        val TILE_ADDED = booleanPreferencesKey("tile_added")
        val COMPLETED = booleanPreferencesKey("completed")
    }
}
