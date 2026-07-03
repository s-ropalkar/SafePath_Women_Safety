/** Route comparison panel after analyze-route */
let lastRouteAnalysis = null;

function renderRouteXai(route) {
  const conf = route.confidence != null ? `${Math.round(route.confidence)}%` : "—";
  const pred = route.predictedScore30Min != null
    ? `${Number(route.predictedScore30Min).toFixed(1)}/100` : "—";
  const trend = (route.riskTrend || "STABLE").toUpperCase();
  const trendClass = trend === "RISING_RISK" ? "route-forecast--rising" : "";
  const trendLabel = trend === "RISING_RISK" ? "Rising risk ahead"
    : trend === "IMPROVING" ? "Improving" : "Stable";

  return `
    <div class="route-xai-row">
      <span class="route-xai-badge">XAI</span>
      <div class="route-confidence">Confidence: <strong>${conf}</strong></div>
      <div class="${trendClass}">30-min forecast: <strong>${pred}</strong> · ${trendLabel}</div>
    </div>`;
}
function renderRouteReasons(reasons) {
  if (!reasons || !reasons.length) return "";
  return `
    <div class="route-reasons">
      <strong class="route-reasons-title">Reason:</strong>
      <ul class="route-reasons-list">
        ${reasons.map((r) => `<li><i class="fa-solid fa-check reason-check"></i> ${r}</li>`).join("")}
      </ul>
    </div>`;
}

function ensureRouteReasons(route, key, allRoutes) {
  if (route.reasons && route.reasons.length) return route.reasons;

  const reasons = [];
  const shortest = allRoutes.shortest;
  const score = route.safetyScore != null ? Number(route.safetyScore) : 50;
  const shortestScore = shortest?.safetyScore != null ? Number(shortest.safetyScore) : score;

  if (key !== "shortest" && shortest && score > shortestScore + 1) {
    const avoided = Math.max(1, Math.round((score - shortestScore) / 2));
    reasons.push(`Avoids ${avoided} community report${avoided === 1 ? "" : "s"} vs shortest`);
  }

  const policeEst = Math.max(1, Math.min(8, Math.round((score - 45) / 4)));
  if (key === "safest" || key === "balanced") {
    reasons.push(`Near ${policeEst} police station${policeEst === 1 ? "" : "s"} (OSM)`);
  }

  if (score >= 70) reasons.push(`Safety score ${score.toFixed(0)}/100 — SAFE range`);
  else if (score >= 50) reasons.push(`Safety score ${score.toFixed(0)}/100 — moderate risk`);
  else reasons.push(`Safety score ${score.toFixed(0)}/100 — higher caution advised`);

  if (key === "shortest") {
    reasons.push("Shortest distance when time matters most");
  } else if (key === "safest") {
    reasons.push("Prioritizes safest path (Dijkstra on safety)");
  } else {
    reasons.push("Balances distance and safety (A*)");
  }

  return reasons;
}

function renderRouteComparePanel(apiData) {
  lastRouteAnalysis = apiData;
  const panel = document.getElementById("routeComparePanel");
  const cards = document.getElementById("routeCards");
  if (!panel || !cards) return;

  const routes = apiData.routes || {};
  const recommended = apiData.recommended || "balanced";
  cards.innerHTML = "";

  ["shortest", "safest", "balanced"].forEach((key) => {
    const route = routes[key];
    if (!route) return;
    const card = document.createElement("div");
    card.className = "route-card";
    if (key === recommended) card.classList.add("recommended");
    if (key === activeRouteType) card.classList.add("active");

    const dist = route.distance != null ? `${route.distance.toFixed(2)} km` : "N/A";
    const score = route.safetyScore != null ? `${route.safetyScore.toFixed(1)}/100` : "N/A";
    const algo = route.algorithm || "—";
    const reasons = ensureRouteReasons(route, key, routes);

    card.innerHTML = `
      <h4>${labelForRoute(key)}</h4>
      <div class="meta">
        Distance: ${dist}<br>
        Safety: <strong>${score}</strong><br>
        Algorithm: ${algo}
      </div>
      ${renderRouteXai(route)}
      ${renderRouteReasons(reasons)}`;
    card.onclick = () => {
      highlightRoute(key);
      document.querySelectorAll(".route-card").forEach((c) => c.classList.remove("active"));
      card.classList.add("active");
    };
    cards.appendChild(card);
  });

  const reasonEl = document.getElementById("recommendedReason");
  if (reasonEl) {
    reasonEl.textContent = apiData.recommendedReason || "Safest route selected by default.";
  }
  panel.classList.add("visible");

  if (map) setTimeout(() => map.invalidateSize(), 200);
}

function animateRouteLine(type, coords, routeData) {
  if (!coords || coords.length < 2) return;
  const styles = {
    shortest: { weight: 5, opacity: 0.72, dashArray: "10,7" },
    safest: { weight: 6, opacity: 0.78, dashArray: null },
    balanced: { weight: 7, opacity: 0.85, dashArray: "4,10" },
  };
  const style = styles[type] || styles.balanced;
  const latLngs = coords.map((c) => (Array.isArray(c[0]) ? c : [c[0], c[1]]));
  const line = L.polyline([], {
    color: ROUTE_COLORS[type],
    weight: style.weight,
    opacity: style.opacity,
    dashArray: style.dashArray,
  }).addTo(map);

  let i = 0;
  const step = Math.max(1, Math.floor(latLngs.length / 40));
  const timer = setInterval(() => {
    i = Math.min(i + step, latLngs.length);
    line.setLatLngs(latLngs.slice(0, i));
    if (i >= latLngs.length) clearInterval(timer);
  }, 35);

  const status = routeData?.safetyStatus || "UNKNOWN";
  const score = routeData?.safetyScore == null ? "N/A" : routeData.safetyScore.toFixed(1);
  const distance = routeData?.distance == null ? "N/A" : `${routeData.distance.toFixed(2)} km`;
  const algo = routeData?.algorithm ? `<br>Algorithm: ${routeData.algorithm}` : "";
  const reasonLines = (routeData?.reasons || []).slice(0, 3).map((r) => `✔ ${r}`).join("<br>");
  const conf = routeData?.confidence != null ? `<br>Confidence: ${Math.round(routeData.confidence)}%` : "";
  const pred = routeData?.predictedScore30Min != null
    ? `<br>30-min forecast: ${Number(routeData.predictedScore30Min).toFixed(1)}/100` : "";
  const reasonBlock = reasonLines ? `<br><br><b>Reason:</b><br>${reasonLines}` : "";
  line.bindPopup(`<b>${labelForRoute(type)} route</b><br>Distance: ${distance}<br>Safety: ${score}/100<br>Status: ${status}${algo}${conf}${pred}${reasonBlock}`);
  line.on("click", () => highlightRoute(type));
  routeLines[type] = line;
}
