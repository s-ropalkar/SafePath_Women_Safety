// SafePath AI - live safety navigation.
// OSRM road routes -> graph -> Dijkstra variants -> safest route decision.

const API = getApiBase();
const ROUTE_COLORS = { shortest: "#2563eb", safest: "#16a34a", balanced: "#f97316" };
const DEVIATION_THRESHOLD_KM = 0.25;
const STOPPED_THRESHOLD_MS = 15 * 60 * 1000;
const TRACKING_POLL_MS = 3_000;

const map = L.map("map").setView([20.5937, 78.9629], 5);
L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  attribution: "OpenStreetMap contributors",
  maxZoom: 19,
}).addTo(map);

let sessionId = new URLSearchParams(location.search).get("sessionId");
let viewKey = sessionStorage.getItem("safepath_view_key") || "";
let currentRoutes = {};
let routeLines = {};
let currentPath = [];
let activeRouteType = "balanced";
let watchId = null;
let locationWatchId = null;
let trackingTimer = null;
let userMarker = null;
let lastPosition = null;
let lastMovementAt = Date.now();
let lastDeviationAlertAt = 0;
let lastSafetyScore = 50;
let authToken = localStorage.getItem("safepath_token") || "";
let currentUser = JSON.parse(localStorage.getItem("safepath_user") || "null");
let guardians = [];
let tripSource = "";
let tripSourceCoords = null;
let tripDestination = null;
let lastAlertLevel = "";
let stoppedPromptShown = false;
let poiData = { police: [], hospital: [], hotel: [], hostel: [], metro: [], bus: [] };
let markerLayers = {};
let permanentMarkers = [];
let unsafeLayer = L.layerGroup().addTo(map);
let poiPathLayer = L.layerGroup().addTo(map);

if (!authToken || !currentUser) {
  location.href = `${getApiBase()}/login.html`;
}

document.getElementById("findRouteBtn").addEventListener("click", findRoute);
document.getElementById("trackBtn").addEventListener("click", toggleTracking);
document.getElementById("locationBtn").addEventListener("click", toggleLocation);

setupAutocomplete("source", "sourceSuggestions");
setupAutocomplete("destination", "destinationSuggestions");
refreshAuthUI();
loadGuardians();

function getGuardianShareUrl() {
  if (!sessionId || !viewKey) {
    return `${getApiBase()}/guardian.html`;
  }
  return `${getApiBase()}/guardian.html?sessionId=${encodeURIComponent(sessionId)}&key=${encodeURIComponent(viewKey)}`;
}

function toastEmailResult(data, sentMsg, type = "success") {
  if (!data || !data.emailsQueued) {
    toast("Add guardians with email to receive alerts.", "warn");
    return;
  }
  if (data.emailDelivery === "queued" || data.emailsSent > 0) {
    toast(sentMsg || "Guardian alert queued for delivery.", type);
  } else {
    toast("Alert saved for guardian.", "info");
  }
}

function mapsLink(lat, lng) {
  return `https://www.google.com/maps?q=${lat},${lng}`;
}

