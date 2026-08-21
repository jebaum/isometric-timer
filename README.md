# Isometric Timer

A native Android isometric-hold interval timer — Kotlin and Compose, built to be
sideloaded onto a personal device rather than published. It sounds and vibrates
on every phase boundary, which matters because during a hold you are usually not
looking at the screen.

Everything lives in [`android/`](android/); see
[`android/README.md`](android/README.md) for the toolchain, the build and
install steps, and the design decisions behind the app.

## Build and install

```sh
cd android
./gradlew :app:testDebugUnitTest     # timing core, no device needed
./gradlew :app:assembleRelease       # -> app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Controls

- Tap **Start**, **Pause/Resume**, **Skip**, or **End routine**.
- Settings are stored only on the device. The defaults are 4 cycles, 35-second
  holds, a 5-second switch, and 90-second rests.
- Finishing a routine logs a completion; the calendar button shows the history
  and the recommended eight-hour gap before the next one.

## History

This started as a PWA at the repository root, and the Android app was written to
match its behaviour. The Android app has since gained functionality the web one
never had and is now the only implementation; the PWA and the JavaScript parity
fixture that guarded the port were removed in August 2026.
