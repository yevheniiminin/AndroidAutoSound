# Android Auto Sound

A small Android app: pick a sound, and it plays every time your phone connects
to your car / **Android Auto** (wired or wireless). Optionally play a second
sound on disconnect.

## Trigger modes

The app offers two ways to detect the connection — pick in the UI:

- **Bluetooth (default, no notification).** A manifest `BroadcastReceiver`
  listens for *your chosen car* connecting over Bluetooth. No always-on service,
  no persistent notification. Covers wireless Android Auto and any car that pairs
  over Bluetooth. Uses `goAsync()` to stay alive during the (short) sound — very
  long clips may be clipped in this mode.
- **Android Auto detection (most reliable).** A foreground service observes the
  official AndroidX [`CarConnection`](https://developer.android.com/reference/androidx/car/app/connection/CarConnection)
  signal, so it catches **USB-only** setups with no Bluetooth too. Keeps a quiet,
  low-priority notification while listening (a requirement for foreground
  services). Restarts after reboot.

Both modes play on the **media** audio stream, so while Android Auto is active
the audio is routed to the car speakers. The file is chosen through the Storage
Access Framework (no broad storage permission) and the grant persists across
reboots.

## Features

- Connect sound + optional **disconnect** sound.
- **Volume** and **lead-in delay** (helps if the first moment is clipped before
  audio routing settles).
- **Choose your car** from paired Bluetooth devices (Bluetooth mode).
- **Improve background reliability** button — requests battery-optimization
  exemption so aggressive OEMs don't kill the app.

## Build

Open the `AndroidAutoSound` folder in **Android Studio**, let Gradle sync, then
**Run ▶** on your phone (USB debugging on) or **Build → Build APK(s)** to
sideload.

Config: `minSdk 26` (Android 8.0), `targetSdk 34` (Android 14), Kotlin, Views.

## Use

1. **Pick sound** → choose an audio file → **Test** to confirm it plays.
2. (Optional) toggle **Also play a sound on disconnect** and pick one.
3. Set **Volume** / **Lead-in delay** to taste.
4. Choose a **Trigger** mode. In Bluetooth mode, tap **Choose car** and pick your
   vehicle.
5. Tap **Improve background reliability** (recommended).
6. Turn on **Enabled**.

## Notes & limitations

- **Bluetooth mode only fires for the device you chose**, and only if the car
  connects over Bluetooth. USB-only cars with no Bluetooth: use the Android Auto
  detection mode instead.
- **First launch after install/reboot.** Android won't deliver events to a
  freshly installed app until it's opened once. Some aggressive OEM battery
  managers (Xiaomi/MIUI, Huawei, Samsung) may still kill it — use the reliability
  button, or open the app once.
- **Timing.** If the first fraction of a second gets clipped, raise the lead-in
  delay a little (or add a moment of silence to the start of your audio file).
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** and the `specialUse` foreground
  service are both restricted on Google Play and require justification if you ever
  publish. Fine for personal / sideloaded use.
- Package name is `com.example.androidautosound` — Google Play rejects
  `com.example`, so rename the applicationId before publishing.

## Project layout

```
app/src/main/
  AndroidManifest.xml               permissions, service, receivers, package queries
  java/.../MainActivity.kt          the settings screen
  java/.../SoundPrefs.kt            all persisted settings
  java/.../SoundPlayer.kt           MediaPlayer on the media stream (volume + completion)
  java/.../BluetoothReceiver.kt     Bluetooth mode: fires on the chosen car connect/disconnect
  java/.../CarConnectionService.kt  reliable mode: foreground service on CarConnection
  java/.../BootReceiver.kt          restarts the reliable-mode service after reboot
  res/layout/activity_main.xml      the single screen
```
