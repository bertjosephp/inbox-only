# Inbox Only

**Block the scroll, keep the messages.**

Inbox Only is a small Android app with one switch: **Focus**. When Focus is on, Instagram Reels,
Facebook feeds, YouTube Shorts and Reddit browsing get blocked, while your DMs and messages keep working
exactly as usual. When Focus is off, your phone behaves completely normally.

## Why I built this

I wanted to stay reachable by friends on work-from-home days and during focused free time, without
getting pulled into Reels and feeds. The usual tools didn't fit. App timers block the *whole* app,
DMs included. Network blockers break messaging too, because the same apps serve both. So I built
something that blocks only the scroll.

## What it does (and doesn't)

| App | Blocked while Focus is on | Still works |
|---|---|---|
| Instagram | Reels tab, Explore, Home feed | DMs and inbox, notifications, a Reel a friend sent you in a chat |
| Facebook | Reels, Video, News feed | Notifications |
| YouTube | Shorts, Home feed | Search, Subscriptions, any video you open directly |
| Reddit | Feeds, Popular, subreddits, search results, the video feed | A single thread, e.g. one you opened from a Google result |

**Messenger is never touched.** Inbox Only doesn't even look at it.

When you land on a blocked screen, the app steps you back out of it. Instagram jumps to your DM inbox,
Facebook sends you to Messenger, and YouTube and Reddit just close. You can switch any app or screen
off in the settings.

**Reels from friends:** if someone sends you a Reel in a DM, you can watch that one. Swipe to the next
Reel and you're sent back to the chat. (You can change this to "Block" if you prefer.)

## Not on the Play Store

I built this for my own phone and the apps I use. You're welcome to download it and try it! There are
no support promises, but issues and pull requests are welcome.

## Compatibility

- Works on **Android 10 and newer** (minimum API level 29). It's built for Android 17 (target API level 37).
- Tested on a **Samsung Galaxy S25+**. It should work on other Android phones too.
- Heads-up: when Instagram, Facebook, YouTube or Reddit redesign their screens, detection can
  temporarily stop working until the rules are updated.

## How it works (in plain English)

Android has a feature called an **accessibility service**. It's normally used by screen readers to
tell what's on the screen. Inbox Only uses it to notice when a blocked screen (like the Reels tab)
appears, and then taps "Back" for you, just as you would.

Nothing is modified. Inbox Only never changes, patches or connects to Instagram or any other app,
and it has no internet permission at all, so nothing ever leaves your phone.

**Why there's no ban risk:** from Instagram's point of view, you pressed the Back button. The app
doesn't automate your account, scrape anything or talk to anyone's servers. It just watches which
screen is open on your own phone.

## Privacy

- **No internet access.** The app can't send anything anywhere. This is checked automatically every
  time it's built.
- **Your messages are never read or stored.** It only checks *which* screen is showing (for example,
  "is the Reels tab selected?"), not what's written on it.
- **No analytics, no tracking, no accounts.**

## Install

1. **Download the APK.** Get the latest `inbox-only-…apk` from the
   [Releases page](../../releases) on your phone.
2. **Allow the install.** Open the file. Android asks you to allow installing apps from your browser
   or Files app: tap **Settings**, switch on **Allow from this source**, then go back and tap **Install**.
   - *Samsung:* if the install is blocked by **Auto Blocker**, go to Settings → Security and privacy →
     Auto Blocker, turn it off, install the app, then turn it back on.
   - If Google Play Protect warns about an unknown app, tap **More details → Install anyway**.

   > 📷 Screenshot to add: `docs/images/01-install-unknown-apps.png`
3. **Open Inbox Only and follow the 3-step setup:**
   1. **Turn on the blocker** (accessibility). Tap *Open accessibility settings*, find Inbox Only and
      switch it on.
      If the switch is greyed out or you see "Restricted setting": this is Android protecting you from
      apps installed outside the Play Store. Tap *Open App info*, then the **⋮** menu (top right) →
      **Allow restricted settings**, confirm, and try again.

      > 📷 Screenshots to add: `docs/images/02-accessibility.png`, `docs/images/03-restricted-settings.png`
   2. **Keep it running** (battery). Tap *Allow*. On Samsung, also go to Settings → Battery →
      Background usage limits → **Never sleeping apps** → **+** → Inbox Only → **Add**.

      > 📷 Screenshot to add: `docs/images/04-never-sleeping-apps.png`
   3. **Add the Focus tile.** Tap *Add tile* and confirm. Or add it yourself: swipe down twice from the
      top, tap the pencil icon, and drag **Focus** into your tiles.

      > 📷 Screenshot to add: `docs/images/05-add-tile.png`

## Daily use

Swipe down and **tap the Focus tile**. That's it. Tap again to turn it off. **Long-press** the tile to
open the settings.

The tile's small text tells you what's going on, like `1h 12m left`, `Until 6:00 PM` or `14 blocked`.

You can also set a **schedule** (say, 8:00–18:00 on weekdays) so Focus turns on and off by itself.
During a scheduled window, tapping the tile gives you a short **break** (5, 10 or 15 minutes) instead
of turning Focus off. If you turn on **strict mode**, the tile won't even give you a break.

## Uninstall

Turn Focus off (optional), then uninstall Inbox Only like any other app. Nothing is left behind: it
never changes any system settings.

## FAQ

**Can I get banned from Instagram/Facebook/YouTube/Reddit?**
No. Inbox Only never touches those apps or their servers. It only presses Back (or Home) for you,
exactly like your own thumb would.

**Why does it need accessibility access?**
It's the only way Android lets an app see which screen another app is showing. Inbox Only uses that to
recognise the Reels tab, a feed and so on. It only listens to Instagram, Facebook, YouTube and Reddit,
only while Focus is on, and it never reads your messages.

**Why isn't it on the Play Store?**
It's a personal project. The Play Store has strict rules for accessibility apps, and going through that
review isn't worth it for an app I made for myself.

**It stopped blocking Reels. Why?**
Most likely Instagram (or another app) updated its design and the app's detection rules need updating.
Messaging keeps working in the meantime: when the app isn't sure, it lets you through. If you're a
developer, [docs/UPDATING_RULES.md](docs/UPDATING_RULES.md) explains the fix. Otherwise, open an issue.
Also check that the accessibility service is still on: the tile shows **Set up** if it isn't.

**Does it drain my battery?**
No. Android only wakes it for the four apps it watches, and only while Focus is on. When Focus is off
it receives nothing at all. It never runs in the background on its own and never uses the network. The
countdown on the tile is only worked out when you pull down Quick Settings.

## Ideas for later

Not planned for now, but maybe someday: website blocking in the browser, more than one schedule, a
stats screen, streaks or badges, cloud sync, home-screen widgets, Wear OS, and a Play Store version.

## For developers

How it works under the hood, building from source and adding apps: [docs/TECHNICAL.md](docs/TECHNICAL.md).
Fixing detection after an app update: [docs/UPDATING_RULES.md](docs/UPDATING_RULES.md).

## License

[MIT](LICENSE)
