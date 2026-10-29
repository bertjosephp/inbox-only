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
