// SafePath AI - Guardian Live Tracker (separate dashboard)
const API = getApiBase();
const sessionId = new URLSearchParams(location.search).get("sessionId");
const viewKey = new URLSearchParams(location.search).get("key");

if (!sessionId || !viewKey) {
  alert("Invalid tracking link. Ask the traveler to copy a fresh guardian link from the app.");
}

const map = L.map("map", { zoomControl: true }).setView([20.5937, 78.9629], 5);
L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  attribution: "OpenStreetMap contributors",
  maxZoom: 19,
}).addTo(map);

window.addEventListener("load", () => {
  setTimeout(() => map.invalidateSize(), 100);
});
window.addEventListener("resize", () => map.invalidateSize());

let travelerMarker = null;
let routeLine = null;
let breadcrumbLine = null;
const breadcrumbCoords = [];
let lastStatus = null;
let routeRendered = false;
let seenEvents = new Set();
let latestData = null;

if (sessionId) {
  addLog("Connecting to SafePath live tracking…", "info");
  pollSession();
  setInterval(pollSession, 3000);
}

async function pollSession() {
  try {
    const res = await fetch(
      `${API}/api/session?sessionId=${encodeURIComponent(sessionId)}&key=${encodeURIComponent(viewKey || "")}`,
    );
    if (res.status === 403) {
      addLog("Invalid or expired tracking link.", "alert");
      return;
    }
    if (!res.ok) throw new Error(`Server returned ${res.status}`);
    const data = await res.json();

    if (data.status !== "success" || data.currentLat === undefined) {
      return;
    }

    latestData = data;
    updateUI(data);
    processEvents(data.events || []);
  } catch (err) {
    console.error("Guardian poll failed:", err);
    addLog("Connection lost. Retrying…", "warning");
  }
}

function processEvents(events) {
  events.slice().reverse().forEach((ev) => {
    const key = `${ev.timestamp}-${ev.type}-${ev.message}`;
    if (seenEvents.has(key)) return;
    seenEvents.add(key);

    const typeMap = {
      SOS: "alert",
      DEVIATION: "warning",
      REROUTE: "warning",
      AUTO_EMERGENCY: "alert",
      SAFE_ARRIVAL: "success",
    };
    addLog(ev.message, typeMap[ev.type] || "info");

    if (ev.type === "SOS" || ev.type === "AUTO_EMERGENCY") {
      playAlertSound();
    }
  });
}

function updateUI(data) {
  const lat = +data.currentLat;
  const lng = +data.currentLng;
  const score = data.safetyScore || 50;
  const status = data.safetyStatus || "MODERATE";
  const travelerName = data.userName || "Active traveler";

  document.getElementById("currentCoords").textContent = `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
  document.getElementById("lastUpdated").textContent = new Date(data.lastUpdated).toLocaleTimeString();
  document.getElementById("travelerName").textContent = travelerName;
  document.getElementById("scoreVal").textContent = score.toFixed(0);

  const badge = document.getElementById("statusBadge");
  const textEl = document.getElementById("statusText");
  const statusClass = status === "RISKY" ? "danger" : status.toLowerCase();
  badge.className = `status-badge ${statusClass}`;
  textEl.textContent = status;

  if (status === "RISKY" && lastStatus !== "RISKY") {
    playAlertSound();
  }
  lastStatus = status;

  const newLatLng = [lat, lng];
  if (!travelerMarker) {
    travelerMarker = L.circleMarker(newLatLng, {
      radius: 12,
      color: "#14b8a6",
      fillColor: "#2dd4bf",
      fillOpacity: 0.9,
      weight: 3,
    }).addTo(map);
    travelerMarker.bindPopup("<b>Traveler</b><br>Live location").openPopup();
    map.setView(newLatLng, 15);
  } else {
    travelerMarker.setLatLng(newLatLng);
  }

  const prev = breadcrumbCoords[breadcrumbCoords.length - 1];
  if (!prev || prev[0] !== lat || prev[1] !== lng) {
    breadcrumbCoords.push(newLatLng);
    if (breadcrumbLine) {
      breadcrumbLine.setLatLngs(breadcrumbCoords);
    } else {
      breadcrumbLine = L.polyline(breadcrumbCoords, {
        color: "#a855f7",
        weight: 4,
        dashArray: "6,6",
        opacity: 0.85,
      }).addTo(map);
    }
  }

  if (data.routePath && data.routePath.length >= 4) {
    const routeCoords = [];
    for (let i = 0; i < data.routePath.length; i += 2) {
      routeCoords.push([data.routePath[i], data.routePath[i + 1]]);
    }
    if (routeLine) {
      routeLine.setLatLngs(routeCoords);
    } else {
      routeLine = L.polyline(routeCoords, {
        color: "#2563eb",
        weight: 6,
        opacity: 0.55,
      }).addTo(map);
      addLog("Planned route loaded on map.", "success");
    }
    if (!routeRendered) {
      map.fitBounds(routeLine.getBounds(), { padding: [50, 50] });
      routeRendered = true;
    }
  }
}

function addLog(text, type = "info") {
  const container = document.getElementById("logContainer");
  const placeholder = container.querySelector(".log-item:not([data-live])");
  if (placeholder) placeholder.remove();

  const item = document.createElement("div");
  item.className = `log-item ${type}`;
  item.dataset.live = "1";

  const time = document.createElement("div");
  time.className = "log-time";
  time.textContent = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });

  const msg = document.createElement("div");
  msg.className = "log-text";
  msg.textContent = text;

  item.appendChild(time);
  item.appendChild(msg);
  container.insertBefore(item, container.firstChild);

  while (container.children.length > 40) {
    container.removeChild(container.lastChild);
  }
}

function playAlertSound() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "sine";
    osc.frequency.setValueAtTime(880, ctx.currentTime);
    osc.frequency.setValueAtTime(440, ctx.currentTime + 0.15);
    gain.gain.setValueAtTime(0.5, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.4);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.4);
  } catch (e) {
    console.warn("Audio warning could not be played:", e);
  }
}
