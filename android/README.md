# Isometric Timer — Android app

A native Kotlin/Compose isometric-hold interval timer, built to be sideloaded
onto a personal device rather than published.

## Why a native app

The app sounds and vibrates on every phase boundary. That is the actual reason
it exists: during a hold you are usually not looking at the screen. Being native
also keeps the screen awake with a single `view.keepScreenOn`, and drives the
countdown from a `withFrameNanos` loop that parks itself when frames stop
arriving rather than from a timer that has to be torn down and rebuilt around
visibility changes.

## Layout

```
app/src/main/java/dev/jebaum/isometric/
  timer/Schedule.kt        Routine construction — pure, no Android imports
  timer/Routine.kt         Monotonic elapsed-time model
  RoutineViewModel.kt      Survives rotation; decides which cue each tick implies
  SettingsStore.kt         Interface + SharedPreferences implementation
  CompletionHistoryStore.kt  SQLite completion log and its testable interface
  cues/CuePlayer.kt        Cue types and the player interface
  cues/AndroidCuePlayer.kt ToneGenerator + VibrationEffect
  ui/HistoryDialog.kt      Month calendar derived from completion timestamps
  ui/                      Compose screen, settings dialog, theme, icons
app/src/test/java/dev/jebaum/isometric/
  CueDispatchTest.kt          The cue state machine's ordinary paths
  RoutineViewModelTest.kt     Its edges: pause, completion, toggle histories, lifecycle
  PreferenceSaves.kt          FakeSettingsStore and the shared Save helpers
  timer/RoutineTest.kt        Schedule construction, validation, and clock formatting
  timer/RoutineEdgeTest.kt    Boundary arithmetic: exact marks, skip, drift, clamping
  timer/ScheduleEdgeTest.kt   The edges of the settings dialog's accepted range
```

`timer/` deliberately has no Android dependencies. Neither does
`RoutineViewModel` — the clock, the settings store and the cue player all arrive
through its constructor, which is what lets the cue state machine be driven from
a plain JVM test instead of needing a device. The whole suite runs in about a
second:

```sh
./gradlew :app:testDebugUnitTest
```

`ui/TimerScreen.kt` splits into a stateful `TimerScreen` that owns the effects
and a stateless `TimerContent`, so every routine state — ready, running, the
closing-seconds warning, rest, complete — has an `@Preview` rather than needing
nine minutes of waiting to reach.

## Routine history

Finishing a routine appends its Unix timestamp to the local SQLite
`completions` table. Ending an unfinished routine does not count. The calendar
button shows one mark for one completion that day and two marks for two or more.

After a completion, the ready screen shows when the recommended eight-hour gap
ends. Starting sooner opens a warning with the choice to wait or start anyway;
the interval is advisory rather than a hard lock. The routine itself continues
to use a monotonic clock, while history deliberately uses wall-clock timestamps
so it survives reboots and can be placed on a calendar. Those timestamps are
stored as UTC Unix milliseconds: the eight-hour gap therefore measures real
elapsed time across timezone changes, while status times and calendar dates are
derived using the phone's current timezone.

## Portrait only

The activity sets `android:screenOrientation="portrait"`. Landscape was tried on
the device: the routine survived rotation and the countdown resized correctly,
but a 411dp-tall window still pushes Pause and Skip below the fold, and scrolling
to pause mid-hold is not acceptable. Android 16 ignores an orientation lock only
on displays 600dp and wider, so it genuinely applies on a phone.

The `verticalScroll` fallback in `TimerContent` stays regardless — a large system
font or display-size setting can overflow a portrait window too.

## Toolchain

Nothing here needs Android Studio. On Arch:

```sh
sudo pacman -S jdk21-openjdk gradle

# SDK command line tools -> ~/Android/Sdk/cmdline-tools/latest/
curl -O https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
mkdir -p ~/Android/Sdk/cmdline-tools
unzip -q commandlinetools-linux-15859902_latest.zip
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

export ANDROID_HOME=~/Android/Sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```

For fish, the persistent bits:

```fish
set -Ux ANDROID_HOME "$HOME/Android/Sdk"
set -Ux JAVA_HOME /usr/lib/jvm/default
fish_add_path -U "$HOME/Android/Sdk/platform-tools"
fish_add_path -U "$HOME/Android/Sdk/cmdline-tools/latest/bin"
```

`local.properties` (gitignored) points the build at the SDK, so the env var is
only needed for `adb` and `sdkmanager`.

`gradle` from pacman is only used once, to generate the wrapper. Everything
after that goes through `./gradlew`.

## Build and install

```sh
./gradlew :app:testDebugUnitTest     # timing core, no device needed
./gradlew :app:assembleRelease       # -> app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without a cable, copy the APK to the phone and tap it; the file manager will ask
for permission to install unknown apps.

`assembleDebug` also works and skips R8, but the debug APK is ~30 MB against the
release build's ~2 MB, and it is signed with a throwaway debug key. It installs
as `dev.jebaum.isometric.debug` so it can sit alongside the real app rather than
failing to install over it on a signature mismatch.

## SDK levels

| | | |
| --- | --- | --- |
| `minSdk` | 36 | The Galaxy S25 runs Android 16. A higher value simply will not install. |
| `targetSdk` | 36 | Deliberately not 37 — see below. |
| `compileSdk` | 37 | Newest stable; costs nothing to build against. |

`targetSdk` behaviour changes are gated *inside the platform* (`if (targetSdk >=
X)`), so that code does not exist on Android 16 and setting 37 would change
nothing today. It would, however, silently switch on a batch of untested
behaviour the moment the phone takes an Android 17 update. Bump it deliberately,
once there is a device to test it on. Lint's `OldTargetApi` warning is the
expected consequence.

## Signing

The release keystore lives at `~/.android-keystores/isometric.jks`, outside the
repository. `keystore.properties` holds the path and passwords and is gitignored;
`app/build.gradle.kts` reads it if present and produces an unsigned release
otherwise.

**Keep both.** Android identifies an app by its signing key, so a differently
signed APK cannot upgrade an installed one — you would have to uninstall first,
which wipes saved settings.

## Kotlin version

Pinned to 2.2.10 rather than the newest release. AGP 9 compiles Kotlin itself
and ships KGP 2.2.10, and the Compose compiler plugin has to match the Kotlin
compiler exactly. Bump it only alongside AGP.

## Lint and warnings

`lint { warningsAsErrors = true }` and `allWarningsAsErrors` are both on, so a
new warning fails the build rather than joining a pile of accepted noise. Three
checks are disabled and one demoted, each a documented decision:

| Check | Why |
| --- | --- |
| `OldTargetApi` | `targetSdk` held at 36 on purpose, above. |
| `ObsoleteSdkInt` | Its suggested fix makes `aapt2` fail to resolve the icon; see [ICONS.md](ICONS.md). |
| `MonochromeLauncherIcon` | The wireframe artwork cannot survive being tinted flat. |
| `NewerVersionAvailable` / `GradleDependency` | Demoted to informational, not disabled: Kotlin must track AGP's KGP, but the notice is still worth seeing for the AndroidX dependencies. |

See [ICONS.md](ICONS.md) for the launcher icon.
