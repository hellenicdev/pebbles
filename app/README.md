# Pebbles Android app

The first launch is a short introduction, then setup. After that there are two places in the bar at the bottom: Home and Settings. Home shows the pebble, the people in the group and whether they are quiet or awake, and a tap or a hug so you can see the glow. The touch itself still happens on the pebble. Settings holds the colour, quiet hours, battery and pairing, the group, occasion glows, Wi-Fi, crash reports, and app updates. A group can hold anywhere from one person to five.

Pair writes version-1 JSON to service `5b1a8c10-6e2d-4a7f-9c31-0d4e5f6a7b80`, characteristic `5b1a8c11-6e2d-4a7f-9c31-0d4e5f6a7b80`: colour, quiet hours, the names you added, and up to five Wi-Fi networks. There is no PIN yet. This source is 0.4.0. The published 0.3.0 APK is the earlier version.

Open `app/` in Android Studio, or from this directory:

```sh
./gradlew :pebble:assembleDebug
```

The debug APK is `pebble/build/outputs/apk/debug/pebble-debug.apk`.

## Sign a release APK

Do this on your own machine. Do not commit the keystore or the passwords.

1. Create a keystore once. Remember the passwords. If you lose this file you cannot publish an update of the same app.

```sh
keytool -genkeypair -v \
  -keystore "$HOME/pebbles-release.jks" \
  -alias pebbles \
  -keyalg RSA -keysize 2048 -validity 10000
```

2. Export the four values, with `PEBBLES_STORE_FILE` as the full path to that `.jks` file:

```sh
export PEBBLES_STORE_FILE="$HOME/pebbles-release.jks"
export PEBBLES_STORE_PASSWORD="the-keystore-password"
export PEBBLES_KEY_ALIAS="pebbles"
export PEBBLES_KEY_PASSWORD="the-key-password"
```

3. Build the release APK. Gradle signs it only when all four variables are set.

```sh
./gradlew :pebble:assembleRelease
```

The signed file is `pebble/build/outputs/apk/release/pebble-release.apk`. Install it with `adb install -r` that path.

Without those variables, `assembleRelease` still writes an APK, and that APK is unsigned. Android will refuse to install an unsigned release APK. Use `assembleDebug` for a locally installable build, or set the four variables for a release you can share.

## Crash reports

Crash reports are off until you turn on "Send crash reports" in Settings, under Crash reports. That choice stays on the phone. While it is off, Sentry does not start and nothing is sent. Turning it off stops sending.

Under the switch, and only while it is on, choose "Anonymous" or "Include my IP address". Anonymous is the default. Both choices stay on the phone, and changing them applies to the next report without restarting the app.

A report is the error, the app version (`pebble@` plus `versionName`, so `pebble@0.4.0` today), and `debug` or `release`. It does not include a user id, an email, Wi-Fi names or passwords, or the contents of a touch. Anonymous also sends no IP address. Include my IP address lets Sentry record this phone's connection address and nothing else. There is no performance tracing and no session replay.

Anonymous works with Sentry's "Prevent Storing of IP Addresses" left off. The report does not contain `{{auto}}`. It sets `sdk.settings.infer_ip` to `never`, which tells Relay not to fill the connection IP. Include my IP address sends `user.ip_address` as `{{auto}}` and `infer_ip` as `auto`, so Sentry stores the connection address.

To check a debug build, turn the switch on, then open Settings and tap "Send test error" in the Debug section. That section is not in a release build. The event shows up in the Sentry project `pebbles-android` (org `hellenicdev`, EU, [de.sentry.io](https://de.sentry.io)).

The DSN shipped in the app is a public client key. Override it without editing source. The environment variable wins when both are set:

```sh
export SENTRY_DSN="https://…@….ingest.de.sentry.io/…"
./gradlew :pebble:assembleDebug
```

```sh
./gradlew :pebble:assembleDebug -PsentryDsn="https://…@….ingest.de.sentry.io/…"
```

Mapping and source upload are off, so debug and release builds do not need a Sentry auth token. The `PEBBLES_*` signing variables are unchanged.

## Updates

Settings, then App updates, has "Check for updates". The app can also look quietly when it opens. That check uses the public GitHub API for [hellenicdev/pebbles](https://github.com/hellenicdev/pebbles/releases) and does not need a token. It compares the release tag with this app's `versionName`.

The updater installs one asset, named `pebbles-<version>.apk`. For version 0.5.0 the file is `pebbles-0.5.0.apk`. A release that is newer but has no file with that name is shown, and not downloaded.

Android only installs the new APK when it is signed with the same key as the app already on the phone. A debug build cannot update to a release build. The release build's `versionCode` must also be higher. `./gradlew :pebble:assembleRelease -PversionName=0.5.0` sets both the name and a matching code.

When a GitHub release is published, `.github/workflows/release-apk.yml` can build that APK and attach it under the expected name. It does this only when the repository secrets `PEBBLES_STORE_BASE64`, `PEBBLES_STORE_PASSWORD`, `PEBBLES_KEY_ALIAS`, and `PEBBLES_KEY_PASSWORD` are set. `PEBBLES_STORE_BASE64` is the release keystore, base64-encoded. Without those secrets the workflow still succeeds and attaches nothing.

The same workflow writes a build provenance attestation for that APK, and only when the APK was actually built. After you download `pebbles-<version>.apk`, check that GitHub signed the build:

```sh
gh attestation verify pebbles-<version>.apk -R hellenicdev/pebbles
```
