# CLAUDE.md — working notes for Claude Code sessions

Inbox Only is a personal Android app (sideloaded, not on the Play Store): a Quick Settings tile toggles
"Focus", and an AccessibilityService blocks doomscroll surfaces (Reels, feeds, Shorts, Reddit browsing)
in Instagram, Facebook, YouTube and Reddit while keeping DMs and messaging fully usable.
The owner knows Swift/Java but is new to Android: explain Android concepts briefly in comments.

## Hard rules
- **Never invent selectors.** View IDs, window class names and content descriptions for target apps come
  only from real device captures (`scripts/inspect.sh`). If you don't have real data, leave the rule as a
  placeholder (`"placeholder": true`) and say so. Test fixtures use obviously fake IDs (`fixture:…`).
- **No ban risk:** only observe UI via AccessibilityService and respond with global actions (back/home)
  or by launching an intent. Never modify, hook or inject into other apps.
- **No INTERNET permission.** CI fails if it appears (`scripts/check-no-internet.sh`).
- **Never read, log or store message content.** Match structure only. Release builds log nothing.
- **Fail open:** if rules don't match, allow. Never block messaging by accident.
- No root, device admin or system-setting changes. Focus off = service inert.

## Commands
```bash
./gradlew spotlessApply          # auto-format (ktlint)
./gradlew spotlessCheck          # style gate
./gradlew testDebugUnitTest      # JVM unit tests (domain/ is pure Kotlin)
./gradlew lintDebug              # Android Lint, warnings are errors
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
scripts/check-no-internet.sh     # after assembling
adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/inspect.sh               # debug builds: stream screen signatures from Logcat
```
Run all of spotlessCheck, testDebugUnitTest, lintDebug, assembleDebug before every commit.

## Architecture map (`app/src/main/java/com/prestoza/inboxonly/`)
- `domain/` — pure Kotlin, no `android.*` imports. All decisions live here and are unit tested:
  `FocusState`, `FocusController` (state transitions), `ScheduleCalculator` (java.time),
  `BlockDecider`, `ReelFromChatTracker`, `BlockThrottle`, `Rules` + `RulesValidator`, `Settings`,
  `BlockCatalog` (app/surface IDs shown in settings), `TileStatusFormatter`, `DailyCount`.
- `data/` — DataStore-backed `SettingsRepository`, `FocusStateStore`, `CounterStore`, `OnboardingStore`;
  `rules/RulesRepository` loads `assets/rules.json`.
- `service/` — thin Android adapters: `BlockerAccessibilityService` + `ScreenSignatureReader`,
  `FocusTileService`, `FocusCoordinator` (the only place Focus state changes are applied),
  `AlarmScheduler`, `AlarmReceiver`, `BootReceiver`, `PermissionStatus`.
- `ui/` — Compose `MainActivity`, `SettingsScreen`, `OnboardingScreen`.
- `AppContainer` — manual dependency injection (no Hilt), created by the `Application` subclass.
- `src/debug/` — `InspectorLogger` (real); `src/release/` — no-op twin.

Focus state has a single source of truth (`FocusStateStore`), always read through
`FocusController.reconcile(...)` so expired states resolve correctly even if an alarm was late.

## Rules and fixtures
- `assets/rules.json` is a placeholder (`"placeholder": true`) until real captures exist; placeholder rules
  block nothing. Unit tests use `src/test/resources/fixture_rules.json` + `FixtureScreens.kt` (fake IDs).
- `ServiceConfigTest` keeps the service XML `packageNames` equal to the packages in rules.json.
- Never set `AccessibilityServiceInfo.packageNames` to null at runtime (null = all apps).

## Conventions
- Conventional Commits with scopes (`feat(tile): …`). Small commits, each building and passing tests.
- Milestone branches `mN-name`, merged into `main` with `--no-ff`, tagged `mN`.
- Update `CHANGELOG.md` (Keep a Changelog) every milestone.
- Comments explain *why*, in plain English. KDoc on public API in `domain/` and `service/`.
  Top-of-file comment on major files. No commented-out code; no TODOs without context.
- Per-app identifiers live only in `app/src/main/assets/rules.json`, never in Kotlin.
- `SPEC.md` is a gitignored working prompt; durable decisions go to `docs/DECISIONS.md`.

## Docs
- `README.md` — non-technical, first person, for the owner and curious strangers.
- `docs/TECHNICAL.md` — developer deep dive. `docs/UPDATING_RULES.md` — fixing rules after app updates.
- `docs/TESTING.md` — on-device acceptance checklist. `docs/DECISIONS.md` — ADRs.