function setupAutocomplete(inputId, datalistId) {
  const input = document.getElementById(inputId);
  const datalist = document.getElementById(datalistId);
  let timer;

  input.addEventListener("input", () => {
    clearTimeout(timer);
    const q = input.value.trim();
    if (q.length < 3) return;

    timer = setTimeout(async () => {
      try {
        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=5&addressdetails=1`;
        const res = await fetch(url, { headers: { "Accept-Language": "en" } });
        const data = await res.json();
        datalist.innerHTML = "";
        data.forEach((item) => {
          const opt = document.createElement("option");
          opt.value = item.display_name;
          opt.dataset.lat = item.lat;
          opt.dataset.lng = item.lon;
          datalist.appendChild(opt);
        });
      } catch (err) {
        toast("Location suggestions are unavailable right now.", "warn");
      }
    }, 300);
  });

  if (inputId === "source") {
    input.addEventListener("change", () => {
      const val = input.value.trim();
      const match = [...datalist.options].find((opt) => opt.value === val);
      if (match?.dataset.lat && match?.dataset.lng) {
        tripSourceCoords = {
          lat: +match.dataset.lat,
          lng: +match.dataset.lng,
          label: val,
          input: val,
        };
      }
    });
  }
}

async function geocode(address) {
  const parts = address.split(",").map((p) => p.trim());
  if (parts.length === 2 && !Number.isNaN(+parts[0]) && !Number.isNaN(+parts[1])) {
    return { lat: +parts[0], lng: +parts[1], label: address };
  }

  const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(address)}&format=json&limit=3&countrycodes=in&addressdetails=1`;
  const res = await fetch(url, { headers: { "Accept-Language": "en" } });
  if (!res.ok) throw new Error("Location search failed.");
  const data = await res.json();
  if (!data.length) throw new Error(`Location not found: ${address}`);
  const best = data.find((item) => item.type === "city" || item.type === "town" || item.type === "village")
    || data[0];
  return { lat: +best.lat, lng: +best.lon, label: best.display_name };
}

function osrmProfile(mode) {
  if (mode === "WALK") return "foot";
  if (mode === "BIKE") return "bike";
  return "driving";
}

async function getRoadRoutes(src, dst, mode) {
  const profile = osrmProfile(mode);
  const url = `https://router.project-osrm.org/route/v1/${profile}/${src.lng},${src.lat};${dst.lng},${dst.lat}?overview=full&geometries=geojson&alternatives=true&steps=false`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`OSRM route service returned ${res.status}.`);
  const data = await res.json();
  if (!data.routes || data.routes.length === 0) throw new Error("No road route found.");

  return data.routes
    .slice(0, 3)
    .sort((a, b) => a.distance - b.distance)
    .map((route) => route.geometry.coordinates.map((coord) => [coord[1], coord[0]]));
}

function pathDistanceKm(coords) {
  if (!coords || coords.length < 2) return 0;
  let total = 0;
  for (let i = 1; i < coords.length; i++) {
    total += haversineKm(coords[i - 1][0], coords[i - 1][1], coords[i][0], coords[i][1]);
  }
  return total;
}

function buildFallbackRouteData(segments) {
  const makeRoute = (type, coords) => ({
    type,
    found: true,
    distance: pathDistanceKm(coords),
    safetyScore: 50,
    safetyStatus: "MODERATE",
    coordinates: coords,
    reasons: ["OSRM road route (safety server unavailable)"],
  });
  return {
    status: "success",
    routes: {
      shortest: makeRoute("SHORTEST", segments[0]),
      safest: makeRoute("SAFEST", segments[1] || segments[0]),
      balanced: makeRoute("BALANCED", segments[2] || segments[0]),
    },
    graphNodes: 0,
    graphEdges: 0,
  };
}

async function analyzeRoutes(segments, mode) {
  const apiRes = await fetch(`${API}/api/analyze-route`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      coordinates: JSON.stringify(segments[0]),
      segments: JSON.stringify(segments),
      mode,
    }),
  });
  const rawText = await apiRes.text();
  if (!rawText.trim()) {
    throw new Error(`Backend returned an empty response. Start the Java server at ${API}`);
  }

  let apiData;
  try {
    if (!rawText.trimStart().startsWith("{")) {
      throw new Error("not json");
    }
    apiData = JSON.parse(rawText);
  } catch (err) {
    throw new Error(`Backend returned invalid JSON. Restart the server: Tasks → safepath: run server`);
  }
  if (!apiRes.ok || apiData.status !== "success") {
    throw new Error(apiData.message || "Safety analysis failed.");
  }
  return apiData;
}

async function findRoute() {
  const srcVal = document.getElementById("source").value.trim();
  const dstVal = document.getElementById("destination").value.trim();
  const mode = document.getElementById("transportMode").value;
  if (!srcVal || !dstVal) {
    toast("Enter both source and destination.", "warn");
    return;
  }

  showSpinner(true);
  clearRoutes();

  try {
    const [src, dst] = await Promise.all([geocode(srcVal), geocode(dstVal)]);
    tripSource = src.label || srcVal;
    tripSourceCoords = { lat: src.lat, lng: src.lng, label: tripSource, input: srcVal };
    tripDestination = { lat: dst.lat, lng: dst.lng, label: dst.label || dstVal };
    const segments = await getRoadRoutes(src, dst, mode);

    let apiData;
    try {
      apiData = await analyzeRoutes(segments, mode);
    } catch (apiErr) {
      console.warn("Safety analysis unavailable, showing road routes only:", apiErr);
      toast(`${apiErr.message}. Showing OSRM road routes.`, "warn");
      apiData = buildFallbackRouteData(segments);
    }

    currentRoutes = apiData.routes || {};
    renderRouteComparePanel(apiData);
    if (typeof setupPlanRouteScroll === "function") {
      document.documentElement.style.setProperty(
        "--plan-toolbar-h",
        `${document.querySelector("#page-plan-route .route-toolbar")?.offsetHeight || 150}px`,
      );
    }
    if (map) setTimeout(() => map.invalidateSize(), 250);
    animateRouteLine("shortest", currentRoutes.shortest?.coordinates || segments[0], currentRoutes.shortest);
    animateRouteLine("safest", currentRoutes.safest?.coordinates || segments[1] || segments[0], currentRoutes.safest);
    animateRouteLine("balanced", currentRoutes.balanced?.coordinates || segments[2] || segments[0], currentRoutes.balanced);
    chooseBestRoute();
    rememberTripMeta();
    document.getElementById("routeLegend").style.display = "block";
    setBoundsFor(segments.flat());
    await ensureSession();
    syncRouteWithServer(currentPath);

    addMarker(src.lat, src.lng, "Start");
    addMarker(dst.lat, dst.lng, "Destination");
    await loadPOIs(src, dst);
  } catch (err) {
    console.error(err);
    toast(`Failed to fetch routes: ${err.message}`, "danger");
  } finally {
    showSpinner(false);
  }
}

function drawRoute(type, routeData, fallbackCoords) {
  const coords = routeData && routeData.found && routeData.coordinates
    ? routeData.coordinates.map((c) => [c[0], c[1]])
    : fallbackCoords;
  if (!coords || coords.length < 2) return;

  const styles = {
    shortest: { weight: 5, opacity: 0.72, dashArray: "10,7" },
    safest:   { weight: 6, opacity: 0.78, dashArray: null },
    balanced: { weight: 7, opacity: 0.85, dashArray: "4,10" },
  };
  const style = styles[type] || styles.balanced;

  const line = L.polyline(coords, {
    color: ROUTE_COLORS[type],
    weight: style.weight,
    opacity: style.opacity,
    dashArray: style.dashArray,
  }).addTo(map);

  const status = routeData?.safetyStatus || "UNKNOWN";
  const score = routeData?.safetyScore == null ? "N/A" : routeData.safetyScore.toFixed(1);
  const distance = routeData?.distance == null ? "N/A" : `${routeData.distance.toFixed(2)} km`;
  const algo = routeData?.algorithm ? `<br>Algorithm: ${routeData.algorithm}` : "";
  line.bindPopup(`<b>${labelForRoute(type)} route</b><br>Distance: ${distance}<br>Safety: ${score}/100<br>Status: ${status}${algo}`);
  line.on("click", () => highlightRoute(type));
  routeLines[type] = line;
}

function chooseBestRoute() {
  const recommended = lastRouteAnalysis?.recommended;
  if (recommended && currentRoutes[recommended]?.found) {
    activeRouteType = recommended;
  } else {
    const candidates = Object.entries(currentRoutes)
      .filter(([, r]) => r && r.found)
      .sort((a, b) => (b[1].safetyScore || 0) - (a[1].safetyScore || 0));
    activeRouteType = candidates[0]?.[0] || "balanced";
  }
  highlightRoute(activeRouteType);
}

function highlightRoute(type) {
  activeRouteType = type;
  Object.entries(routeLines).forEach(([key, line]) => {
    line.setStyle({ weight: key === type ? 8 : 4, opacity: key === type ? 1 : 0.36 });
    if (key === type) line.bringToFront();
  });

  const route = currentRoutes[type];
  if (route) {
    lastSafetyScore = route.safetyScore || lastSafetyScore;
    updateSafetyStatus(lastSafetyScore);
    if (route.confidence != null && typeof updateXaiPanel === "function") {
      updateXaiPanel({
        confidence: route.confidence,
        predictedScore30Min: route.predictedScore30Min,
        trend: route.riskTrend,
        xaiFactors: route.reasons || [],
      });
    }
    currentPath = route.coordinates || routeLines[type]?.getLatLngs().map((p) => [p.lat, p.lng]) || [];
    syncRouteWithServer(currentPath);
    rememberTripMeta();
  }
}

async function syncRouteWithServer(path) {
  if (!sessionId || !path || path.length === 0) return;
  const route = currentRoutes[activeRouteType] || {};
  try {
    await fetch(`${API}/api/set-route`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId,
        coordinates: JSON.stringify(path),
        routeType: activeRouteType,
        distanceKm: route.distance != null ? String(route.distance) : "0",
      }),
    });
  } catch (err) {
    console.warn("Failed to sync route with server:", err);
  }
}

function formatKm(distance) {
  return `${(distance || 0).toFixed(2)} km`;
}

function labelForRoute(type) {
  return type.charAt(0).toUpperCase() + type.slice(1);
}

const TRIP_META_KEY = "safepath_trip_meta";

function rememberTripMeta() {
  const route = currentRoutes[activeRouteType] || {};
  const src = tripSource
    || tripSourceCoords?.label
    || document.getElementById("source")?.value?.trim()
    || "";
  const dst = tripDestination?.label
    || document.getElementById("destination")?.value?.trim()
    || "";
  const coords = route.coordinates || currentPath || [];
  const distance = route.distance != null
    ? route.distance
    : (coords.length >= 2 ? pathDistanceKm(coords) : 0);
  if (!src && !dst) return;
  sessionStorage.setItem(TRIP_META_KEY, JSON.stringify({
    source: src,
    destination: dst,
    distanceKm: distance,
    routeType: activeRouteType || "balanced",
  }));
}

function getTripMeta() {
  try {
    return JSON.parse(sessionStorage.getItem(TRIP_META_KEY) || "{}");
  } catch {
    return {};
  }
}

function setBoundsFor(coords) {
  if (coords.length) map.fitBounds(L.latLngBounds(coords), { padding: [36, 36] });
}

function addMarker(lat, lng, label) {
  const isStart = label === "Start";
  const marker = L.marker([lat, lng], {
    icon: L.divIcon({
      className: "route-pin-wrap",
      html: `<span class="route-pin ${isStart ? "route-pin--start" : "route-pin--dest"}">${isStart ? "A" : "B"}</span>`,
      iconSize: [30, 30],
      iconAnchor: [15, 15],
    }),
  }).bindPopup(label).addTo(map);
  permanentMarkers.push(marker);
}

