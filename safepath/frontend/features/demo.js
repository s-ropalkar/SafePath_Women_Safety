/** One-click demo for judges without GPS */
let demoMode = false;
let demoPath = [];

async function launchDemo() {
  showSpinner(true);
  try {
    const data = await fetchDemoRoute();

    document.getElementById("source").value = data.source.label;
    document.getElementById("destination").value = data.destination.label;
    demoPath = data.path || [];
    demoMode = true;

    toast("Demo mode: route loaded. Use sidebar checkpoints or Start Tracking when ready.", "info");
    await findRoute();
    await ensureSession();
    await startTripEmails();

    const btn = document.getElementById("trackBtn");
    btn.textContent = "Stop Tracking";
    btn.classList.add("danger");

    const [lat, lng] = demoPath[0] || [data.source.lat, data.source.lng];
    await handlePosition(positionFromCoords(lat, lng, 25, "demo"));
  } catch (err) {
    toast(err.message || "Demo launch failed.", "danger");
  } finally {
    showSpinner(false);
  }
}

function stopDemoMode() {
  demoMode = false;
}

document.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("launchDemoBtn");
  if (btn) btn.addEventListener("click", launchDemo);
});
