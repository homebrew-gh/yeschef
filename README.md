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

## Look and feel

White and light blue with soft gold accents, DM Serif Display for headings (SIL Open Font
License, see `licenses/`), and a chef's hat icon that also works as an Android 13+ themed icon.
Shopping items carry a colour stripe and tag for each recipe they came from, and the list can be
filtered to one recipe.

Screenshots of every main screen are rendered by Paparazzi (`app/src/test/java/.../screens`) and
committed by CI to `app/src/test/snapshots/images` on each pull request. Locally:
`./gradlew recordPaparazziDebug`.

## The cookbook: categories and tags

The Recipes tab reads like a cookbook. Its chapters come from Mealie **categories**, and its filter chips come from **tags**. Give each recipe one category and as many tags as fit. This scheme is what the app expects, and it's what to tell an AI assistant that tags the library:

| Mealie field | Use it for | Values |
| --- | --- | --- |
| Category (one per recipe) | Chapter | Mains, Sides, Soups & Salads, Breakfast, Desserts, Baking, Starters & Snacks, Drinks, Sauces & Basics |
| Tags: protein | Filter chips | Chicken, Beef, Pork, Lamb, Turkey, Seafood, Vegetarian, Vegan |
| Tags: style | Search and future filters | Quick (under 30 min), Slow cooker, Grill, One pot, Make ahead, Weeknight, Freezer friendly |

Common alternatives are understood too. "Main course", "Dinner" and "Entree" go under Mains, and "Side dish" goes under Sides. "Fish" and "Shrimp" count as Seafood. Any other category becomes a chapter of its own, after the standard ones. Recipes with no category go under "More recipes".

**Last cooked** is Mealie's "last made" date. When you leave cook mode the app asks "Mark as cooked?", and the date then shows on the recipe card. Sort by "Not made in a while" to find old favourites. The dice button picks a random recipe from what's on screen and skips anything cooked in the last two weeks.

## Scaling and nutrition

The **Serves** control next to Ingredients scales every amount, including the "You'll need" boxes in cook mode. It changes the number at the start of each ingredient line ("1 1/2 lb" becomes "2¼ lb"), so ingredients Mealie hasn't parsed scale too. Lines with no amount, like "salt to taste", are left as they are. Recipes without a serving count get a ×½ to ×4 multiplier instead. **Nutrition** shows Mealie's per-serving values in a small section that expands when tapped.

## How sync works

Mealie has no "changed since" query for lists and meal plans, so each sync pushes queued edits
first, then re-pulls recipes (summaries), shopping lists and the meal plan in full. For one
household's data this is small. A sync runs when the WireGuard VPN connects, when the app comes
to the foreground, about every 30 minutes, and right after each edit (WorkManager retries with
backoff while the server is unreachable). Local edits that haven't been sent are never overwritten
by server data.

## Installing and updating

Every push to `main` builds a signed APK and publishes it as a GitHub Release
(`build-N`). On the phone or tablet, open the repo's **Releases** page, download the newest
`yeschef-0.2.N.apk` and install it; it updates the app in place, keeping your data.

All builds are signed with one key, stored only in repository secrets, and the version code
rises with every build, so Android accepts each new APK as an update.

### One-time signing key setup

Run on any computer with a JDK (`keytool` also ships with Android Studio):

```sh
keytool -genkeypair -v -keystore yeschef.jks -storetype PKCS12 -alias yeschef \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=YesChef"
# choose a strong password when asked

base64 -w0 yeschef.jks > yeschef.jks.b64    # macOS: base64 -i yeschef.jks -o yeschef.jks.b64
```

Then in GitHub: **Settings > Secrets and variables > Actions > New repository secret**, add:

| Secret | Value |
|---|---|
| `YESCHEF_KEYSTORE_BASE64` | contents of `yeschef.jks.b64` |
| `YESCHEF_KEYSTORE_PASSWORD` | the password you chose |
| `YESCHEF_KEY_ALIAS` | `yeschef` |
| `YESCHEF_KEY_PASSWORD` | the same password (PKCS12 uses one password) |

Keep `yeschef.jks` and its password somewhere safe (a password manager), then delete the
`.b64` file. If the key is ever lost, the next build can't update the app and you'd have to
uninstall once. Without the secrets, CI still builds but signs with a throwaway key.

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
