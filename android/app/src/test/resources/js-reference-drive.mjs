// Regenerate the fixture with:
//   node app/src/test/resources/js-reference-drive.mjs > app/src/test/resources/js-reference-drive.txt
import { Routine, buildSchedule, clock } from "../../../../../timer.js";

// Deterministic drive of the JS reference so the Kotlin port can be diffed
// against it snapshot-for-snapshot.
const settings = { cycles: 2, hold: 5, switch: 2, rest: 4 };

let time = 4223.456;
const routine = new Routine(buildSchedule(settings), { now: () => time });

const lines = [];
function record(tag) {
  const s = routine.snapshot();
  lines.push([
    tag,
    time.toFixed(3),
    s.phase.label,
    s.phase.kind,
    s.secondsLeft,
    s.progress.toFixed(6),
    s.totalLeft,
    s.cycle,
    s.cycles,
    s.next,
    s.paused,
    s.started,
    s.done,
    clock(s.totalLeft),
  ].join("|"));
}

record("init");
routine.togglePause(); // start
record("start");

let step = 0;
while (step < 700) {
  time += 0.05;
  step += 1;
  if (step === 40) { routine.togglePause(); record("pause"); }
  if (step === 60) { routine.togglePause(); record("resume"); }
  if (step === 120) { routine.skip(); record("skip"); }
  if (step === 121) { routine.skip(); record("skip2"); }
  if (step % 7 === 0) record(`t${step}`);
}
record("end");

// Skip storm on a fresh routine from a fractional origin.
let t2 = 987654.321;
const r2 = new Routine(buildSchedule({ cycles: 4, hold: 35, switch: 5, rest: 90 }), {
  now: () => t2,
  startPaused: false,
});
for (let i = 0; i < r2.phases.length + 5; i += 1) {
  t2 += 0.0166666;
  r2.skip();
  const s = r2.snapshot();
  lines.push(["skipstorm", i, s.phase.label, s.secondsLeft, s.totalLeft, s.done, r2.elapsed().toFixed(9)].join("|"));
}

console.log(lines.join("\n"));