function clearRoutes() {
  Object.values(routeLines).forEach((line) => map.removeLayer(line));
  permanentMarkers.forEach((marker) => map.removeLayer(marker));
  routeLines = {};
  permanentMarkers = [];
  currentRoutes = {};
  currentPath = [];
  poiPathLayer.clearLayers();
}

async function loadPOIs(src, dst) {
  const pad = 0.05;
  const south = Math.min(src.lat, dst.lat) - pad;
  const north = Math.max(src.lat, dst.lat) + pad;
  const west = Math.min(src.lng, dst.lng) - pad;
  const east = Math.max(src.lng, dst.lng) + pad;

  poiData = { police: [], hospital: [], hotel: [], hostel: [], metro: [], bus: [] };

  try {
    const res = await fetch(`${API}/api/nearby-pois`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ south, west, north, east }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || data.status !== "success") {
      throw new Error(data.message || "POI API failed");
    }
    (data.pois || []).forEach((poi) => {
      const type = poi.type || "hotel";
      const item = {
        lat: poi.lat,
        lng: poi.lng,
        name: poi.name || "Unnamed safe spot",
      };
      if (poiData[type]) poiData[type].push(item);
    });
    const total = Object.values(poiData).reduce((n, arr) => n + arr.length, 0);
    if (total > 0) {
      toast(`Loaded ${total} safe checkpoints — toggle buttons in sidebar to show on map.`, "info");
    } else {
      toast("No checkpoints found in this area.", "warn");
    }
  } catch (err) {
    console.warn("POI load failed", err);
    toast("Safe checkpoint data could not be loaded. Try again after Find Route.", "warn");
  }
}

const markerIcons = { police: "P", hospital: "H", hotel: "T", hostel: "S", metro: "M", bus: "B" };

function toggleMarkers(type) {
  const button = document.getElementById(`toggle${capitalize(type)}`);
  if (!button) return;

  if (markerLayers[type]) {
    map.removeLayer(markerLayers[type]);
    delete markerLayers[type];
    button.style.opacity = "0.55";
    return;
  }

  const items = poiData[type] || [];
  if (!items.length) {
    toast(`No ${type} data loaded yet. Click Find Route first.`, "warn");
    return;
  }

  const layer = L.layerGroup();
  items.forEach((poi) => {
    const ref = getReferencePosition();
    let popup = `<b>${poi.name}</b><br>${capitalize(type)}`;
    if (ref) {
      const dist = haversineKm(ref.lat, ref.lng, poi.lat, poi.lng).toFixed(2);
      popup += `<br><b>${dist} km away</b>`;
    }
    L.marker([poi.lat, poi.lng], {
      icon: L.divIcon({
        html: `<span class="map-badge ${type}">${markerIcons[type]}</span>`,
        className: "poi-marker-wrap",
        iconSize: [26, 26],
        iconAnchor: [13, 13],
      }),
      zIndexOffset: 400,
    }).bindPopup(popup).on("click", () => showPathToPoi(poi.lat, poi.lng)).addTo(layer);
  });
  layer.addTo(map);
  markerLayers[type] = layer;
  button.style.opacity = "1";
  toast(`Showing ${items.length} ${type} marker${items.length === 1 ? "" : "s"} on map.`, "success");
}

function capitalize(s) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

let locationIsFallback = false;

const GEO_OPTIONS_HIGH = { enableHighAccuracy: true, maximumAge: 0, timeout: 45000 };
const GEO_OPTIONS_MID = { enableHighAccuracy: true, maximumAge: 5000, timeout: 35000 };
const GEO_OPTIONS_LOW = { enableHighAccuracy: false, maximumAge: 15000, timeout: 25000 };
const GEO_WATCH = { enableHighAccuracy: true, maximumAge: 0, timeout: 45000 };

function requestGeolocation(options) {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(Object.assign(new Error("unsupported"), { code: 0 }));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, options);
  });
}

async function resolveSourceCoords() {
  const srcVal = document.getElementById("source").value.trim();
  if (!srcVal) return null;
  if (tripSourceCoords && tripSourceCoords.input === srcVal) return tripSourceCoords;
  try {
    const g = await geocode(srcVal);
    tripSourceCoords = { lat: g.lat, lng: g.lng, label: g.label, input: srcVal };
    return tripSourceCoords;
  } catch (_) {
    return null;
  }
}

function positionFromCoords(lat, lng, accuracy = 150, source = "address") {
  return {
    coords: { latitude: lat, longitude: lng, accuracy },
    _fallback: true,
    _source: source,
  };
}

async function resolveStartPosition() {
  if (navigator.permissions) {
    try {
      const perm = await navigator.permissions.query({ name: "geolocation" });
      if (perm.state === "denied") {
        throw Object.assign(new Error("denied"), { code: 1 });
      }
    } catch (_) {
      /* permissions API not available */
    }
  }

  for (const opts of [GEO_OPTIONS_HIGH, GEO_OPTIONS_MID, GEO_OPTIONS_LOW]) {
    try {
      const pos = await requestGeolocation(opts);
      locationIsFallback = false;
      return pos;
    } catch (err) {
      if (err.code === 1) throw err;
    }
  }

  const src = await resolveSourceCoords();
  if (src) {
    locationIsFallback = true;
    toast("GPS unavailable — using source address until live GPS connects.", "info");
    return positionFromCoords(src.lat, src.lng, 500, "address");
  }

  if (currentPath.length >= 1) {
    locationIsFallback = true;
    toast("GPS unavailable — using route start until live GPS connects.", "info");
    const [lat, lng] = currentPath[0];
    return positionFromCoords(lat, lng, 500, "route");
  }

  throw Object.assign(new Error("no position"), { code: 2 });
}

async function ensureSession() {
  if (sessionId && viewKey) return sessionId;
  const res = await fetch(`${API}/api/start-tracking`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token: authToken }),
  });
  const { data, ok } = await parseApiJson(res);
  if (!ok || data.status !== "success") {
    throw new Error(data.message || "Could not start tracking session. Restart the server.");
  }
  sessionId = data.sessionId;
  viewKey = data.viewKey || "";
  if (viewKey) sessionStorage.setItem("safepath_view_key", viewKey);
  return sessionId;
}

function updateLocationButton(on) {
  const btn = document.getElementById("locationBtn");
  const status = document.getElementById("locationStatus");
  if (btn) {
    btn.textContent = on ? "Location On" : "Turn On Location";
    btn.classList.toggle("location-active", on);
  }
  if (status) {
    status.textContent = on
      ? "GPS active — your position is shown on the map"
      : "GPS off — click to enable location on the map";
  }
}

function stopLocationWatch() {
  if (locationWatchId !== null) navigator.geolocation.clearWatch(locationWatchId);
  locationWatchId = null;
}

