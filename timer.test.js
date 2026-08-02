import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  DEFAULT_SETTINGS,
  KIND_HOLD,
  LABEL_LEFT,
  LABEL_REST,
  LABEL_RIGHT,
  LABEL_SWITCH,
  Routine,
  buildSchedule,
  clock,
  cumulative,
} from "./timer.js";

test("the default schedule matches the Python routine", () => {
  const phases = buildSchedule(DEFAULT_SETTINGS);
  assert.equal(phases.length, 15);
  assert.equal(phases.reduce((total, phase) => total + phase.seconds, 0), 570);
  assert.equal(phases.at(-1).label, LABEL_LEFT);
  assert.equal(phases.at(-1).kind, KIND_HOLD);
  assert.deepEqual(
    phases.map((phase) => phase.label),
    [LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT, LABEL_REST,
      LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT, LABEL_REST,
      LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT, LABEL_REST,
      LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT],
  );
});

test("custom durations are exact and the final cycle has no rest", () => {
  const phases = buildSchedule({ cycles: 2, hold: 5, switch: 2, rest: 4 });
  assert.deepEqual(phases.map((phase) => phase.seconds), [5, 2, 5, 4, 5, 2, 5]);
  assert.equal(phases.at(-1).cycle, 2);
});

test("zero switch and rest durations omit those phases", () => {
  const phases = buildSchedule({ cycles: 2, hold: 5, switch: 0, rest: 0 });
  assert.deepEqual(phases.map((phase) => phase.label),
    [LABEL_RIGHT, LABEL_LEFT, LABEL_RIGHT, LABEL_LEFT]);
});

test("invalid schedule values are rejected", () => {
  for (const settings of [
    { cycles: 0, hold: 35, switch: 5, rest: 90 },
    { cycles: 4, hold: 0, switch: 5, rest: 90 },
    { cycles: 4, hold: 35, switch: -1, rest: 90 },
    { cycles: 4, hold: 35, switch: 5, rest: -1 },
    { cycles: 2.5, hold: 35, switch: 5, rest: 90 },
  ]) {
    assert.throws(() => buildSchedule(settings), RangeError);
  }
});

test("cumulative marks and clock formatting line up", () => {
  const phases = buildSchedule(DEFAULT_SETTINGS);
  assert.equal(cumulative(phases).at(-1), 570);
  assert.equal(clock(570), "9:30");
  assert.equal(clock(75), "1:15");
  assert.equal(clock(-2), "0:00");
});

test("a routine waits for the first start", () => {
  let time = 10;
  const routine = new Routine(buildSchedule(DEFAULT_SETTINGS), { now: () => time });
  time = 110;
  assert.equal(routine.elapsed(), 0);
  assert.equal(routine.snapshot().secondsLeft, 35);
  assert.equal(routine.snapshot().started, false);

  routine.togglePause();
  time = 111;
  assert.equal(routine.elapsed(), 1);
  assert.equal(routine.snapshot().started, true);
});

test("pause, resume, and skip preserve monotonic timing", () => {
  let time = 0;
  const routine = new Routine(buildSchedule(DEFAULT_SETTINGS), {
    now: () => time,
    startPaused: false,
  });

  time = 10;
  routine.togglePause();
  time = 110;
  assert.equal(routine.elapsed(), 10);

  routine.skip();
  assert.equal(routine.elapsed(), 35);
  assert.equal(routine.snapshot().phase.label, LABEL_SWITCH);

  routine.togglePause();
  time = 115;
  assert.equal(routine.elapsed(), 40);
  assert.equal(routine.snapshot().phase.label, LABEL_LEFT);
});

test("skipping through every phase completes at zero", () => {
  let time = 0;
  const routine = new Routine(buildSchedule(DEFAULT_SETTINGS), {
    now: () => time,
    startPaused: false,
  });
  for (let index = 0; index < routine.phases.length + 5; index += 1) routine.skip();
  const snapshot = routine.snapshot();
  assert.equal(snapshot.done, true);
  assert.equal(snapshot.secondsLeft, 0);
  assert.equal(snapshot.totalLeft, 0);
  assert.equal(snapshot.next, "DONE");
});

test("manifest contains installable icons and every cached shell file exists", async () => {
  const root = fileURLToPath(new URL(".", import.meta.url));
  const manifest = JSON.parse(await readFile(new URL("./manifest.webmanifest", import.meta.url)));
  assert.equal(manifest.display, "standalone");
  assert.deepEqual(manifest.icons.map((icon) => icon.sizes), ["192x192", "512x512"]);

  const worker = await readFile(new URL("./sw.js", import.meta.url), "utf8");
  const cachedPaths = [...worker.matchAll(/^\s+"\.\/(.+)",$/gm)].map((match) => match[1]);
  for (const relativePath of cachedPaths) {
    await readFile(new URL(relativePath, new URL(`file://${root}/`)));
  }
});

// The Android port is verified against a fixture generated from this file, so a
// change here that the Kotlin has not matched must fail on this side too --
// otherwise JsParityTest keeps passing against a stale reference.
test("the Android parity fixture still matches this implementation", async () => {
  const base = new URL("./android/app/src/test/resources/", import.meta.url);
  const generator = fileURLToPath(new URL("./js-reference-drive.mjs", base));
  const checkedIn = await readFile(new URL("./js-reference-drive.txt", base), "utf8");

  const regenerated = execFileSync(process.execPath, [generator], { encoding: "utf8" });

  assert.equal(
    regenerated.trimEnd(),
    checkedIn.trimEnd(),
    "timer.js changed without regenerating the Android parity fixture. Run:\n"
      + "  node android/app/src/test/resources/js-reference-drive.mjs"
      + " > android/app/src/test/resources/js-reference-drive.txt\n"
      + "then re-run the Kotlin suite to see whether the port needs the same change.",
  );
});
