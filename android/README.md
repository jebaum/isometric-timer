# Isometric Timer — Android app

A native Kotlin/Compose rewrite of the PWA in the repository root, built to be
sideloaded onto a personal device rather than published.

## Why a native app

The timing core ported almost unchanged, but three things the web app fought
with stop being problems:

| Web | Native |
| --- | --- |
| Wake Lock API, reacquired on `visibilitychange`, with a footer reporting whether it worked | `view.keepScreenOn`, one line |
| `setTimeout(render, 50)` paused and resumed around `document.hidden` | A `withFrameNanos` loop that parks itself when frames stop arriving |
| Visual 3-second warning only | Sound and vibration on every phase boundary |

That last row is the actual reason to do this: during a hold you are usually not
looking at the screen.

## Layout

```
app/src/main/java/dev/jebaum/isometric/
  timer/Schedule.kt        Port of the root timer.js — pure, no Android imports
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
  timer/RoutineTest.kt        Port of the root timer.test.js
  timer/RoutineEdgeTest.kt    Boundary arithmetic: exact marks, skip, drift, clamping
  timer/SchedulePropertyTest.kt  totalSeconds() vs buildSchedule() over the valid domain
  timer/JsParityTest.kt       Snapshot-for-snapshot diff against real timer.js output
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

## Keeping the two implementations honest

`timer.js` and `timer/Schedule.kt` + `timer/Routine.kt` are parallel
implementations of the same routine logic, and `timer.test.js` and
`RoutineTest.kt` are parallel suites. A change to how a routine is built or
timed belongs in both, and both suites should be run:

```sh
npm test                                    # from the repository root
cd android && ./gradlew :app:testDebugUnitTest
```

One case does not survive the port: the web suite rejects `cycles: 2.5`, which
`Int` makes unrepresentable. One case has no web counterpart: the web suite's
last test checks the web manifest and service-worker cache list.

Hand-copied expectations would drift, so `timer/JsParityTest.kt` does not rely
on them. It drives the Kotlin `Routine` through a 127-step script — fractional
clock origins, 50 ms ticks, a pause, a resume, skips, run to completion, then a
skip storm — and diffs **every** snapshot field against
`app/src/test/resources/js-reference-drive.txt`, which was generated by running
the real `timer.js` under Node. A behavioural divergence between the two
implementations shows up as a line-by-line mismatch.

The web suite guards against the fixture going stale: `npm test` regenerates it
and fails if it differs from the checked-in copy, so a `timer.js` change that the
Kotlin has not matched turns the JS build red rather than quietly leaving
`JsParityTest` passing against an old reference. Regenerate with:

```sh
node app/src/test/resources/js-reference-drive.mjs > app/src/test/resources/js-reference-drive.txt
```

then re-run the Kotlin suite to see whether the port needs the same change.

See [ICONS.md](ICONS.md) for the launcher icon.