async function toggleLocation() {
  if (locationWatchId !== null) {
    stopLocationWatch();
    updateLocationButton(false);
    toast("Location updates stopped.", "info");
    return;
  }

  if (watchId !== null) {
    toast("Location is already active while tracking.", "info");
    updateLocationButton(true);
    return;
  }

  if (!navigator.geolocation) {
    toast("Geolocation is not supported in this browser.", "warn");
    return;
  }

  toast("Allow location access when your browser prompts.", "info");
  try {
    const pos = await requestGeolocation(GEO_OPTIONS_HIGH);
    await handlePosition(pos);
    locationWatchId = navigator.geolocation.watchPosition(
      (p) => handlePosition(p),
      handleGpsError,
      GEO_WATCH,
    );
    updateLocationButton(true);
    toast("Location enabled.", "success");
  } catch (err) {
    handleGpsError(err);
    updateLocationButton(false);
  }
}

async function toggleTracking() {
  const btn = document.getElementById("trackBtn");
  if (watchId !== null || trackingTimer) {
    stopTracking();
    return;
  }

  await ensureSession();
  btn.textContent = "Stop Tracking";
  btn.classList.add("danger");
  toast("Allow location access when your browser prompts.", "info");
  startTripEmails();

  if (locationWatchId !== null) {
    stopLocationWatch();
  }

  try {
    const pos = await resolveStartPosition();
    await handlePosition(pos);
    watchId = navigator.geolocation.watchPosition(
      (p) => handlePosition(p),
      () => { /* transient GPS errors are ignored while tracking */ },
      GEO_WATCH,
    );
    trackingTimer = setInterval(async () => {
      try {
        const p = await requestGeolocation(GEO_OPTIONS_HIGH);
        await handlePosition(p);
      } catch (_) {
        /* keep trying for a GPS fix */
      }
    }, TRACKING_POLL_MS);
    updateLocationButton(true);
  } catch (err) {
    handleGpsError(err);
    stopTracking();
  }
}

function stopTracking() {
  if (typeof stopDemoMode === "function") stopDemoMode();
  if (watchId !== null) navigator.geolocation.clearWatch(watchId);
  if (trackingTimer) clearInterval(trackingTimer);
  watchId = null;
  trackingTimer = null;
  const btn = document.getElementById("trackBtn");
  btn.textContent = "Start Tracking";
  btn.classList.remove("danger");
  if (locationWatchId === null) updateLocationButton(false);
}

function requestSinglePosition() {
  navigator.geolocation.getCurrentPosition(handlePosition, handleGpsError, {
    enableHighAccuracy: true,
    maximumAge: 1000,
    timeout: 10000,
  });
}

let lastJourneyForecastAt = 0;

async function handlePosition(pos) {
  const lat = pos.coords.latitude;
  const lng = pos.coords.longitude;
  const now = Date.now();
  const isLiveGps = !pos._fallback;

  if (isLiveGps && locationIsFallback) {
    locationIsFallback = false;
    toast("Live GPS location updated.", "success");
  }
  if (pos._fallback) locationIsFallback = true;

  if (lastPosition) {
    const movedKm = haversineKm(lat, lng, lastPosition.lat, lastPosition.lng);
    if (movedKm > 0.025) lastMovementAt = now;
  } else {
    lastMovementAt = now;
  }
  lastPosition = { lat, lng, at: now };

  const accuracyM = pos.coords.accuracy ? Math.round(pos.coords.accuracy) : null;
  const popupText = accuracyM
    ? `Current location<br>Accuracy: ~${accuracyM} m`
    : "Current location";

  if (!userMarker) {
    userMarker = L.marker([lat, lng]).bindPopup(popupText).addTo(map);
    map.setView([lat, lng], Math.max(map.getZoom(), 15));
  } else {
    userMarker.setLatLng([lat, lng]);
    userMarker.setPopupContent(popupText);
  }

  try {
    await ensureSession();
    const res = await fetch(`${API}/api/update-location`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId, latitude: String(lat), longitude: String(lng) }),
    });
    const { data, ok } = await parseApiJson(res);
    if (ok && data.safetyScore !== undefined) {
      lastSafetyScore = data.safetyScore;
      updateSafetyStatus(lastSafetyScore);
      if (data.xai && typeof updateXaiPanel === "function") updateXaiPanel(data.xai);
      runJourneyAiMonitor(data, lat, lng, now);
      monitorRouteSafety();
      if (data.alertLevel) handleSafetyAlert(data.alertLevel, lastSafetyScore);
    }
  } catch (err) {
    toast("Live safety update failed.", "warn");
  }

  if (currentPath.length) {
    const deviation = checkDeviationFromRoute(lat, lng, currentPath);
    if (deviation > DEVIATION_THRESHOLD_KM) handleDeviation(deviation);
  }
  checkStoppedEmergency();
}

function handleGpsError(err) {
  const code = err && err.code;
  const hints = {
    1: "Location permission denied. Click the lock icon in your address bar and allow location for this site.",
    2: "Position unavailable. Enable device location / Wi-Fi and try again.",
    3: "Location request timed out. Move near a window or retry.",
  };
  const msg = hints[code] || `GPS unavailable: ${err?.message || "unknown error"}`;
  toast(msg, "warn");
  if (code === 1) {
    toast(`Tip: use ${getApiBase()} for reliable GPS in development.`, "info");
  }
}

function showPathToPoi(lat, lng) {
  poiPathLayer.clearLayers();
  const ref = getReferencePosition();
  if (!ref) return;
  L.polyline([[ref.lat, ref.lng], [lat, lng]], {
    color: "#0f766e",
    weight: 4,
    dashArray: "8,6",
    opacity: 0.85,
  }).addTo(poiPathLayer);
}

function handleSafetyAlert(level, score) {
  if (level === lastAlertLevel) return;
  lastAlertLevel = level;
  if (level === "HIGH_RISK") {
    toast(`High risk zone! Safety score ${score.toFixed(0)}/100. Guardian alert queued.`, "danger");
    postSessionAlert("HIGH_RISK", `Safety score dropped to ${score.toFixed(0)}/100`);
    emergencyReroute(true);
  } else if (level === "MODERATE") {
    toast(`Moderate safety (${score.toFixed(0)}/100). Stay alert.`, "warn");
  } else if (level === "SAFE") {
    toast(`Safety improved to ${score.toFixed(0)}/100.`, "success");
  }
}

function runJourneyAiMonitor(data, lat, lng, now) {
  if (data?.journeyAlert?.message) {
    toast(`AI Journey Monitor: ${data.journeyAlert.message}`, data.journeyAlert.level || "warn");
  } else if (data?.xai?.predictedScore30Min != null && data?.safetyScore != null) {
    const drop = data.safetyScore - data.xai.predictedScore30Min;
    if (drop >= 8 && now - lastJourneyForecastAt > 90_000) {
      lastJourneyForecastAt = now;
      toast(
        `AI predicts safety may drop to ${Number(data.xai.predictedScore30Min).toFixed(0)}/100 in 30 min. Consider Safest route.`,
        "warn",
      );
    }
  }

  if (lastPosition && lastPosition.at && lastPosition.lat != null) {
    const dtSec = (now - lastPosition.at) / 1000;
    if (dtSec > 2 && dtSec < 90) {
      const speedKmh = haversineKm(lat, lng, lastPosition.lat, lastPosition.lng) / (dtSec / 3600);
      if (speedKmh > 100) {
        toast("AI Journey Monitor: unusually rapid movement detected.", "warn");
      }
    }
  }
}

