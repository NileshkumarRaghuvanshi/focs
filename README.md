# Focs

An Android app that keeps you off time-wasting apps and helps you push through lazy moments.

## What it does

**1. Hard app limits.** Pick an app, give it a daily limit. When the time is used up, Focs sends you to the home screen and shows a "Time's up" wall. There is no "ignore for today" button. Making a limit stricter works immediately; loosening or removing one only takes effect tomorrow, so you can't talk yourself into "15 more minutes". You get a one-minute warning toast.

**2. Focus mode.** Pick a duration and start. A big countdown shows the time left (also in the notification shade). Do Not Disturb switches to priority-only: every notification is silenced and hidden, except calls from your **starred contacts**, **anyone who calls twice within 15 minutes**, and alarms. All your limited apps are blocked completely until the timer ends. Your previous DND setup is restored afterwards. Ending early is possible, but only after a 10-second pause looking at one of your goals.

**3. Motivation.** Add your own goal one-liners on the Goals tab. Tap "I'm feeling lazy" during focus mode to see one of your goals or one of 30 built-in lines. Goals also appear on the block wall and the quit dialog.

## Setup on the phone (one time)

1. **Usage access**: Settings > Usage access > Focs > allow. (Lets Focs measure time per app.)
2. **Accessibility**: Settings > Accessibility > Focs app blocker > on. (This is what closes apps.)
3. **Do Not Disturb access**: allow Focs in the list. (Lets focus mode silence notifications.)
4. **Star your important people** in the Contacts app.

The app shows a card for each missing permission with a button that opens the right Settings page.

## Building

Open the folder in Android Studio (Ladybug or newer) and press Run, or `./gradlew assembleDebug`.
Kotlin + Jetpack Compose, minSdk 28 (Android 9), targetSdk 35. No internet permission, no accounts; everything stays on the phone.

## Known loopholes

- You can still turn off the accessibility service in Settings, which stops the blocking. A later version could add a Device Admin lock or a cooldown before the service can be turned off.
- Some phone makers (Xiaomi, Oppo, Vivo, some Samsung modes) kill background services aggressively. If blocking stops working, set Focs' battery usage to "Unrestricted".

## Code map

- `data/Store.kt` limits (with the "loosen tomorrow" rule), goals, focus state
- `data/UsageTracker.kt` today's per-app usage from UsageStatsManager events
- `block/BlockerService.kt` accessibility service that detects the front app and blocks it
- `block/BlockActivity.kt` the "Time's up" wall
- `focus/FocusManager.kt` DND policy (starred callers + repeat callers) and session start/stop
- `focus/FocusService.kt` foreground service with the countdown notification
- `ui/` the three tabs: Limits, Focus, Goals
