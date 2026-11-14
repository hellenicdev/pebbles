# Pebbles Android app

First APK. It keeps the settings the pebble firmware is waiting for: a sender colour, quiet hours (UTC, no buzz and no chime), a group of five, and up to five Wi-Fi networks. They stay on the phone. Bluetooth pairing is not in this version, so Tap and Hold only update the preview on screen.

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