function monitorRouteSafety() {
  if (lastSafetyScore < 45) {
    const safer = Object.entries(currentRoutes)
      .filter(([, r]) => r && r.found && r.safetyScore > lastSafetyScore + 8)
      .sort((a, b) => b[1].safetyScore - a[1].safetyScore)[0];
    if (safer && safer[0] !== activeRouteType) {
      highlightRoute(safer[0]);
      const msg = `Route became risky (${lastSafetyScore.toFixed(0)}/100). Suggested reroute: ${labelForRoute(safer[0])}.`;
      postSessionAlert("REROUTE", msg);
      toast(msg, "danger");
    }
  }
}

function handleDeviation(distanceKm) {
  const now = Date.now();
  if (now - lastDeviationAlertAt < 20_000) return;
  lastDeviationAlertAt = now;
  const msg = `Deviation alert: ${distanceKm.toFixed(2)} km off planned route.`;
  postSessionAlert("DEVIATION", msg);
  toast(msg, "danger");
}

function checkStoppedEmergency() {
  if (Date.now() - lastMovementAt <= STOPPED_THRESHOLD_MS) {
    stoppedPromptShown = false;
    return;
  }
  if (stoppedPromptShown) return;
  stoppedPromptShown = true;
  showStoppedMovementConfirm();
}

function showStoppedMovementConfirm() {
  const overlay = document.createElement("div");
  overlay.className = "sos-overlay stopped-confirm-overlay";
  overlay.innerHTML = `
    <div class="sos-panel">
      <div class="sos-icon">?</div>
      <h2>No movement for 15 minutes</h2>
      <p>Are you safe? We can alert your guardians if you need help.</p>
      <button type="button" id="confirmStoppedSafeBtn" class="btn-primary">I'm safe</button>
      <button type="button" id="confirmStoppedSosBtn" class="sos-helpline-btn">Send emergency alert</button>
    </div>`;
  document.body.appendChild(overlay);

  document.getElementById("confirmStoppedSafeBtn").onclick = () => {
    lastMovementAt = Date.now();
    stoppedPromptShown = false;
    overlay.remove();
    toast("Movement timer reset. Stay safe!", "success");
  };
  document.getElementById("confirmStoppedSosBtn").onclick = () => {
    overlay.remove();
    stoppedPromptShown = false;
    triggerSOS("No movement for 15 minutes — user confirmed emergency");
  };
}

function openGuardianModal() {
  document.getElementById("guardianModal").style.display = "flex";
}

function closeGuardianModal() {
  document.getElementById("guardianModal").style.display = "none";
}

async function addGuardian() {
  const name = document.getElementById("guardianName").value.trim();
  const phone = document.getElementById("guardianPhone").value.trim();
  const email = document.getElementById("guardianEmail").value.trim();
  if (!name || (!phone && !email)) {
    toast("Enter guardian name and phone or email.", "warn");
    return;
  }
  const res = await fetch(`${API}/api/guardians`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token: authToken, name, phone, email }),
  });
  const data = await res.json();
  if (!res.ok) {
    toast(data.message || "Failed to add guardian.", "danger");
    return;
  }
  document.getElementById("guardianName").value = "";
  document.getElementById("guardianPhone").value = "";
  document.getElementById("guardianEmail").value = "";
  await refreshGuardianDashboardLink();
  await loadGuardians();
  closeGuardianModal();
  toast(`Guardian ${name} saved.`, "success");
}

async function removeGuardian(id) {
  await fetch(`${API}/api/guardians`, {
    method: "DELETE",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token: authToken, id }),
  });
  await loadGuardians();
}

async function loadGuardians() {
  if (!authToken) return;
  try {
    const res = await fetch(`${API}/api/guardians?token=${encodeURIComponent(authToken)}`);
    const data = await res.json();
    guardians = data.guardians || [];
    renderGuardianList();
    await refreshGuardianDashboardLink();
  } catch (err) {
    console.warn("Failed to load guardians", err);
  }
}

function renderGuardianList() {
  const list = document.getElementById("guardianList");
  if (!list) return;
  list.innerHTML = "";
  guardians.forEach((g) => {
    const li = document.createElement("li");
    const initials = (g.name || "?").trim().substring(0, 2).toUpperCase();
    const phone = g.phone ? `<span class="guardian-contact">${g.phone}</span>` : "";
    const email = g.email ? `<span class="guardian-contact">${g.email}</span>` : "";
    li.innerHTML = `
      <div class="guardian-card-row">
        <div class="guardian-avatar" aria-hidden="true">${initials}</div>
        <div class="guardian-info">
          <strong class="guardian-name">${g.name}</strong>
          ${phone}${email}
        </div>
        <button type="button" class="guardian-remove" title="Remove guardian"><i class="fa-solid fa-trash"></i> Remove</button>
      </div>`;
    li.querySelector(".guardian-remove").onclick = () => removeGuardian(g.id);
    list.appendChild(li);
  });
}

async function refreshGuardianDashboardLink() {
  await ensureSession();
  const dash = document.getElementById("guardianDashboardLink");
  if (dash) {
    dash.href = getGuardianShareUrl();
    dash.style.display = guardians.length ? "inline-flex" : "none";
  }
}

async function startTripEmails() {
  await ensureSession();
  if (currentPath.length) syncRouteWithServer(currentPath);
  const res = await fetch(`${API}/api/start-trip`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId,
      token: authToken,
      userName: currentUser?.name || "User",
      source: tripSource || document.getElementById("source").value.trim() || "Unknown",
      destination: tripDestination?.label || document.getElementById("destination").value.trim() || "Unknown",
      destLat: String(tripDestination?.lat ?? 0),
      destLng: String(tripDestination?.lng ?? 0),
      trackingLink: getGuardianShareUrl(),
    }),
  });
  let data;
  try {
    ({ data } = await parseApiJson(res));
  } catch (err) {
    toast(err.message, "danger");
    return;
  }
  if (res.ok) {
    toastEmailResult(data, "Guardian notified by email.");
  } else if (guardians.length === 0) {
    toast("Add guardians with email to receive trip alerts.", "warn");
  } else {
    toast(data.message || "Trip alert could not be sent.", "warn");
  }
  rememberTripMeta();
}

function copyLiveLink() {
  ensureSession().then(async () => {
    await copyText(getGuardianShareUrl());
    toast("Live tracking link copied.", "success");
  });
}

function logoutUser() {
  localStorage.removeItem("safepath_token");
  localStorage.removeItem("safepath_user");
  location.href = `${getApiBase()}/login.html`;
}

function refreshAuthUI() {
  const greeting = document.getElementById("userGreeting");
  if (currentUser && greeting) {
    greeting.textContent = `Hi, ${currentUser.name}`;
  }
  const profileName = document.getElementById("profileName");
  const profileEmail = document.getElementById("profileEmail");
  if (currentUser) {
    if (profileName) profileName.textContent = currentUser.name || "—";
    if (profileEmail) profileEmail.textContent = currentUser.email || "—";
  }
  if (location.hash === "#page-profile") loadProfileData();
}

