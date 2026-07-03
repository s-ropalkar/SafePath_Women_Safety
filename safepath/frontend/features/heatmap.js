/** Community unsafe zone heatmap from MySQL */
let heatmapLayer = null;
let heatmapVisible = false;
let heatLocations = [];

const HEAT_GRADIENT = {
  0.25: "#eab308",
  0.5: "#f97316",
  0.75: "#ef4444",
  1.0: "#991b1b",
};

function heatmapStyle(reportCount) {
  if (reportCount >= 5) return { color: "#ef4444", fillColor: "#ef4444", label: "High Risk", level: "high" };
  if (reportCount >= 2) return { color: "#f97316", fillColor: "#f97316", label: "Medium Risk", level: "medium" };
  return { color: "#eab308", fillColor: "#eab308", label: "Low Risk", level: "low" };
}

function heatIntensity(reportCount) {
  return Math.min(1, 0.4 + (reportCount || 1) * 0.12);
}

function updateHeatmapButtonLabel() {
  const btn = document.getElementById("toggleHeatmapBtn");
  if (!btn) return;
  const n = heatLocations.length;
  const verb = heatmapVisible ? "Hide" : "Show";
  btn.textContent = n > 0 ? `${verb} Unsafe Heatmap (${n})` : `${verb} Unsafe Heatmap`;
  btn.style.opacity = heatmapVisible ? "1" : "0.65";
  btn.classList.toggle("heatmap-on", heatmapVisible);
}

function fitHeatmapBounds(locations) {
  if (!locations.length) return;
  const bounds = L.latLngBounds(locations.map((l) => [l.latitude, l.longitude]));
  map.fitBounds(bounds.pad(0.35), { maxZoom: 16, animate: true });
}

function renderHeatmap(locations) {
  if (heatmapLayer) {
    map.removeLayer(heatmapLayer);
    heatmapLayer = null;
  }

  heatLocations = locations || [];

  if (!heatLocations.length) {
    updateHeatmapButtonLabel();
    return;
  }

  heatmapLayer = L.layerGroup();

  if (typeof L.heatLayer === "function") {
    const points = heatLocations.map((loc) => [
      loc.latitude,
      loc.longitude,
      heatIntensity(loc.reportCount),
    ]);
    L.heatLayer(points, {
      radius: 50,
      blur: 32,
      maxZoom: 18,
      minOpacity: 0.45,
      gradient: HEAT_GRADIENT,
    }).addTo(heatmapLayer);
  }

  heatLocations.forEach((loc) => {
    const style = heatmapStyle(loc.reportCount || 1);
    const radiusM = 200 + Math.min(loc.reportCount || 1, 15) * 80;
    L.circle([loc.latitude, loc.longitude], {
      radius: radiusM,
      color: style.color,
      fillColor: style.fillColor,
      fillOpacity: 0.15,
      weight: 2,
      opacity: 0.9,
    })
      .bindPopup(heatmapPopupHtml(loc, style))
      .addTo(heatmapLayer);

    if (loc.confirmed || (loc.reportCount || 0) >= 3) {
      L.marker([loc.latitude, loc.longitude], {
        icon: L.divIcon({
          className: "heatmap-confirmed-pin",
          html: '<span class="heatmap-confirmed-badge">✓</span>',
          iconSize: [22, 22],
          iconAnchor: [11, 11],
        }),
      })
        .bindPopup(heatmapPopupHtml(loc, style))
        .addTo(heatmapLayer);
    }

    L.circleMarker([loc.latitude, loc.longitude], {
      radius: 8,
      color: "#fff",
      fillColor: style.fillColor,
      fillOpacity: 1,
      weight: 2,
    })
      .bindPopup(heatmapPopupHtml(loc, style))
      .addTo(heatmapLayer);
  });

  if (heatmapVisible) {
    heatmapLayer.addTo(map);
    if (heatmapLayer.bringToFront) heatmapLayer.bringToFront();
  }

  updateHeatmapButtonLabel();
}

function heatmapPopupHtml(loc, style) {
  const confirmed = loc.confirmed || (loc.reportCount || 0) >= 3;
  const badge = confirmed
    ? '<span class="heatmap-confirmed-badge">Confirmed zone</span><br>'
    : '<span style="opacity:0.8">Unverified (needs 3+ reports)</span><br>';
  const predicted = loc.predictedRiskProbability != null
    ? `<br>AI predicted risk: <b>${loc.predictedRiskProbability}%</b>`
    : "";
  return `<b>${style.label}</b><br>${badge}Reports: ${loc.reportCount || 1}${predicted}<br>${escapeHtml(loc.reason || "Community report")}`;
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

async function loadUnsafeHeatmap() {
  try {
    const res = await fetch(`${API}/api/unsafe-locations`);
    const { data, ok } = await parseApiJson(res);
    if (!ok || data.status !== "success") {
      if (heatmapVisible) toast("Could not load unsafe zone data.", "warn");
      return;
    }
    renderHeatmap(data.locations || []);
    if (heatmapVisible && heatLocations.length) {
      if (heatmapLayer) {
        heatmapLayer.addTo(map);
        if (heatmapLayer.bringToFront) heatmapLayer.bringToFront();
      }
    }
  } catch (err) {
    console.warn("Heatmap load failed:", err);
    if (heatmapVisible) toast(err.message || "Heatmap load failed.", "warn");
  }
}

async function toggleHeatmap() {
  heatmapVisible = !heatmapVisible;

  if (heatmapVisible) {
    await loadUnsafeHeatmap();
    if (!heatLocations.length) {
      heatmapVisible = false;
      updateHeatmapButtonLabel();
      toast("No community unsafe reports yet. Use Report Unsafe Location.", "info");
      return;
    }
    if (heatmapLayer) {
      heatmapLayer.addTo(map);
      if (heatmapLayer.bringToFront) heatmapLayer.bringToFront();
      fitHeatmapBounds(heatLocations);
      toast(`Showing ${heatLocations.length} unsafe zone${heatLocations.length === 1 ? "" : "s"}.`, "success");
    }
  } else if (heatmapLayer && map.hasLayer(heatmapLayer)) {
    map.removeLayer(heatmapLayer);
  }

  updateHeatmapButtonLabel();
}

document.addEventListener("DOMContentLoaded", () => {
  loadUnsafeHeatmap();
  const btn = document.getElementById("toggleHeatmapBtn");
  if (btn) btn.addEventListener("click", toggleHeatmap);
});
