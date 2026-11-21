# Pebbles Android app

The first launch is a short introduction, then setup. After that the app is four settings screens: Home, People, Pebble, and Wi-Fi. The phone does not send a tap or a hug. Those happen on the Pebble. A group can hold anywhere from one person to five.

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

Crash reports are off until you turn on "Send anonymous crash reports" on the Home screen. That choice stays on the phone. While it is off, Sentry does not start and nothing is sent. Turning it off stops sending.

A report is the error, the app version (`pebble@` plus `versionName`, so `pebble@0.4.0` today), and `debug` or `release`. It does not include a user id, an email, an IP address, Wi-Fi names or passwords, or the contents of a touch. There is no performance tracing and no session replay.

To check a debug build, turn the switch on, then tap "Send test error". That button is not in a release build. The event shows up in the Sentry project `pebbles-android` (org `hellenicdev`, EU, [de.sentry.io](https://de.sentry.io)).

The DSN shipped in the app is a public client key. Override it without editing source. The environment variable wins when both are set:

```sh
export SENTRY_DSN="https://…@….ingest.de.sentry.io/…"
./gradlew :pebble:assembleDebug
```

```sh
./gradlew :pebble:assembleDebug -PsentryDsn="https://…@….ingest.de.sentry.io/…"
```

Mapping and source upload are off, so debug and release builds do not need a Sentry auth token. The `PEBBLES_*` signing variables are unchanged.
