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
