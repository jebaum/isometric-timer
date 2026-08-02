export const DEFAULT_SETTINGS = Object.freeze({
  cycles: 4,
  hold: 35,
  switch: 5,
  rest: 90,
});

export const KIND_HOLD = "hold";
export const KIND_SWITCH = "switch";
export const KIND_REST = "rest";

export const LABEL_RIGHT = "RIGHT SIDE";
export const LABEL_SWITCH = "SWITCH";
export const LABEL_LEFT = "LEFT SIDE";
export const LABEL_REST = "REST";

function wholeNumber(name, value, minimum) {
  if (!Number.isInteger(value) || value < minimum) {
    const qualifier = minimum === 0 ? "zero or greater" : "greater than zero";
    throw new RangeError(`${name} must be a whole number ${qualifier}`);
  }
}

export function buildSchedule({ cycles, hold, switch: switchSeconds, rest }) {
  wholeNumber("cycles", cycles, 1);
  wholeNumber("hold", hold, 1);
  wholeNumber("switch", switchSeconds, 0);
  wholeNumber("rest", rest, 0);

  const phases = [];
  for (let index = 0; index < cycles; index += 1) {
    const cycle = index + 1;
    phases.push({ label: LABEL_RIGHT, seconds: hold, kind: KIND_HOLD, cycle });
    if (switchSeconds > 0) {
      phases.push({
        label: LABEL_SWITCH,
        seconds: switchSeconds,
        kind: KIND_SWITCH,
        cycle,
      });
    }
    phases.push({ label: LABEL_LEFT, seconds: hold, kind: KIND_HOLD, cycle });
    if (index < cycles - 1 && rest > 0) {
      phases.push({ label: LABEL_REST, seconds: rest, kind: KIND_REST, cycle });
    }
  }
  return phases;
}

export function cumulative(phases) {
  const marks = [0];
  for (const phase of phases) {
    marks.push(marks.at(-1) + phase.seconds);
  }
  return marks;
}

export function clock(seconds) {
  const rounded = Math.max(0, Math.trunc(seconds));
  return `${Math.trunc(rounded / 60)}:${String(rounded % 60).padStart(2, "0")}`;
}

function bisectRight(values, target) {
  let low = 0;
  let high = values.length;
  while (low < high) {
    const middle = Math.trunc((low + high) / 2);
    if (target < values[middle]) {
      high = middle;
    } else {
      low = middle + 1;
    }
  }
  return low;
}

export class Routine {
  constructor(
    phases,
    { now = () => performance.now() / 1000, startPaused = true } = {},
  ) {
    if (phases.length === 0) {
      throw new RangeError("a routine needs at least one phase");
    }

    this.phases = phases;
    this.marks = cumulative(phases);
    this.total = this.marks.at(-1);
    this.cycles = phases.at(-1).cycle;
    this.now = now;
    this.origin = now();
    this.offset = 0;
    this.pausedAt = startPaused ? this.origin : null;
    this.started = !startPaused;
  }

  get paused() {
    return this.pausedAt !== null;
  }

  elapsed() {
    const current = this.pausedAt ?? this.now();
    return current - this.origin - this.offset;
  }

  done() {
    return this.elapsed() >= this.total;
  }

  indexAt(elapsed) {
    return Math.min(bisectRight(this.marks, elapsed) - 1, this.phases.length - 1);
  }

  togglePause() {
    if (this.done()) return;

    if (this.pausedAt === null) {
      this.pausedAt = this.now();
    } else {
      this.offset += this.now() - this.pausedAt;
      this.pausedAt = null;
      this.started = true;
    }
  }

  skip() {
    if (this.done()) return;

    const elapsed = this.elapsed();
    const index = this.indexAt(elapsed);
    this.offset -= this.marks[index + 1] - elapsed;
  }

  snapshot() {
    const elapsed = this.elapsed();
    const done = elapsed >= this.total;
    const index = this.indexAt(elapsed);
    const phase = this.phases[index];
    const into = done ? phase.seconds : elapsed - this.marks[index];

    return {
      phase,
      next: done ? "DONE" : (this.phases[index + 1]?.label ?? "DONE"),
      secondsLeft: done ? 0 : Math.max(0, Math.ceil(phase.seconds - into)),
      progress: done ? 1 : Math.max(0, Math.min(1, into / phase.seconds)),
      totalLeft: done ? 0 : Math.max(0, Math.ceil(this.total - elapsed)),
      cycle: phase.cycle,
      cycles: this.cycles,
      paused: this.paused,
      started: this.started,
      done,
    };
  }
}
