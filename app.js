import { DEFAULT_SETTINGS, Routine, buildSchedule, clock } from "./timer.js";

const STORAGE_KEY = "isometric-settings-v1";
const FRAME_INTERVAL = 50;
const WARNING_SECONDS = 3;

const elements = {
  status: document.querySelector("#run-status"),
  phase: document.querySelector("#phase-label"),
  countdown: document.querySelector("#countdown"),
  progress: document.querySelector("#progress-fill"),
  total: document.querySelector("#total-left"),
  cycle: document.querySelector("#cycle-count"),
  next: document.querySelector("#next-label"),
  start: document.querySelector("#start-button"),
  skip: document.querySelector("#skip-button"),
  reset: document.querySelector("#reset-button"),
  install: document.querySelector("#install-button"),
  settings: document.querySelector("#settings-button"),
  dialog: document.querySelector("#settings-dialog"),
  settingsForm: document.querySelector("#settings-form"),
  closeSettings: document.querySelector("#close-settings"),
  defaults: document.querySelector("#restore-defaults"),
  preview: document.querySelector("#routine-preview"),
  wakeDot: document.querySelector("#wake-dot"),
  wakeStatus: document.querySelector("#wake-status"),
  inputs: {
    cycles: document.querySelector("#cycles-input"),
    hold: document.querySelector("#hold-input"),
    switch: document.querySelector("#switch-input"),
    rest: document.querySelector("#rest-input"),
  },
};

function validSettings(candidate) {
  try {
    buildSchedule(candidate);
    return true;
  } catch {
    return false;
  }
}

function loadSettings() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (validSettings(stored)) return stored;
  } catch {
    // Private browsing and corrupted preferences both fall back harmlessly.
  }
  return { ...DEFAULT_SETTINGS };
}

function settingsFromInputs() {
  return Object.fromEntries(
    Object.entries(elements.inputs).map(([name, input]) => [name, Number(input.value)]),
  );
}

function fillSettingsForm(values) {
  for (const [name, input] of Object.entries(elements.inputs)) {
    input.value = values[name];
  }
  updateRoutinePreview();
}

function updateRoutinePreview() {
  const candidate = settingsFromInputs();
  if (!elements.settingsForm.checkValidity() || !validSettings(candidate)) {
    elements.preview.textContent = "Enter valid whole-second durations";
    return;
  }
  const total = buildSchedule(candidate).reduce((sum, phase) => sum + phase.seconds, 0);
  const noun = candidate.cycles === 1 ? "cycle" : "cycles";
  elements.preview.textContent = `${candidate.cycles} ${noun} · ${clock(total)} total`;
}

let settings = loadSettings();
let routine = new Routine(buildSchedule(settings));
let frameTimer = null;
let completionHandled = false;
let wakeLock = null;
let wakeLockPending = false;
let wakeLockFailed = false;
let installPrompt = null;

function routineIsActive(snapshot = routine.snapshot()) {
  return snapshot.started && !snapshot.done;
}

function updateWakeStatus(snapshot) {
  const supported = "wakeLock" in navigator;
  elements.wakeDot.classList.toggle("is-active", Boolean(wakeLock));

  if (wakeLock) {
    elements.wakeStatus.textContent = "Screen will stay awake";
  } else if (routineIsActive(snapshot) && wakeLockPending) {
    elements.wakeStatus.textContent = "Keeping screen awake…";
  } else if (routineIsActive(snapshot) && (!supported || wakeLockFailed)) {
    elements.wakeStatus.textContent = "Screen wake lock unavailable";
  } else if (routineIsActive(snapshot)) {
    elements.wakeStatus.textContent = "Screen may sleep";
  } else {
    elements.wakeStatus.textContent = "Works offline after first visit";
  }
}

async function requestWakeLock() {
  if (
    wakeLock
    || wakeLockPending
    || !("wakeLock" in navigator)
    || document.visibilityState !== "visible"
    || !routineIsActive()
  ) return;

  wakeLockPending = true;
  wakeLockFailed = false;
  updateWakeStatus(routine.snapshot());
  try {
    const sentinel = await navigator.wakeLock.request("screen");
    wakeLock = sentinel;
    sentinel.addEventListener("release", () => {
      if (wakeLock === sentinel) wakeLock = null;
      updateWakeStatus(routine.snapshot());
    });
  } catch {
    wakeLockFailed = true;
  } finally {
    wakeLockPending = false;
    updateWakeStatus(routine.snapshot());
  }
}

async function releaseWakeLock() {
  const sentinel = wakeLock;
  wakeLock = null;
  if (sentinel && !sentinel.released) {
    try {
      await sentinel.release();
    } catch {
      // The platform may already have released it while the page was hidden.
    }
  }
}

