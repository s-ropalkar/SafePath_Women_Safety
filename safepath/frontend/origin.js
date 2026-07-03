/** SafePath must run on the Java server — not VS Code Live Server. */
const APP_PORT_KEY = "safepath_port";
const DEFAULT_PORT = "8080";

function storedPort() {
  return sessionStorage.getItem(APP_PORT_KEY) || DEFAULT_PORT;
}

function getApiBase() {
  if (location.protocol === "file:") {
    return `http://localhost:${storedPort()}`;
  }
  const appPort = storedPort();
  if (!location.port || location.port === appPort) {
    return location.origin;
  }
  return `${location.protocol}//${location.hostname}:${appPort}`;
}

function isWrongDevServer() {
  const port = location.port;
  if (!port || port === storedPort()) return false;
  return port.startsWith("55") || port === "3000" || port === "5173" || port === "4173";
}

function showServerHelp(reason) {
  const port = storedPort();
  const url = `http://localhost:${port}/`;
  document.documentElement.innerHTML = `
    <head><meta charset="UTF-8"/><title>SafePath — start server</title>
    <style>
      body{font-family:system-ui,sans-serif;background:#0f172a;color:#f8fafc;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px}
      .box{max-width:520px;background:#1e293b;border:1px solid #334155;border-radius:16px;padding:28px;line-height:1.6}
      h1{margin:0 0 12px;font-size:1.4rem;color:#14b8a6}
      code{background:#0f172a;padding:2px 8px;border-radius:6px}
      ol{padding-left:20px}
      a{color:#38bdf8}
    </style></head>
    <body><div class="box">
      <h1>SafePath server is not running</h1>
      <p>${reason}</p>
      <p><strong>Do not use Live Server.</strong> Use one port only:</p>
      <ol>
        <li>Open terminal in <code>safepath/</code></li>
        <li>Run <code>run.bat</code> or press F5 → <em>SafePath: Run Server</em></li>
        <li>Open <a href="${url}">${url}</a></li>
      </ol>
    </div></body>`;
}

async function syncPortFromHealth(base) {
  try {
    const res = await fetch(`${base}/health`, { signal: AbortSignal.timeout(2500) });
    if (!res.ok) return false;
    const data = await res.json();
    if (data.port) sessionStorage.setItem(APP_PORT_KEY, String(data.port));
    return true;
  } catch (_) {
    return false;
  }
}

(async function boot() {
  if (location.protocol === "file:") {
    const ok = await syncPortFromHealth(getApiBase());
    if (ok) {
      location.replace(`${getApiBase()}/login.html`);
      return;
    }
    showServerHelp("You opened an HTML file directly from disk.");
    return;
  }

  if (isWrongDevServer()) {
    const target = getApiBase();
    const ok = await syncPortFromHealth(target);
    const page = (location.pathname.split("/").pop() || "login.html").split("?")[0];
    if (ok && ["login.html", "index.html", "guardian.html"].includes(page)) {
      location.replace(`${getApiBase()}/${page}${location.search || ""}`);
      return;
    }
    showServerHelp("VS Code Live Server uses a different port than the Java backend.");
    return;
  }

  await syncPortFromHealth(location.origin);
})();