function formatProfileDate(ts) {
  if (!ts) return "—";
  return new Date(ts).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

async function loadProfileData() {
  if (!authToken) return;
  const tripList = document.getElementById("tripHistoryList");
  const reportList = document.getElementById("myReportsList");
  try {
    const [tripRes, reportRes] = await Promise.all([
      fetch(`${API}/api/trip-history?token=${encodeURIComponent(authToken)}`),
      fetch(`${API}/api/my-reports?token=${encodeURIComponent(authToken)}`),
    ]);
    const tripData = await tripRes.json().catch(() => ({}));
    const reportData = await reportRes.json().catch(() => ({}));

    if (!tripRes.ok) {
      console.warn("trip-history error", tripRes.status, tripData);
    }
    if (!reportRes.ok) {
      console.warn("my-reports error", reportRes.status, reportData);
    }

    if (tripList) {
      const trips = tripData.trips || [];
      if (!trips.length) {
        tripList.innerHTML = '<p class="info-text empty-state">Complete a trip with <strong>Confirm Safe Arrival</strong> to see history here.</p>';
      } else {
        tripList.innerHTML = trips.map((t) => `
          <article class="profile-list-item">
            <div class="pli-main">
              <strong>${t.source || "—"} → ${t.destination || "—"}</strong>
              <span class="pli-meta">${formatProfileDate(t.completedAt)} · ${labelForRoute(t.routeType || "balanced")} · ${(t.distanceKm || 0).toFixed(2)} km</span>
            </div>
            <div class="pli-side">
              <span class="pli-score">${Math.round(t.safetyScore || 0)}/100</span>
              ${t.rating ? `<span class="pli-rating">${"★".repeat(t.rating)}</span>` : ""}
            </div>
          </article>`).join("");
      }
    }

    if (reportList) {
      const reports = reportData.reports || [];
      if (!reports.length) {
        reportList.innerHTML = '<p class="info-text empty-state">Reports you submit appear on the community heatmap.</p>';
      } else {
        reportList.innerHTML = reports.map((r) => `
          <article class="profile-list-item profile-list-item--report">
            <div class="pli-main">
              <strong>${r.category || "Unsafe location"}</strong>
              <span class="pli-meta">${formatProfileDate(r.createdAt)} · ${r.severity || "—"} severity</span>
              <span class="pli-desc">${r.description || r.reason || ""}</span>
            </div>
            <div class="pli-side">
              <span class="pli-coords">${Number(r.latitude).toFixed(4)}, ${Number(r.longitude).toFixed(4)}</span>
            </div>
          </article>`).join("");
      }
    }
  } catch (err) {
    console.warn("Failed to load profile data", err);
  }
}

function setupMapRouteResizer() {
  const resizer = document.getElementById("mapRouteResizer");
  const mapStage = document.querySelector("#page-plan-route .map-stage");
  const page = document.getElementById("page-plan-route");
  if (!resizer || !mapStage || !page || resizer.dataset.bound) return;
  resizer.dataset.bound = "1";

  let dragging = false;
  const onMove = (clientY) => {
    const rect = page.getBoundingClientRect();
    const toolbar = page.querySelector(".route-toolbar");
    const toolbarH = toolbar ? toolbar.offsetHeight : 0;
    const minH = 160;
    const maxH = rect.height - toolbarH - 180;
    const newH = Math.max(minH, Math.min(clientY - rect.top - toolbarH, maxH));
    mapStage.style.flex = "0 0 auto";
    mapStage.style.height = `${newH}px`;
    mapStage.style.maxHeight = `${newH}px`;
    if (map) map.invalidateSize();
  };

  resizer.addEventListener("mousedown", (e) => {
    dragging = true;
    e.preventDefault();
  });
  resizer.addEventListener("touchstart", (e) => {
    dragging = true;
    e.preventDefault();
  }, { passive: false });

  window.addEventListener("mousemove", (e) => {
    if (!dragging) return;
    onMove(e.clientY);
  });
  window.addEventListener("touchmove", (e) => {
    if (!dragging || !e.touches[0]) return;
    onMove(e.touches[0].clientY);
  }, { passive: false });

  const stopDrag = () => { dragging = false; };
  window.addEventListener("mouseup", stopDrag);
  window.addEventListener("touchend", stopDrag);
}

function setupPlanRouteScroll() {
  const page = document.getElementById("page-plan-route");
  const toolbar = page?.querySelector(".route-toolbar");
  if (!page || page.dataset.scrollBound) return;
  page.dataset.scrollBound = "1";

  const syncToolbarHeight = () => {
    if (toolbar) {
      document.documentElement.style.setProperty(
        "--plan-toolbar-h",
        `${toolbar.offsetHeight}px`,
      );
    }
    if (map) map.invalidateSize();
  };
  syncToolbarHeight();
  window.addEventListener("resize", syncToolbarHeight);
  page.addEventListener("scroll", () => {
    if (map) map.invalidateSize();
  }, { passive: true });
}

function getReferencePosition() {
  if (lastPosition) return lastPosition;
  if (userMarker) {
    const p = userMarker.getLatLng();
    return { lat: p.lat, lng: p.lng };
  }
  return null;
}

function callHelpline(number) {
  window.location.href = `tel:${number}`;
}

function startSosSiren() {
  const AudioCtx = window.AudioContext || window.webkitAudioContext;
  if (!AudioCtx) return () => {};

  const ctx = new AudioCtx();
  const gain = ctx.createGain();
  gain.gain.value = 0.42;
  gain.connect(ctx.destination);

  const oscA = ctx.createOscillator();
  const oscB = ctx.createOscillator();
  oscA.type = "sawtooth";
  oscB.type = "square";
  oscA.connect(gain);
  oscB.connect(gain);
  oscA.start();
  oscB.start();

  let high = true;
  const pulse = setInterval(() => {
    const t = ctx.currentTime;
    oscA.frequency.setValueAtTime(high ? 920 : 520, t);
    oscB.frequency.setValueAtTime(high ? 680 : 380, t);
    high = !high;
  }, 280);

  const lfo = ctx.createOscillator();
  const lfoGain = ctx.createGain();
  lfo.frequency.value = 4.5;
  lfoGain.gain.value = 0.12;
  lfo.connect(lfoGain);
  lfoGain.connect(gain.gain);
  lfo.start();

  return () => {
    clearInterval(pulse);
    try {
      oscA.stop();
      oscB.stop();
      lfo.stop();
      ctx.close();
    } catch (_) {
      /* already stopped */
    }
  };
}

async function emergencyReroute(silent = false) {
  if (!lastPosition || !tripDestination) {
    if (!silent) toast("Need active GPS and a destination for emergency reroute.", "warn");
    return;
  }
  showSpinner(true);
  try {
    const mode = document.getElementById("transportMode").value;
    const res = await fetch(`${API}/api/emergency-reroute`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        latitude: String(lastPosition.lat),
        longitude: String(lastPosition.lng),
        destLat: String(tripDestination.lat),
        destLng: String(tripDestination.lng),
        mode,
      }),
    });
    const data = await res.json();
    if (!res.ok || !data.route?.found) throw new Error(data.message || "Reroute failed");
    currentRoutes.safest = data.route;
    if (routeLines.safest) map.removeLayer(routeLines.safest);
    drawRoute("safest", data.route, data.route.coordinates);
    highlightRoute("safest");
    postSessionAlert("REROUTE", "Emergency safer route recalculated.");
    if (!silent) toast("Emergency safer route applied.", "success");
  } catch (err) {
    if (!silent) toast(err.message, "danger");
  } finally {
    showSpinner(false);
  }
}

async function postSessionAlert(type, message, extras = {}) {
  if (!sessionId) return null;
  try {
    const res = await fetch(`${API}/api/session-alert`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId,
        token: authToken,
        type,
        message,
        latitude: String(lastPosition?.lat ?? 0),
        longitude: String(lastPosition?.lng ?? 0),
        safetyScore: String(lastSafetyScore),
        ...extras,
      }),
    });
    if (!res.ok) return null;
    return await res.json();
  } catch (err) {
    console.warn("Failed to post session alert", err);
    return null;
  }
}

