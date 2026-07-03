/** One-click demo for judges without GPS */
let demoMode = false;
let demoTimer = null;
let demoPath = [];
let demoIndex = 0;

async function launchDemo() {
  showSpinner(true);
  try {
    const data = await fetchDemoRoute();

    document.getElementById("source").value = data.source.label;
    document.getElementById("destination").value = data.destination.label;
    demoPath = data.path || [];
    demoIndex = 0;
    demoMode = true;

    toast("Demo mode: predefined Delhi trip loaded.", "info");
    await findRoute();
    await ensureSession();
    await startTripEmails();

    const btn = document.getElementById("trackBtn");
    btn.textContent = "Stop Tracking";
    btn.classList.add("danger");

    const [lat, lng] = demoPath[0] || [data.source.lat, data.source.lng];
    await handlePosition(positionFromCoords(lat, lng, 25, "demo"));
    startDemoMovement();
  } catch (err) {
    toast(err.message || "Demo launch failed.", "danger");
  } finally {
    showSpinner(false);
  }
}

function startDemoMovement() {
  stopDemoMovement();
  if (!demoPath.length) return;
  demoIndex = 1;
  demoTimer = setInterval(async () => {
    if (!demoMode || demoIndex >= demoPath.length) {
      stopDemoMovement();
      return;
    }
    const [lat, lng] = demoPath[demoIndex];
    demoIndex += 1;
    await handlePosition(positionFromCoords(lat, lng, 25, "demo"));
    if (demoIndex >= demoPath.length) {
      toast("Demo route complete.", "success");
      stopDemoMovement();
    }
  }, 3500);
}

function stopDemoMovement() {
  if (demoTimer) clearInterval(demoTimer);
  demoTimer = null;
}

function stopDemoMode() {
  demoMode = false;
  stopDemoMovement();
}

document.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("launchDemoBtn");
  if (btn) btn.addEventListener("click", launchDemo);
});
