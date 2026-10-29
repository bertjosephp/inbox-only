package com.prestoza.inboxonly.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prestoza.inboxonly.domain.AppBlocking
import com.prestoza.inboxonly.domain.BreakLength
import com.prestoza.inboxonly.domain.FocusState
import com.prestoza.inboxonly.domain.ReelsFromChats
import com.prestoza.inboxonly.domain.Schedule
import com.prestoza.inboxonly.domain.Settings
import com.prestoza.inboxonly.domain.TileDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class PersistenceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val customSettings =
        Settings(
            apps =
                mapOf(
                    "instagram" to AppBlocking(appEnabled = true, disabledSurfaces = setOf("explore")),
                    "reddit" to AppBlocking(appEnabled = false),
                ),
            schedule =
                Schedule(
                    enabled = true,
                    start = LocalTime.of(22, 30),
                    end = LocalTime.of(6, 15),
                    days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                ),
            tileDuration = TileDuration.UNTIL_OFF,
            breakLength = BreakLength.FIFTEEN_MINUTES,
            reelsFromChats = ReelsFromChats.BLOCK,
            strictMode = true,
        )

    // ---- Pure encode/decode ----

    @Test
    fun `empty preferences decode to default settings`() {
        val decoded = SettingsRepository.decode(mutablePreferencesOf())
        assertEquals(Settings().copy(apps = decoded.apps), decoded)
        decoded.apps.values.forEach { assertEquals(AppBlocking(), it) }
    }

    @Test
    fun `settings round-trip through preferences`() {
        val prefs = mutablePreferencesOf()
        SettingsRepository.encode(customSettings, prefs)
        val decoded = SettingsRepository.decode(prefs)
        assertEquals(customSettings.schedule, decoded.schedule)
        assertEquals(customSettings.tileDuration, decoded.tileDuration)
        assertEquals(customSettings.breakLength, decoded.breakLength)
        assertEquals(customSettings.reelsFromChats, decoded.reelsFromChats)
        assertEquals(customSettings.strictMode, decoded.strictMode)
        assertEquals(AppBlocking(true, setOf("explore")), decoded.blockingFor("instagram"))
        assertEquals(AppBlocking(false), decoded.blockingFor("reddit"))
        assertEquals(AppBlocking(), decoded.blockingFor("youtube"))
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("tile_duration") to "FOREVER")
        assertEquals(Settings().tileDuration, SettingsRepository.decode(prefs).tileDuration)
    }

    @Test
    fun `every focus state round-trips`() {
        val t1 = Instant.parse("2026-10-29T18:00:00Z")
        val t2 = Instant.parse("2026-10-29T12:05:00Z")
        listOf(
            FocusState.Off,
            FocusState.Manual(null),
            FocusState.Manual(t1),
            FocusState.Scheduled(t1),
            FocusState.Break(t1, t2),
        ).forEach { state ->
            val prefs = mutablePreferencesOf()
            FocusStateStore.encode(state, prefs)
            assertEquals(state, FocusStateStore.decode(prefs))
        }
    }

    @Test
    fun `corrupt focus state fails open to off`() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("kind") to "break")
        assertEquals(FocusState.Off, FocusStateStore.decode(prefs))
    }

    // ---- Real DataStore files: survives "process death" ----

    // Opens a DataStore on [file] whose lifetime is [job]; cancelling the job is like the process dying.
    private fun TestScope.openStore(
        file: File,
        job: Job,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job),
            produceFile = { file },
        )

    @Test
    fun `focus state survives process death`() =
        runTest {
            val file = File(tmp.root, "focus_state.preferences_pb")
            val state = FocusState.Break(Instant.parse("2026-10-29T18:00:00Z"), Instant.parse("2026-10-29T12:05:00Z"))

            val firstProcess = Job()
            FocusStateStore(openStore(file, firstProcess)).save(state)
            firstProcess.cancelAndJoin()

            val secondProcess = Job()
            assertEquals(state, FocusStateStore(openStore(file, secondProcess)).current())
            secondProcess.cancelAndJoin()
        }

    @Test
    fun `settings survive process death`() =
        runTest {
            val file = File(tmp.root, "settings.preferences_pb")

            val firstProcess = Job()
            SettingsRepository(openStore(file, firstProcess)).update { customSettings }
            firstProcess.cancelAndJoin()

            val secondProcess = Job()
            val reloaded = SettingsRepository(openStore(file, secondProcess)).settings.first()
            assertEquals(customSettings.schedule, reloaded.schedule)
            assertEquals(customSettings.strictMode, reloaded.strictMode)
            secondProcess.cancelAndJoin()
        }

    @Test
    fun `counter increments and resets on a new day`() =
        runTest {
            val job = Job()
            val counter = CounterStore(openStore(File(tmp.root, "counter.preferences_pb"), job))
            val today = LocalDate.of(2026, 10, 29)

            repeat(3) { counter.increment(today) }
            assertEquals(3, counter.countOn(today))
            assertEquals(0, counter.countOn(today.plusDays(1)))

            counter.increment(today.plusDays(1))
            assertEquals(1, counter.countOn(today.plusDays(1)))
            job.cancelAndJoin()
        }
}
