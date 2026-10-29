# Updating rules after an app update

When Instagram, Facebook, YouTube or Reddit ship a redesign, a rule can stop matching. Since the app
fails open, the symptom is "Reels aren't blocked any more", never "my DMs are blocked". The fix is a JSON
edit in `app/src/main/assets/rules.json`, driven by real data from your phone.

**Golden rule: never guess an identifier.** Every view ID and content description in `rules.json` must
come from inspector output captured on a real device.

## What you need

- A Mac with the SDK set up ([TECHNICAL.md §7](TECHNICAL.md#7-build-from-source-macos-command-line-no-android-studio-needed)).
- The phone with **Developer options → USB debugging** on, connected by USB. Run `adb devices` and
  accept the prompt on the phone.

## Steps

1. **Install a debug build.** The inspector only exists in debug builds.
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   A debug build has a different signature from the release build. If Android refuses the install,
   uninstall the release app first, then redo onboarding on the debug build. In particular, re-enable
   the accessibility service.
2. **Start capturing.** You don't need Focus on: in debug builds the service listens anyway.
   ```bash
   scripts/inspect.sh capture.txt
   ```
3. **Open the screens.** On the phone, open each screen involved, one at a time, waiting ~2 seconds on
   each. Include both the broken one and its neighbours, e.g. the Reels tab *and* the Home tab *and* the
   DM inbox. Each screen prints a block like:
   ```
   ===== com.instagram.android | window=… | 57 nodes =====
     FrameLayout id=com.instagram.android:id/… desc="Reels" [selected]
     …
   ===== end =====
   ```
   Text longer than 30 characters is already replaced with `‹N chars›`. Still, skim the capture before
   sharing it anywhere and delete anything personal (usernames, short messages).
4. **Find a distinguishing feature.** Compare the blocks:
   - A **selected tab** is the most robust signal: `selectedViewIds`, or `selectedContentDescriptions`
     if the tabs share an ID.
   - A **view ID that only exists on that screen**: `allViewIds` / `anyViewIds`.
   - Use `noneViewIds` to tell apart two screens that share most IDs (e.g. the Reel viewer inside the
     Reels tab has the bottom bar; one opened from a DM doesn't).
   - Prefer IDs over content descriptions: descriptions change with the phone's language.
5. **Edit `rules.json`.** Update the `match`, keep `allowed` screens complete (DMs first!) and bump
   `version`. For `redirect` deep links, only use a URI you've confirmed opens the right screen:
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "<uri>" <package>
   ```
6. **Check.**
   ```bash
   ./gradlew testDebugUnitTest   # RulesTest validates the file; ServiceConfigTest checks packages
   ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   With Focus on, `scripts/inspect.sh` also prints a `decision …` line for each screen showing the IDs
   the rules saw and the outcome. That's the quickest way to see why a rule did or didn't fire.
7. **Update the fixtures.** If a screen's structure changed, update its signature in
   `app/src/test/java/…/domain/FixtureScreens.kt` so the tests keep modelling reality. Commit with `fix(rules): …`.
8. **Release.** Bump `versionCode`/`versionName` in `app/build.gradle.kts`, update `CHANGELOG.md`, and
   tag `vX.Y.Z` to publish a signed APK.

## Troubleshooting

- **No output at all:**
  - Is it a debug build?
  - Is the accessibility service on? Settings → Accessibility → Inbox Only.
  - Run `adb devices` to check the connection.
- **Output only for some screens:** the inspector only logs a screen whose outline changed, so revisit
  another screen and come back.
- **Rule matches in tests but not on the phone:** check the `decision` log line. A view that's
  off-screen or not yet loaded isn't reported, so pick an element that's always visible on that screen.
