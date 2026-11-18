# InfoTablet

A wall-mounted home information display for an old Android tablet, in Bosnian. It replaces the
tablet's home screen and shows the clock, weather and warnings, air quality, fuel prices, a
calendar with holidays and plans, news, football (the BiH league and the Premier League,
Bundesliga and La Liga, with the followed club's live score), exchange rates, water, power,
heating and gas notices for the home streets, and road conditions, plus reminders with a
full-screen alarm and a 30-day history.

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

Keys: `creator_name`, `creator_link` (shown in O aplikaciji), `sport_league` (`bih`, `PL`, `BL1`
or `PD`) and `sport_club` (also chosen in Postavke, Sadržaj), `football_key` (see below),
`water_places` (streets for the water, power, heating and gas notices), `city`, `city2`, and `pin`.
A value of `-` clears the setting (for `pin`: removes a forgotten PIN).

The Premier League, Bundesliga and La Liga come from football-data.org, which needs a free key
(register at football-data.org). Set it from your own terminal so it never lands in a file or a
chat; it is not included in backups, so set it again after a reset:

```sh
adb shell am broadcast -a ba.infotablet.CONFIG --es key football_key --es value <your key>
```

## Looking after the tablet

A one-page guide in Bosnian for the people who live with the tablet is in
[docs/uputstvo.html](docs/uputstvo.html) (print it on A4, or use the PDF next to it). The tablet's
own system menus stay in English, so the steps below use the English names.

### Updating

- **Normal way.** Publish a release (see above). The tablet picks it up within a month, sooner if
  a source has been failing for two days. To fetch it at once: Postavke, Ažuriranja,
  "Provjeri ažuriranje". When it is ready, "Instaliraj" appears on Početna and in Postavke.
  Android then asks in English: Install. The app reopens by itself; settings and reminders stay.
- **Over USB.** `adb install -r InfoTablet-1.2.3.apk`. This only works with an APK signed with
  the release key. Never uninstall to get past a signature error unless you have a fresh backup:
  uninstalling deletes the reminders.

### Changing settings

- **On the tablet:** Postavke, behind the PIN. It locks again when you leave it or after two
  minutes without a touch.
- **From a phone on the home Wi-Fi:** the Wi-Fi page (reminders and a backup download). Its address
  and a QR code are in Postavke, Wi-Fi stranica. The address comes from the router, so reserve the
  tablet's IP address in the router's DHCP settings to keep a phone bookmark working.
- **Device-only values** (creator, club, streets, PIN): over adb, see the next section.
- **Forgotten PIN:** clear it over adb with `--es key pin --es value -`, then open Postavke and set
  a new one.

### Backups

The tablet writes a copy of its settings and reminders to the `InfoTablet` folder on its own
storage once a day when something changed, and keeps the newest seven. Postavke, Sigurnost i
podaci has "Napravi kopiju" for a copy by hand (those are never deleted automatically) and "Vrati"
to restore one. The Wi-Fi page can download a copy to a phone. The PIN and the football-data key are never in a copy; the other device-only values are, so keep
copies off the tablet private and out of this repository:

```sh
adb pull /sdcard/InfoTablet
```

### Recovering after a factory reset

1. Before the reset, if the tablet still works, copy the backups off it (above). A reset may wipe
   the tablet's storage too.
2. Android setup: connect to Wi-Fi, skip the Google account, automatic date and time, time zone
   Europe/Sarajevo.
3. Settings, About tablet: tap "Build number" seven times. In Developer options, turn on USB
   debugging. Then, with the tablet connected:

   ```sh
   adb shell settings put global stay_on_while_plugged_in 3   # screen stays on while charging
   adb shell settings put system screen_off_timeout 1800000   # 30 minutes on battery
   adb shell settings put secure screensaver_enabled 0        # no Daydream
   adb shell settings put global install_non_market_apps 1    # Unknown sources, for updates
   adb shell settings put global package_verifier_enable 0    # no Google "Verify apps" prompt
   ```

4. Settings, Security, Screen lock: None (otherwise a lock screen appears after every restart).
5. Install the newest APK from this repository's Releases: `adb install InfoTablet-x.y.z.apk`.
6. Press Home, choose InfoTablet, then "Always".
7. Restore: `adb push infotablet-<date>.json /sdcard/InfoTablet/`, then Postavke (it asks for a new
   PIN first), Sigurnost i podaci, "Vrati". This brings back the settings, the device-only values
   (except the football-data key) and the reminders. Without a backup, set the device-only values
   over adb instead.
8. Disable the preinstalled apps that run in the background or report to Lenovo: Settings, Apps,
   All, the app, Disable. On this tablet these were Lenovo LSF device, Lenovo registration
   (com.leadertech.powerregister.len2), the Lenovo weather, calendar and memo widgets, Lenovo
   Power Saving and Google Search. Where the button says Uninstall instead, leave the app alone.
9. Restart the tablet and check that InfoTablet opens by itself with no lock screen.

### Signing key

Release builds are signed with a key kept outside this repository (`local.properties` points to
it). Back up both the key file and the properties file with its password, in two places (for
example a password manager and an encrypted USB stick). Without the key, no update can be
installed over the app on the tablet; it would have to be uninstalled first, which deletes the
reminders unless they are restored from a backup.

### Hardware

- Keep the tablet on its wall charger. On battery the screen turns off after 30 minutes and
  reminders can go unseen.
- The battery sits at 100 % all the time, and a lithium battery kept full for years can swell.
  Look at the back every few months; if it bulges or the case lifts, unplug the tablet and replace
  the battery. A cool spot away from radiators helps (the 20-hour test ran at 23 to 28 °C).

## Health diary

Every 15 minutes the app writes one line to `InfoTablet/log/health-<date>.tsv` on the tablet's
storage: process memory, free system memory, battery temperature, level and health, power source,
network, and how many times each source succeeded or failed since the line before. App starts,
installed updates and crashes (with the stack trace) are logged too. Files are kept for 14 days and
never leave the tablet:

```sh
adb pull /sdcard/InfoTablet/log
```

## Licences

Third-party components and fonts are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
