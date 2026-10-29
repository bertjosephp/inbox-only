# Decisions

Short ADR-style entries: what we decided, and why. Newest at the bottom.

## ADR-001: AccessibilityService, not VPN/DNS blocking
**Decision:** Detect blocked screens with an AccessibilityService and respond with back/home or an intent.
**Why:** Instagram, Facebook and YouTube serve DMs and Reels from the same servers, so a network blocker
can't block one without the other. It would also break messaging. Accessibility only *observes* the UI,
never touches other apps or their servers, so there is no account-ban risk.

## ADR-002: Manual dependency injection, no Hilt
**Decision:** A small `AppContainer` built in the `Application` subclass.
**Why:** The app has a handful of objects. Hilt adds annotation processing, generated code and build
time for no real benefit at this size.

## ADR-003: Spotless + ktlint instead of the ktlint Gradle plugin
**Decision:** Run ktlint through Spotless with explicit file globs.
**Why:** AGP 9 compiles Kotlin itself ("built-in Kotlin") without the `kotlin-android` plugin, which
the standalone ktlint plugin uses to discover source sets. Spotless just targets `**/*.kt`.

## ADR-004: Build with JDK 21 locally, target Java 17
**Decision:** Bytecode targets Java 17 (`compileOptions`); CI runs on Temurin 17; any JDK ≥ 17 builds it.
**Why:** The spec asks for JDK 17. The owner's Mac has JDK 21, which AGP fully supports. Targeting 17
keeps the two identical in output.

## ADR-005: Focus state is reconciled from absolute times, with one alarm at a time
**Decision:** The stored `FocusState` holds absolute `Instant`s. Every reader passes it through
`FocusController.reconcile(state, settings, now, zone)`, and only the single earliest future edge
(timer end, break end, window end, next window start) is registered as an alarm.
**Why:** Alarms can be late (Doze), lost (reboot, force-stop) or made stale (time zone change, schedule
edit). Recomputing from the clock means a missed alarm can never leave Focus in the wrong state. One
alarm is simpler to reason about than a set of them.

## ADR-006: A schedule window absorbs manual Focus
**Decision:** If manual Focus is running when a schedule window starts, the state becomes `Scheduled`
and ends with the window, even if the manual timer would have run longer.
**Why:** It keeps one invariant: inside a window the state is always Scheduled or Break, so strict mode
and breaks behave the same however Focus got turned on. The edge case (manual timer longer than the
window, and also running into it) is rare and harmless.

## ADR-007: Schedule days are start days
**Decision:** A 22:00–06:00 window on Friday runs until Saturday 06:00 even if Saturday isn't selected.
A window whose start equals its end is invalid (no window).
**Why:** That's how alarm clocks and calendars treat overnight events. DST gaps shift times forward and
repeated times use the first occurrence (java.time defaults).

## ADR-008: Exact alarms via USE_EXACT_ALARM
**Decision:** Declare `USE_EXACT_ALARM` (Android 13+) and `SCHEDULE_EXACT_ALARM` with
`maxSdkVersion="32"` (Android 12/12L only); schedule with `setExactAndAllowWhileIdle`.
**Why:** Doze delays inexact alarms by many minutes, which would make breaks and schedule edges visibly
late. `USE_EXACT_ALARM` is auto-granted and can't be revoked by the user. Google Play only allows it for
alarm-clock and calendar apps, but this app is sideloaded and never reviewed by Play.
`SCHEDULE_EXACT_ALARM` can be revoked from settings and would need a permission prompt. Even when an
alarm is late, `reconcile()` corrects the state the next time anything reads it.

## ADR-009: Regular (non-"active") tile, rendered only while the shade is open
**Decision:** The tile doesn't use `META_DATA_ACTIVE_TILE`. Status is computed in `onStartListening`,
which the system calls whenever the shade shows the tile, and it re-renders if the stored state changes
while the shade stays open.
**Why:** An "active" tile only gets `onStartListening` when the app requests it, so a countdown subtitle
would go stale. A regular tile costs nothing while the shade is closed and needs no ticking timer.

## ADR-010: "Set up" tile instead of STATE_UNAVAILABLE
**Decision:** While the accessibility service is disabled, the tile is `STATE_INACTIVE` with the subtitle
`Set up`, and tapping it opens the app.
**Why:** The spec asked for `STATE_UNAVAILABLE` plus "tap opens onboarding", but Android doesn't deliver
taps to an unavailable tile, so that combination can't work.

## ADR-011: Turning the service off keeps its package filter
**Decision:** When Focus is off, `setServiceInfo()` sets `eventTypes = 0` and leaves `packageNames` as
declared in XML.
**Why:** In `AccessibilityServiceInfo`, `packageNames = null` means *every app*. "Clearing" packages
would make the service hear everything the moment event types came back. A unit test checks the XML
filter lists exactly the four packages in `rules.json`.

## ADR-012: The debug inspector listens even when Focus is off
**Decision:** In debug builds only (`InspectorLogger.ENABLED`), the service stays subscribed while
Focus is off, so screens can be captured without blocking getting in the way. Blocking itself still
requires Focus. Release builds have `ENABLED = false` and stay fully inert.
**Why:** Capturing a blocked screen with Focus on would immediately bounce out of it.

## ADR-013: Throttle content-changed events, don't debounce
**Decision:** At most one screen check per 250 ms window after a content-changed event; window-state
changes are checked immediately.
**Why:** A playing video or Reel fires content changes continuously. A trailing debounce would never
settle and never fire, which would make Reels unblockable.

## ADR-014: Block actions per surface, chosen by the owner
**Decision:** Each surface in `rules.json` lists actions tried in order, with Home as the guaranteed last
resort. Intended actions for a blocked landing screen: Instagram → DM inbox (deep link), Facebook →
Messenger (or Facebook's own inbox, if one exists on device), YouTube → close (Home), Reddit → close (Home).
**Why:** YouTube and Facebook open straight onto their feed. Pressing Back there just exits, so a
redirect is the only way to keep the useful parts reachable. The owner chose to simply close YouTube and
Reddit. Deep links are only added after they're verified on the phone.

## ADR-015: Reel identity is an in-memory hash
**Decision:** To notice a swipe to a different Reel, the service hashes the content description or text
of one rule-chosen node in the Reel viewer (e.g. the author label). It keeps only the hash, in memory.
**Why:** `TYPE_VIEW_SCROLLED` is off-limits for battery reasons, and comparing a hash is the cheapest
way to tell Reels apart. If no node is suitable, the tracker allows the opened Reel until the viewer
is left, so Reel-from-chat stays usable rather than broken.
