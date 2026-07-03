/** Shared API response parsing — handles plain-text 404 from stale servers */
async function parseApiJson(res) {
  const text = await res.text();
  if (!text.trim()) {
    return { ok: res.ok, data: {}, text: "" };
  }
  if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
    try {
      return { ok: res.ok, data: JSON.parse(text), text };
    } catch (err) {
      throw new Error(`Invalid server response. Restart SafePath (port 8080). Details: ${err.message}`);
    }
  }
  if (text.includes("404 Not Found") || text.includes("Do not open index.html with Live Server")) {
    throw new Error("Server is outdated or not running. In VS Code run: Tasks → safepath: run server, then reload.");
  }
  throw new Error(text.slice(0, 120) || "Unexpected server response");
}

/** Built-in demo trip — works even if /api/demo-route is unavailable */
const BUILTIN_DEMO_ROUTE = {
  status: "success",
  source: { label: "Connaught Place, New Delhi", lat: 28.6315, lng: 77.2167 },
  destination: { label: "India Gate, New Delhi", lat: 28.6129, lng: 77.2295 },
  mode: "WALK",
  path: [
    [28.6315, 77.2167], [28.6298, 77.2185], [28.6275, 77.2200],
    [28.6250, 77.2225], [28.6220, 77.2250], [28.6185, 77.2270],
    [28.6155, 77.2285], [28.6129, 77.2295],
  ],
};

async function fetchDemoRoute() {
  try {
    const res = await fetch(`${API}/api/demo-route`);
    const { data } = await parseApiJson(res);
    if (res.ok && data.status === "success") return data;
  } catch (err) {
    console.warn("Demo API unavailable, using built-in route:", err.message);
  }
  return BUILTIN_DEMO_ROUTE;
}

/** Warn once if the running server is missing new API routes */
async function checkServerVersion() {
  try {
    const res = await fetch(`${API}/health`);
    const { data } = await parseApiJson(res);
    if (data.apiVersion && data.apiVersion >= 2) return;
    toast("Server is outdated. Run Tasks → safepath: run server, then reload.", "warn");
  } catch (_) {
    /* origin.js handles offline */
  }
}

document.addEventListener("DOMContentLoaded", () => {
  if (typeof getApiBase === "function" && location.pathname.endsWith("index.html")) {
    checkServerVersion();
  }
});
