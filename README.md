# YesChef

A native Android app for [Mealie](https://mealie.io) running on a Start9 server, built for one
household's Pixel phone and Pixel Tablet. Mealie stays the source of truth (an AI agent fills it
with recipes, meal plans and grocery lists through Mealie's API); YesChef is the fast, offline-first
way to use that data in the kitchen and the store.

## What's in v0.1

- **Server setup**: Mealie address plus an API token (or username and password, which is
  exchanged for a token).
- **Shopping lists**: grouped into store sections using Mealie labels, in the order set in the
  list's label settings. Tick items or add new ones offline; edits queue and sync later.
- **Recipes**: searchable list and recipe detail with ingredients and steps. On the tablet the
  recipe opens beside the list. **Cook mode** (sun icon) keeps the screen awake and enlarges text.
- **Meal plan**: the next three weeks, tap a meal to open its recipe.
- **Cook timers**: times in recipe steps ("bake 25 minutes", "simmer 1 hr 15 min", "10-12 min")
  show up as chips under the step; tap one to start a timer. Run as many as you like, with pause,
  +1 min and cancel, from the tray at the bottom of every screen or from the notification. A
  finished timer rings with your alarm sound until you stop it, even when the app is closed or the
  screen is off. The timer icon on a recipe starts a custom timer.
- **Offline-first**: everything is cached in a local database. Upcoming planned recipes are
  downloaded ahead of time so they open without a connection.

## How sync works

Mealie has no "changed since" query for lists and meal plans, so each sync pushes queued edits
first, then re-pulls recipes (summaries), shopping lists and the meal plan in full. For one
household's data this is small. A sync runs when the WireGuard VPN connects, when the app comes
to the foreground, about every 30 minutes, and right after each edit (WorkManager retries with
backoff while the server is unreachable). Local edits that haven't been sent are never overwritten
by server data.

## Connecting to Start9 over WireGuard

1. Connect the WireGuard VPN on the device.
2. StartOS signs its HTTPS certificates with its own Root CA. Download it from StartOS and
   install it on the device: *Settings > Security & privacy > More security settings > Encryption
   & credentials > Install a certificate > CA certificate*. YesChef's network security config
   trusts user-installed CAs (Android apps don't by default).
3. In Mealie, create an API token (profile > API Tokens) and paste it with the server address,
   for example `https://<your-mealie-address>` (YesChef adds `/api`).

## Building

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

Every push and pull request is built by GitHub Actions; the debug APK is attached to the run as
the `yeschef-debug-apk` artifact, so you can sideload it without a local Android setup.

## Layout

```
app/src/main/java/app/yeschef/
  data/remote/   Mealie REST API (Retrofit + kotlinx.serialization)
  data/local/    Room cache and queued edits
  data/          Repository (sync), shopping sort logic, settings
  data/sync/     WorkManager sync worker and scheduling
  ui/            Compose screens: setup, shopping, recipes, meal plan, settings
```
