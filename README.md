# Isometric Timer

An isometric-hold interval timer, in two implementations:

- **A PWA** at the repository root — installable, offline, no dependencies.
- **A native Android app** in [`android/`](android/) — Kotlin and Compose, built
  to be sideloaded. It adds sound and vibration on every phase boundary, which
  matters because during a hold you are usually not looking at the screen.

The routine logic is duplicated across the two (`timer.js` and
`android/…/timer/`), and so are its tests. A change to how a routine is built or
timed belongs in both.

## Run locally

Service workers require an HTTP origin, so serve the directory instead of
opening `index.html` directly:

```sh
python3 -m http.server 8000
```

Open <http://localhost:8000>. Run the timing-core tests with:

```sh
npm test
```

## Install on Android

Preferred: build and sideload the native app — see
[`android/README.md`](android/README.md).

As a PWA, visit <https://jebaum.github.io/isometric-timer/> in Chrome on
Android, then use **Install app** or **Add to Home screen** from Chrome's menu.
The service worker caches the complete app during the first visit, so later
routines work offline. While a routine is active, the app requests a screen wake
lock and reacquires it after returning from another app.

## Controls

- Tap **Start**, **Pause/Resume**, **Skip**, or **End routine**.
- A hardware keyboard retains the terminal keys: Space pauses, `s` skips, and
  `q` ends the routine.
- Settings are stored only on the device. The defaults are 4 cycles, 35-second
  holds, a 5-second switch, and 90-second rests.
