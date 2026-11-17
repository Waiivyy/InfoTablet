# InfoTablet

A wall-mounted home information display for an old Android tablet, in Bosnian. It replaces the
tablet's home screen and shows the clock, weather and warnings, air quality, fuel prices, the
holiday calendar, news, football, exchange rates, water works and road conditions, plus
reminders with a full-screen alarm.

Built for a Lenovo IdeaTab A3000-H (Android 4.2.2, 1024×600), and it runs on Android 4.2 or newer.

- **Fully local.** No server and no account. The tablet fetches public sources directly and keeps
  the last good data in a local database. The only other requests go to GitHub Releases, to check
  for updates. No analytics, no ads, no telemetry.
- **Plain Android.** Java and framework views only (no AndroidX), OkHttp 3.12 with Conscrypt for
  modern TLS on Android 4.2, jsoup for HTML. The APK is about 1.5 MB.
- **Sources** are documented in [docs/sources.md](docs/sources.md): what each one returns, how
  often it is polled and what happens when it fails. Their credits are also shown in the app
  under Postavke, O aplikaciji.

## Building

Needs JDK 21 and the Android SDK (build-tools 36.1.0).

```sh
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build adds a self-test screen and adb hooks for testing (shifting the clock, short alarm
rings, offering a local APK as an update); see `app/src/debug`.

## Releases and updates

The app checks this repository's latest GitHub Release once a month, at once when a data source has
been failing for two days, and when "Provjeri ažuriranje" is tapped in Postavke. A newer APK is
downloaded, checked against GitHub's SHA-256, the package name, the version and the signing key,
and then offered with an "Instaliraj" button.

To publish a version:

1. Build it: `./gradlew assembleRelease -PappVersion=1.2.3` (the version code becomes 10203).
2. Create a release with the tag `v1.2.3` and attach `app/build/outputs/apk/release/app-release.apk`
   (for example renamed to `InfoTablet-1.2.3.apk`). The release text is shown in the app.

Release builds are signed with a key kept outside the repository. `local.properties` points to a
properties file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`:

```properties
infotablet.signing=/path/to/signing.properties
```

Without it, the release build comes out unsigned. Every update must be signed with the same key:
an APK signed with another key cannot replace the installed app, and uninstalling it deletes the
reminders.

## Device-only settings

Personal values are never part of the code. They are set on the tablet over adb (only the adb
shell may send this broadcast):

```sh
adb shell am broadcast -a ba.infotablet.CONFIG --es key <key> --es value <value>
```

Keys: `creator_name`, `creator_link` (shown in O aplikaciji), `sport_club`, `water_places`
(streets for the water works notices), `city`, `city2`, and `pin`. A value of `-` clears the
setting (for `pin`: removes a forgotten PIN).

## Licences

Third-party components and fonts are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