async function sendEmergencyToGuardian(reason) {
  const loc = lastPosition
    ? `${lastPosition.lat.toFixed(5)}, ${lastPosition.lng.toFixed(5)}`
    : "location pending";
  const trackUrl = getGuardianShareUrl();
  const statusText = document.getElementById("safetyStatus")?.textContent || "UNKNOWN";
  const fullMsg = `${reason}. Location: ${loc}. Safety: ${lastSafetyScore.toFixed(0)}/100 (${statusText}). Live map: ${trackUrl}`;

  const data = await postSessionAlert("SOS", fullMsg);
  if (data) {
    toastEmailResult(data, "Emergency alert sent to guardian.", "danger");
  } else {
    toast("Emergency alert failed. Restart the server and try again.", "warn");
  }
}

function shareLiveLinkWhatsApp() {
  ensureSession().then(() => {
    const phone = guardians.find((g) => g.phone)?.phone;
    const trackUrl = getGuardianShareUrl();
    const loc = lastPosition ? `${lastPosition.lat.toFixed(5)}, ${lastPosition.lng.toFixed(5)}` : "awaiting GPS";
    const statusText = document.getElementById("safetyStatus")?.textContent || "MODERATE";
    const text = encodeURIComponent(
      `SafePath live tracking\nLocation: ${loc}\nSafety Score: ${lastSafetyScore.toFixed(0)}/100 (${statusText})\nTrack me: ${trackUrl}`,
    );
    if (phone) {
      window.open(`https://api.whatsapp.com/send?phone=${phone.replace(/\D/g, "")}&text=${text}`, "_blank");
    } else {
      window.open(`https://api.whatsapp.com/send?text=${text}`, "_blank");
    }
  });
}

function triggerSOS(reason = "Emergency SOS") {
  ensureSession().then(() => {
    showSosOverlay(() => {
      sendEmergencyToGuardian(reason);
    });
  });
}

function showSosOverlay(onSend) {
  const overlay = document.createElement("div");
  overlay.className = "sos-overlay";
  const stopSiren = startSosSiren();
  let seconds = 3;
  overlay.innerHTML = `
    <div class="sos-panel">
      <div class="sos-icon">!</div>
      <h2>Emergency SOS Activated</h2>
      <p id="sosCountdown">Sending alert in ${seconds} seconds...</p>
      <p>Location: ${lastPosition ? `${lastPosition.lat.toFixed(5)}, ${lastPosition.lng.toFixed(5)}` : "pending"}</p>
      <div class="sos-helpline">
        <p class="sos-helpline-title"><i class="fa-solid fa-phone"></i> Emergency helplines</p>
        <button type="button" class="sos-helpline-btn" data-tel="1091">Women Helpline 1091</button>
        <button type="button" class="sos-helpline-btn" data-tel="100">Police 100</button>
      </div>
      <button id="cancelSosBtn">Cancel SOS</button>
    </div>
  `;
  document.body.appendChild(overlay);

  overlay.querySelectorAll(".sos-helpline-btn").forEach((btn) => {
    btn.addEventListener("click", () => callHelpline(btn.dataset.tel));
  });

  const closeOverlay = () => {
    stopSiren();
    overlay.remove();
  };

  const timer = setInterval(() => {
    seconds -= 1;
    const countdownEl = document.getElementById("sosCountdown");
    if (countdownEl) {
      countdownEl.textContent = `Sending alert in ${seconds} seconds...`;
    }
    if (seconds <= 0) {
      clearInterval(timer);
      closeOverlay();
      onSend();
    }
  }, 1000);

  document.getElementById("cancelSosBtn").onclick = () => {
    clearInterval(timer);
    closeOverlay();
    toast("SOS cancelled.", "success");
  };
}

let unsafeTargetLatLng = null;
let activeCategory = "Poor Lighting";
let activeSeverity = "Low";

function reportUnsafe() {
  const openAt = (lat, lng, hint) => {
    unsafeTargetLatLng = { lat, lng };
    openUnsafeModal();
    if (hint) toast(hint, "info");
  };

  if (lastPosition) {
    openAt(lastPosition.lat, lastPosition.lng);
    return;
  }

  navigator.geolocation.getCurrentPosition(
    (pos) => openAt(pos.coords.latitude, pos.coords.longitude),
    () => {
      const c = map.getCenter();
      openAt(c.lat, c.lng, "Using map center. Right-click the map to pick another spot.");
    },
    { enableHighAccuracy: false, timeout: 12000, maximumAge: 60000 },
  );
}

function openUnsafeModal() {
  document.getElementById("unsafeModal").style.display = "flex";
  document.getElementById("unsafeDescription").value = "";
  setActiveCategory("Poor Lighting");
  setActiveSeverity("Low");
  const label = document.getElementById("unsafeCoordsLabel");
  if (label && unsafeTargetLatLng) {
    label.textContent = `Reporting: ${unsafeTargetLatLng.lat.toFixed(5)}, ${unsafeTargetLatLng.lng.toFixed(5)}`;
  }
}

function closeUnsafeModal() {
  document.getElementById("unsafeModal").style.display = "none";
  unsafeTargetLatLng = null;
}

function setActiveCategory(cat) {
  activeCategory = cat;
  document.querySelectorAll(".cat-btn").forEach((btn) => {
    if (btn.dataset.category === cat) {
      btn.classList.add("active");
    } else {
      btn.classList.remove("active");
    }
  });
}

function setActiveSeverity(sev) {
  activeSeverity = sev;
  document.querySelectorAll(".sev-btn").forEach((btn) => {
    if (btn.dataset.severity === sev) {
      btn.classList.add("active");
    } else {
      btn.classList.remove("active");
    }
  });
}

async function postUnsafe(lat, lng, reason) {
  const desc = document.getElementById("unsafeDescription")?.value?.trim() || "";
  const res = await fetch(`${API}/api/report-unsafe`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      latitude: String(lat),
      longitude: String(lng),
      reason,
      token: authToken,
      category: activeCategory,
      severity: activeSeverity,
      description: desc,
    }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.status !== "success") {
    throw new Error(data.message || "Report failed");
  }
  const msg = data.message || "Unsafe location reported.";
  const level = data.affectsRouting === false ? "info" : "success";
  drawUnsafeZone(lat, lng, reason);
  if (typeof loadUnsafeHeatmap === "function") {
    loadUnsafeHeatmap().then(() => {
      if (typeof heatmapVisible !== "undefined" && heatmapVisible
          && typeof fitHeatmapBounds === "function" && typeof heatLocations !== "undefined"
          && heatLocations.length) {
        fitHeatmapBounds(heatLocations);
      }
    });
  }
  toast(msg, level);
  loadProfileData();
}

function drawUnsafeZone(lat, lng, reason) {
  L.circle([lat, lng], {
    radius: 350,
    color: "#ef4444",
    fillColor: "#f97316",
    fillOpacity: 0.22,
    weight: 1,
  }).bindPopup(`Unsafe zone<br><b>${reason}</b>`).addTo(unsafeLayer);
}

map.on("contextmenu", (e) => {
  unsafeTargetLatLng = { lat: e.latlng.lat, lng: e.latlng.lng };
  openUnsafeModal();
});