function stateLabel(snapshot) {
  if (snapshot.done) return "COMPLETE";
  if (!snapshot.started) return "READY";
  if (snapshot.paused) return "PAUSED";
  return "IN PROGRESS";
}

function render() {
  const snapshot = routine.snapshot();
  const warning = snapshot.phase.kind === "hold"
    && snapshot.secondsLeft <= WARNING_SECONDS
    && !snapshot.done;

  document.body.dataset.kind = snapshot.phase.kind;
  document.body.dataset.warning = String(warning);
  document.body.dataset.state = snapshot.done
    ? "done"
    : (!snapshot.started ? "ready" : (snapshot.paused ? "paused" : "running"));

  elements.status.textContent = stateLabel(snapshot);
  elements.phase.textContent = snapshot.done ? "DONE" : snapshot.phase.label;
  elements.countdown.textContent = String(snapshot.secondsLeft).padStart(2, "0");
  elements.countdown.setAttribute("aria-label", `${snapshot.secondsLeft} seconds remaining`);
  elements.progress.style.setProperty("--progress", snapshot.progress);
  elements.total.textContent = clock(snapshot.totalLeft);
  elements.cycle.textContent = `${snapshot.cycle} / ${snapshot.cycles}`;
  elements.next.textContent = snapshot.next;

  elements.start.textContent = snapshot.done
    ? "Again"
    : (!snapshot.started ? "Start" : (snapshot.paused ? "Resume" : "Pause"));
  elements.skip.disabled = !snapshot.started || snapshot.done;
  elements.reset.disabled = !snapshot.started && !snapshot.done;
  elements.settings.disabled = routineIsActive(snapshot);

  updateWakeStatus(snapshot);

  if (snapshot.done && !completionHandled) {
    completionHandled = true;
    void releaseWakeLock();
  }

  window.clearTimeout(frameTimer);
  frameTimer = null;
  if (snapshot.started && !snapshot.paused && !snapshot.done && !document.hidden) {
    frameTimer = window.setTimeout(render, FRAME_INTERVAL);
  }
}

function resetRoutine() {
  window.clearTimeout(frameTimer);
  void releaseWakeLock();
  wakeLockFailed = false;
  completionHandled = false;
  routine = new Routine(buildSchedule(settings));
  render();
}

elements.start.addEventListener("click", () => {
  const snapshot = routine.snapshot();
  if (snapshot.done) {
    resetRoutine();
    return;
  }

  routine.togglePause();
  render();
  if (routineIsActive()) void requestWakeLock();
});

elements.skip.addEventListener("click", () => {
  routine.skip();
  render();
});

elements.reset.addEventListener("click", resetRoutine);

elements.settings.addEventListener("click", () => {
  fillSettingsForm(settings);
  elements.dialog.showModal();
});

elements.closeSettings.addEventListener("click", () => elements.dialog.close());

elements.defaults.addEventListener("click", () => fillSettingsForm(DEFAULT_SETTINGS));

for (const input of Object.values(elements.inputs)) {
  input.addEventListener("input", updateRoutinePreview);
}

elements.settingsForm.addEventListener("submit", (event) => {
  event.preventDefault();
  if (!elements.settingsForm.reportValidity()) return;

  const candidate = settingsFromInputs();
  if (!validSettings(candidate)) return;

  settings = candidate;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // The routine still works if persistent storage has been disabled.
  }
  elements.dialog.close();
  resetRoutine();
});

elements.dialog.addEventListener("click", (event) => {
  if (event.target === elements.dialog) elements.dialog.close();
});

document.addEventListener("keydown", (event) => {
  const target = event.target;
  const editing = target instanceof HTMLInputElement
    || target instanceof HTMLTextAreaElement
    || target instanceof HTMLSelectElement
    || elements.dialog.open;
  if (editing || event.metaKey || event.ctrlKey || event.altKey) return;

  if (event.code === "Space") {
    event.preventDefault();
    elements.start.click();
  } else if (event.key.toLowerCase() === "s" && !elements.skip.disabled) {
    elements.skip.click();
  } else if (event.key.toLowerCase() === "q" && !elements.reset.disabled) {
    elements.reset.click();
  }
});

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    render();
    if (routineIsActive()) void requestWakeLock();
  } else {
    window.clearTimeout(frameTimer);
  }
});

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  installPrompt = event;
  elements.install.hidden = false;
});

elements.install.addEventListener("click", async () => {
  if (!installPrompt) return;
  await installPrompt.prompt();
  await installPrompt.userChoice;
  installPrompt = null;
  elements.install.hidden = true;
});

window.addEventListener("appinstalled", () => {
  installPrompt = null;
  elements.install.hidden = true;
});

if ("serviceWorker" in navigator && location.protocol !== "file:") {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("./sw.js").catch(() => {
      // The timer itself does not depend on the offline worker.
    });
  });
}

render();
