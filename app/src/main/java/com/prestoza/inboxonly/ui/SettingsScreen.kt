// SettingsScreen: the app's single settings screen, in Jetpack Compose (Android's declarative UI
// toolkit, similar in spirit to SwiftUI). It's stateless: it draws the Settings it's given and reports
// changes through callbacks. MainActivity owns loading and saving.
package com.prestoza.inboxonly.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.prestoza.inboxonly.domain.AppBlocking
import com.prestoza.inboxonly.domain.BlockCatalog
import com.prestoza.inboxonly.domain.BreakLength
import com.prestoza.inboxonly.domain.ReelsFromChats
import com.prestoza.inboxonly.domain.Settings
import com.prestoza.inboxonly.domain.TargetApp
import com.prestoza.inboxonly.domain.TileDuration
import com.prestoza.inboxonly.service.PermissionStatus
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The system screens the footer's "Fix" buttons can open. */
enum class PermissionFix { ACCESSIBILITY, BATTERY, EXACT_ALARMS }

/** Everything on the settings screen, top to bottom as in the spec. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    permissions: PermissionStatus,
    rulesArePlaceholder: Boolean,
    use24Hour: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onFix: (PermissionFix) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Inbox Only") }) }) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.padding(padding)) {
            item { SectionTitle("Block list") }
            item {
                Text(
                    "Chats and DMs are always allowed.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            BlockCatalog.apps.forEach { app ->
                item(key = app.id) { AppBlockRow(app, settings.blockingFor(app.id), onUpdate) }
            }

            item { SectionDivider() }
            item { ScheduleSection(settings, use24Hour, onUpdate) }

            item { SectionDivider() }
            item {
                ChoiceSection(
                    title = "Tile duration",
                    options = TileDuration.entries,
                    selected = settings.tileDuration,
                    label = ::tileDurationLabel,
                    onSelect = { choice -> onUpdate { it.copy(tileDuration = choice) } },
                )
            }
            item {
                ChoiceSection(
                    title = "Break length",
                    options = BreakLength.entries,
                    selected = settings.breakLength,
                    label = { "${it.duration.toMinutes()} min" },
                    onSelect = { choice -> onUpdate { it.copy(breakLength = choice) } },
                )
            }
            item {
                ChoiceSection(
                    title = "Reels from chats",
                    options = ReelsFromChats.entries,
                    selected = settings.reelsFromChats,
                    label = { if (it == ReelsFromChats.ALLOW_ONE) "Allow one" else "Block" },
                    onSelect = { choice -> onUpdate { it.copy(reelsFromChats = choice) } },
                )
            }
            item {
                SwitchRow(
                    title = "Strict mode",
                    subtitle = "During the schedule, the tile can't start a break.",
                    checked = settings.strictMode,
                    onCheckedChange = { on -> onUpdate { it.copy(strictMode = on) } },
                )
            }

            item { SectionDivider() }
            item { PermissionFooter(permissions, rulesArePlaceholder, onFix) }
        }
    }
}

@Composable
private fun AppBlockRow(
    app: TargetApp,
    blocking: AppBlocking,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun update(change: (AppBlocking) -> AppBlocking) =
        onUpdate {
            it.copy(
                apps =
                    it.apps + (app.id to change(it.blockingFor(app.id))),
            )
        }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                val blockedCount = app.surfaces.count { it.id !in blocking.disabledSurfaces }
                Text(
                    if (blocking.appEnabled) "$blockedCount of ${app.surfaces.size} blocked" else "Not blocked",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = blocking.appEnabled, onCheckedChange = { on -> update { it.copy(appEnabled = on) } })
        }
        if (expanded) {
            app.surfaces.forEach { surface ->
                val checked = surface.id !in blocking.disabledSurfaces
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        enabled = blocking.appEnabled,
                        onCheckedChange = { block ->
                            update {
                                val disabled =
                                    if (block) {
                                        it.disabledSurfaces - surface.id
                                    } else {
                                        it.disabledSurfaces +
                                            surface.id
                                    }
                                it.copy(disabledSurfaces = disabled)
                            }
                        },
                    )
                    Text(surface.label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSection(
    settings: Settings,
    use24Hour: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    val schedule = settings.schedule
    var editing by remember { mutableStateOf<ScheduleEdge?>(null) }

    SwitchRow(
        title = "Schedule",
        subtitle = "Turn Focus on automatically.",
        checked = schedule.enabled,
        onCheckedChange = { on -> onUpdate { it.copy(schedule = it.schedule.copy(enabled = on)) } },
    )
    if (!schedule.enabled) return

    Row(Modifier.padding(horizontal = 16.dp)) {
        TextButton(
            onClick = { editing = ScheduleEdge.START },
        ) { Text("Start ${formatTime(schedule.start, use24Hour)}") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { editing = ScheduleEdge.END }) { Text("End ${formatTime(schedule.end, use24Hour)}") }
    }
    // Read through Compose so day names update if the phone's language changes while the screen is open.
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in schedule.days,
                onClick = {
                    onUpdate {
                        val days = if (day in it.schedule.days) it.schedule.days - day else it.schedule.days + day
                        it.copy(schedule = it.schedule.copy(days = days))
                    }
                },
                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
            )
        }
    }
    val problem =
        when {
            schedule.start == schedule.end -> "Start and end can't be the same time."
            schedule.days.isEmpty() -> "Pick at least one day."
            schedule.end < schedule.start -> "Runs overnight, ending the next morning."
            else -> null
        }
    problem?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    editing?.let { edge ->
        val initial = if (edge == ScheduleEdge.START) schedule.start else schedule.end
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = use24Hour)
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(state.hour, state.minute)
                    onUpdate {
                        val updated =
                            if (edge ==
                                ScheduleEdge.START
                            ) {
                                it.schedule.copy(start = time)
                            } else {
                                it.schedule.copy(end = time)
                            }
                        it.copy(schedule = updated)
                    }
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

private enum class ScheduleEdge { START, END }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SectionTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun PermissionFooter(
    permissions: PermissionStatus,
    rulesArePlaceholder: Boolean,
    onFix: (PermissionFix) -> Unit,
) {
    SectionTitle("Permissions")
    if (permissions.accessibilityEnabled && !permissions.accessibilityRunning) {
        // Seen after app updates: switched on in Settings, but Android isn't running it.
        PermissionRow("Blocker stopped: switch it off and on", granted = false) { onFix(PermissionFix.ACCESSIBILITY) }
    } else {
        PermissionRow("Accessibility service", permissions.accessibilityEnabled) { onFix(PermissionFix.ACCESSIBILITY) }
    }
    PermissionRow("Battery optimization off", permissions.batteryOptimizationIgnored) { onFix(PermissionFix.BATTERY) }
    PermissionRow("Exact alarms", permissions.exactAlarmsAllowed) { onFix(PermissionFix.EXACT_ALARMS) }
    if (rulesArePlaceholder) {
        Text(
            "Blocking rules are placeholders, so nothing is blocked yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Text((if (granted) "✓ " else "✗ ") + label, modifier = Modifier.weight(1f))
        if (!granted) TextButton(onClick = onFix) { Text("Fix") }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() = HorizontalDivider(Modifier.padding(top = 8.dp))

private fun tileDurationLabel(duration: TileDuration): String =
    when (duration) {
        TileDuration.THIRTY_MINUTES -> "30m"
        TileDuration.ONE_HOUR -> "1h"
        TileDuration.TWO_HOURS -> "2h"
        TileDuration.UNTIL_OFF -> "Until off"
    }

private fun formatTime(
    time: LocalTime,
    use24Hour: Boolean,
): String = time.format(DateTimeFormatter.ofPattern(if (use24Hour) "H:mm" else "h:mm a", Locale.US))