let selectedArrivalRating = 0;

function arrivalRatingDelta(stars) {
  return (stars - 3) * 5;
}

function clampSafetyScore(score) {
  return Math.max(0, Math.min(100, score));
}

function arrivalRatingHint(stars) {
  const delta = arrivalRatingDelta(stars);
  if (delta > 0) return `${stars} stars — safety score +${delta}`;
  if (delta < 0) return `${stars} stars — safety score ${delta}`;
  return `${stars} stars — no change to safety score`;
}

function updateArrivalStarDisplay(rating) {
  document.querySelectorAll("#arrivalStarRow .star-btn").forEach((btn) => {
    const n = Number(btn.dataset.rating);
    btn.classList.toggle("active", rating > 0 && n <= rating);
  });
  const hint = document.getElementById("arrivalRatingHint");
  if (hint) {
    hint.textContent = rating ? arrivalRatingHint(rating) : "Tap a star to rate";
  }
}

function openArrivalReviewModal() {
  selectedArrivalRating = 0;
  updateArrivalStarDisplay(0);
  const route = currentRoutes[activeRouteType];
  const distEl = document.getElementById("arrivalDistance");
  const scoreEl = document.getElementById("arrivalSafetyScore");
  const routeEl = document.getElementById("arrivalRouteName");
  if (distEl) {
    distEl.textContent = route?.distance != null ? `${route.distance.toFixed(1)} km` : "--";
  }
  if (scoreEl) {
    const score = route?.safetyScore != null ? route.safetyScore : lastSafetyScore;
    scoreEl.textContent = `${Number(score).toFixed(0)}/100`;
  }
  if (routeEl) {
    routeEl.textContent = `${labelForRoute(activeRouteType)} Route`;
  }
  document.getElementById("arrivalReviewModal").style.display = "flex";
}

function closeArrivalReviewModal() {
  document.getElementById("arrivalReviewModal").style.display = "none";
}

function reachSafeConfirmation() {
  openArrivalReviewModal();
}

async function submitArrivalReview() {
  if (!selectedArrivalRating) {
    toast("Please rate your trip with 1–5 stars.", "warn");
    return;
  }
  const meta = getTripMeta();
  const route = currentRoutes[activeRouteType] || {};
  const coords = route.coordinates || currentPath || [];
  const tripDistance = route.distance != null
    ? route.distance
    : (coords.length >= 2 ? pathDistanceKm(coords) : meta.distanceKm ?? 0);
  const tripSourceLabel = tripSource
    || meta.source
    || document.getElementById("source").value.trim()
    || "Unknown";
  const tripDestLabel = tripDestination?.label
    || meta.destination
    || document.getElementById("destination").value.trim()
    || "Unknown";

  closeArrivalReviewModal();
  stopTracking();
  const stars = selectedArrivalRating;
  try {
    await ensureSession();
  } catch (err) {
    toast(err.message || "Could not start session.", "warn");
    return;
  }
  const delta = arrivalRatingDelta(stars);
  const previewScore = clampSafetyScore(lastSafetyScore + delta);
  lastSafetyScore = previewScore;
  updateSafetyStatus(lastSafetyScore);

  const data = await postSessionAlert(
    "SAFE_ARRIVAL",
    "User has reached safely.",
    {
      rating: String(stars),
      source: tripSourceLabel,
      destination: tripDestLabel,
      distanceKm: String(tripDistance),
      routeType: activeRouteType || meta.routeType || "balanced",
    },
  );
  if (data) {
    if (data.safetyScore != null) {
      lastSafetyScore = Number(data.safetyScore);
      updateSafetyStatus(lastSafetyScore);
    }
    const appliedDelta = data.scoreDelta != null ? Number(data.scoreDelta) : delta;
    let scoreMsg = "";
    if (appliedDelta !== 0) {
      scoreMsg = appliedDelta > 0
        ? ` Safety score +${appliedDelta.toFixed(0)} (now ${lastSafetyScore.toFixed(0)}/100).`
        : ` Safety score ${appliedDelta.toFixed(0)} (now ${lastSafetyScore.toFixed(0)}/100).`;
    }
    if (data.emailsQueued) {
      toastEmailResult(data, `Reached safely — guardian notified.${scoreMsg}`, "success");
    } else {
      toast(`Safe arrival confirmed.${scoreMsg}`, "success");
    }
  } else {
    toast("Safe arrival saved but alert could not be sent.", "warn");
  }
  loadProfileData();
}

document.addEventListener("DOMContentLoaded", () => {
  const sidebar = document.getElementById("appSidebar");
  const backdrop = document.getElementById("sidebarBackdrop");
  const toggle = () => {
    sidebar?.classList.toggle("open");
    backdrop?.classList.toggle("open");
  };
  document.getElementById("sidebarToggle")?.addEventListener("click", toggle);
  document.getElementById("mobileNavMenu")?.addEventListener("click", toggle);
  backdrop?.addEventListener("click", toggle);
  document.getElementById("mobileTrackBtn")?.addEventListener("click", () => {
    document.getElementById("trackBtn")?.click();
  });

  window.addEventListener("hashchange", () => {
    if (location.hash === "#page-profile") loadProfileData();
  });
  document.querySelectorAll('a[href="#page-profile"]').forEach((link) => {
    link.addEventListener("click", () => setTimeout(loadProfileData, 100));
  });
  if (location.hash === "#page-profile") loadProfileData();
  setupMapRouteResizer();
  setupPlanRouteScroll();

  document.querySelectorAll("#arrivalStarRow .star-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      selectedArrivalRating = Number(btn.dataset.rating);
      updateArrivalStarDisplay(selectedArrivalRating);
    });
  });

  // Unsafe report modal DOM bindings
  const closeBtn = document.getElementById("closeUnsafeModal");
  if (closeBtn) closeBtn.onclick = closeUnsafeModal;

  document.querySelectorAll(".cat-btn").forEach((btn) => {
    btn.onclick = (e) => {
      e.preventDefault();
      setActiveCategory(btn.dataset.category);
    };
  });

  document.querySelectorAll(".sev-btn").forEach((btn) => {
    btn.onclick = (e) => {
      e.preventDefault();
      setActiveSeverity(btn.dataset.severity);
    };
  });

  const submitBtn = document.getElementById("submitUnsafeBtn");
  if (submitBtn) {
    submitBtn.onclick = async (e) => {
      e.preventDefault();
      if (!unsafeTargetLatLng) return;
      const desc = document.getElementById("unsafeDescription").value.trim();
      const reason = `[${activeSeverity}] ${activeCategory}${desc ? ": " + desc : ""}`;
      
      showSpinner(true);
      try {
        await postUnsafe(unsafeTargetLatLng.lat, unsafeTargetLatLng.lng, reason);
        closeUnsafeModal();
      } catch (err) {
        toast("Report submission failed.", "danger");
      } finally {
        showSpinner(false);
      }
    };
  }
});

function showSpinner(show) {
  document.getElementById("loadingSpinner").style.display = show ? "flex" : "none";
}

function toast(message, type = "info") {
  const el = document.createElement("div");
  el.className = `toast ${type}`;
  el.textContent = message;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 5200);
}

function copyText(text) {
  if (navigator.clipboard) return navigator.clipboard.writeText(text).catch(() => {});
  return Promise.resolve();
}

function haversineKm(lat1, lng1, lat2, lng2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180)
    * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

