# Stretch PWA

An installable Android-friendly stretching timer. It keeps a quiet routine,
precise phase timing, touch controls, and a three-second visual warning, with
no server-side code or JavaScript dependencies.

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

Visit <https://jebaum.github.io/stretch-timer/> in Chrome on Android, then use
**Install app** or **Add to Home screen** from Chrome's menu. The service worker
caches the complete app during the first visit, so later routines work offline.
While a routine is active, the app requests a screen wake lock and reacquires it
after returning from another app.

## Controls

- Tap **Start**, **Pause/Resume**, **Skip**, or **End routine**.
- A hardware keyboard retains the terminal keys: Space pauses, `s` skips, and
  `q` ends the routine.
- Settings are stored only on the device. The defaults are 4 cycles, 35-second
  holds, a 5-second switch, and 90-second rests.
