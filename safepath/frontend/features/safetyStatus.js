function updateSafetyStatus(safetyScore) {
  const statusEl = document.getElementById("safetyStatus");
  const scoreEl = document.getElementById("safetyScore");

  const status = getSafetyStatus(safetyScore);
  const color = getSafetyColor(safetyScore);

  if (scoreEl) scoreEl.textContent = safetyScore.toFixed(1);
  if (statusEl) {
    statusEl.textContent = status;
    statusEl.style.color = color;
  }
}

function updateXaiPanel(xai) {
  const confEl = document.getElementById("xaiConfidence");
  const predEl = document.getElementById("xaiPredicted");
  const trendEl = document.getElementById("xaiTrend");
  const factorsEl = document.getElementById("xaiFactors");
  if (!xai) return;

  if (confEl) confEl.textContent = xai.confidence != null ? Math.round(xai.confidence) : "--";
  if (predEl) predEl.textContent = xai.predictedScore30Min != null
    ? Number(xai.predictedScore30Min).toFixed(1) : "--";

  if (trendEl) {
    const trend = (xai.trend || "STABLE").toUpperCase();
    trendEl.className = "xai-trend";
    if (trend === "RISING_RISK") {
      trendEl.classList.add("xai-trend--rising");
      trendEl.textContent = "Trend: rising risk (30 min)";
    } else if (trend === "IMPROVING") {
      trendEl.classList.add("xai-trend--improving");
      trendEl.textContent = "Trend: improving";
    } else {
      trendEl.classList.add("xai-trend--stable");
      trendEl.textContent = "Trend: stable";
    }
  }

  if (factorsEl && xai.xaiFactors && xai.xaiFactors.length) {
    factorsEl.innerHTML = xai.xaiFactors
      .slice(0, 5)
      .map((f) => `<li>${f}</li>`)
      .join("");
  }
}

function getSafetyColor(safetyScore) {
  if (safetyScore >= 70) return "#22c55e";
  if (safetyScore >= 50) return "#f59e0b";
  return "#ef4444";
}

function getSafetyStatus(safetyScore) {
  if (safetyScore >= 70) return "SAFE";
  if (safetyScore >= 50) return "MODERATE";
  return "RISKY";
}
