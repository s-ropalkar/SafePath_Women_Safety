package server.core;

import server.graph.Node;
import server.models.UnsafeLocation;
import server.store.UnsafeStore;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.util.*;

/**
 * SafetyEngine – dynamic safety scores (0–100) per node using OSM POIs (Gaussian decay),
 * community unsafe zones, and time-of-day modifiers.
 */
public class SafetyEngine {

  private static final double COMMUNITY_RADIUS_KM = 0.5;
  private static final double UNSAFE_PENALTY_PER_REPORT = 3.0;

  private enum PoiType {
    POLICE(0, 1.0, 0.40, true),
    HOSPITAL(1, 0.65, 0.55, true),
    HOTEL(2, 0.45, 0.60, true),
    METRO(3, 0.75, 0.45, true),
    BUS(4, 0.35, 0.35, true),
    BANK(5, 0.30, 0.30, true),
    BAR(6, 0.55, 0.35, false);

    final int code;
    final double weight;
    final double sigmaKm;
    final boolean positive;

    PoiType(int code, double weight, double sigmaKm, boolean positive) {
      this.code = code;
      this.weight = weight;
      this.sigmaKm = sigmaKm;
      this.positive = positive;
    }
  }

  /** Cached Overpass POIs: {lat, lng, typeCode} */
  private final List<double[]> poiList = new ArrayList<>();
  private final UnsafeStore unsafeStore;

  private double cachedMinLat = Double.MAX_VALUE, cachedMaxLat = -Double.MAX_VALUE;
  private double cachedMinLng = Double.MAX_VALUE, cachedMaxLng = -Double.MAX_VALUE;

  public SafetyEngine(UnsafeStore unsafeStore) {
    this.unsafeStore = unsafeStore;
  }

  public enum TransportMode { WALK, BIKE, CAR, BUS }

  public void loadPOIsForBoundingBox(double minLat, double minLng,
                                     double maxLat, double maxLng) {
    if (minLat >= cachedMinLat && maxLat <= cachedMaxLat
        && minLng >= cachedMinLng && maxLng <= cachedMaxLng) {
      return;
    }
    poiList.clear();
    unsafeStore.refreshCache();
    double pad = 0.02;
    fetchOverpassPOIs(minLat - pad, minLng - pad, maxLat + pad, maxLng + pad);
    cachedMinLat = minLat;
    cachedMaxLat = maxLat;
    cachedMinLng = minLng;
    cachedMaxLng = maxLng;
  }

  private void fetchOverpassPOIs(double s, double w, double n, double e) {
    String bbox = s + "," + w + "," + n + "," + e;
    String query = "[out:json][timeout:12];"
        + "("
        + "node[amenity=police](" + bbox + ");"
        + "node[amenity=hospital](" + bbox + ");"
        + "node[amenity=hotel](" + bbox + ");"
        + "node[tourism=hotel](" + bbox + ");"
        + "node[railway=station][station=subway](" + bbox + ");"
        + "node[amenity=bus_station](" + bbox + ");"
        + "node[highway=bus_stop](" + bbox + ");"
        + "node[amenity=bank](" + bbox + ");"
        + "node[amenity=atm](" + bbox + ");"
        + "node[amenity=bar](" + bbox + ");"
        + "node[amenity=nightclub](" + bbox + ");"
        + ");"
        + "out body;";

    try {
      URL url = new URL("https://overpass-api.de/api/interpreter");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setConnectTimeout(8000);
      conn.setReadTimeout(12000);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

      String body = "data=" + URLEncoder.encode(query, "UTF-8");
      try (OutputStream os = conn.getOutputStream()) {
        os.write(body.getBytes("UTF-8"));
      }

      if (conn.getResponseCode() == 200) {
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
          StringBuilder sb = new StringBuilder();
          String line;
          while ((line = br.readLine()) != null) sb.append(line);
          parseOverpassJson(sb.toString());
        }
      }
      conn.disconnect();
    } catch (Exception ex) {
      System.err.println("[SafetyEngine] Overpass fetch failed: " + ex.getMessage());
    }
  }

  private void parseOverpassJson(String json) {
    int idx = 0;
    while (true) {
      int latIdx = json.indexOf("\"lat\":", idx);
      if (latIdx < 0) break;
      int lonIdx = json.indexOf("\"lon\":", latIdx);
      if (lonIdx < 0) break;

      double lat = extractDouble(json, latIdx + 6);
      double lon = extractDouble(json, lonIdx + 6);

      int tagStart = json.lastIndexOf("{", latIdx);
      int tagEnd = json.indexOf("}", lonIdx);
      String chunk = (tagStart >= 0 && tagEnd > tagStart)
          ? json.substring(tagStart, tagEnd) : "";

      PoiType type = classifyPoi(chunk);
      addPoi(lat, lon, type);
      idx = lonIdx + 6;
    }
  }

  private PoiType classifyPoi(String chunk) {
    if (chunk.contains("\"police\"")) return PoiType.POLICE;
    if (chunk.contains("\"hospital\"")) return PoiType.HOSPITAL;
    if (chunk.contains("\"subway\"")) return PoiType.METRO;
    if (chunk.contains("bus_stop") || chunk.contains("bus_station")) return PoiType.BUS;
    if (chunk.contains("\"bank\"") || chunk.contains("\"atm\"")) return PoiType.BANK;
    if (chunk.contains("\"bar\"") || chunk.contains("\"nightclub\"")) return PoiType.BAR;
    return PoiType.HOTEL;
  }

  private double extractDouble(String s, int start) {
    int end = start;
    while (end < s.length() && (Character.isDigit(s.charAt(end))
        || s.charAt(end) == '.' || s.charAt(end) == '-')) end++;
    try {
      return Double.parseDouble(s.substring(start, end));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private void addPoi(double lat, double lng, PoiType type) {
    poiList.add(new double[]{lat, lng, type.code});
  }

  public double calculateNodeSafety(Node node) {
    return calculateNodeSafety(node, TransportMode.WALK);
  }

  public double calculateNodeSafety(Node node, TransportMode mode) {
    double score = 50.0;
    score += proximityScore(node, mode);
    score += timeOfDayBonus();
    score -= unsafePenalty(node);
    score += transportModifier(mode);
    if (mode == TransportMode.WALK && isNight()) score -= 12;
    return Math.max(0, Math.min(100, score));
  }

  /** Gaussian sum of all nearby POI influences. */
  private double proximityScore(Node node, TransportMode mode) {
    double sum = 0;
    double range = 0.03;

    for (double[] poi : poiList) {
      if (Math.abs(poi[0] - node.getLatitude()) > range
          || Math.abs(poi[1] - node.getLongitude()) > range) {
        continue;
      }
      PoiType type = poiTypeFromCode((int) poi[2]);
      if (type == null) continue;

      Node poiNode = new Node(poi[0], poi[1]);
      double dist = node.distanceTo(poiNode);
      double maxReach = type.sigmaKm * 3.0;
      if (dist > maxReach) continue;

      double gaussian = Math.exp(-(dist * dist) / (2.0 * type.sigmaKm * type.sigmaKm));
      double contribution = type.weight * gaussian * 22.0;
      if (!type.positive) contribution = -contribution;
      if (type == PoiType.BAR && isNight()) contribution *= 1.6;
      if (type == PoiType.METRO && isNight()) contribution *= 1.25;
      sum += contribution;
    }
    return sum;
  }

  private PoiType poiTypeFromCode(int code) {
    for (PoiType t : PoiType.values()) {
      if (t.code == code) return t;
    }
    return null;
  }

  private double timeOfDayBonus() {
    int h = LocalTime.now().getHour();
    if (h >= 6 && h < 21) return 8;
    return -5;
  }

  private double unsafePenalty(Node node) {
    double penalty = 0;
    for (UnsafeLocation loc : unsafeStore.getUnsafeLocations()) {
      Node unsafeNode = new Node(loc.latitude, loc.longitude);
      if (node.distanceTo(unsafeNode) <= COMMUNITY_RADIUS_KM) {
        penalty += effectiveReportWeight(loc) * UNSAFE_PENALTY_PER_REPORT;
      }
    }
    return Math.min(100, penalty);
  }

  /** Recency decay + unverified vs confirmed community weight. */
  private double effectiveReportWeight(UnsafeLocation loc) {
    double recency = recencyFactor(loc.createdAt);
    double confirmation = loc.reportCount >= 3 ? 1.0 : 0.45;
    return loc.reportCount * recency * confirmation;
  }

  private double recencyFactor(long createdAtMs) {
    long ageMs = System.currentTimeMillis() - createdAtMs;
    long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
    return ageMs <= thirtyDaysMs ? 1.0 : 0.5;
  }

  public boolean isConfirmedZone(UnsafeLocation loc) {
    return loc.reportCount >= 3;
  }

  public double predictSafetyForward(Node node, TransportMode mode, int minutesAhead) {
    double now = calculateNodeSafety(node, mode);
    double delta = 0;
    int h = LocalTime.now().getHour();
    int mins = Math.max(15, Math.min(60, minutesAhead));
    int futureHour = (h + (mins / 60)) % 24;
    boolean nightNow = isNight();
    boolean nightLater = futureHour >= 21 || futureHour < 6;

    if (!nightNow && nightLater) delta -= 7;
    if (nightNow || nightLater) delta -= 2.5 * (mins / 15.0);

    int nearbyExposure = (int) Math.round(nearbyReportExposure(node));
    if (nearbyExposure > 0) delta -= nearbyExposure * 0.9;

    if ((nightNow || nightLater) && countPoisNear(node, PoiType.METRO, 0.9) == 0) {
      delta -= 5;
    }

    return Math.max(0, Math.min(100, now + delta));
  }

  public double computeConfidence(Node node) {
    double confidence = 72;
    int police = countPoisNear(node, PoiType.POLICE, 1.0);
    int metro = countPoisNear(node, PoiType.METRO, 0.9);
    int hospital = countPoisNear(node, PoiType.HOSPITAL, 1.2);
    confidence += Math.min(14, (police + metro + hospital) * 3);

    double exposure = nearbyReportExposure(node);
    if (exposure > 0) confidence += 6;
    if (poiList.isEmpty()) confidence -= 16;
    if (exposure == 0 && police == 0 && metro == 0) confidence -= 6;

    return Math.max(45, Math.min(98, confidence));
  }

  /** Predicted unsafe-zone probability (0–100) without official crime data. */
  public double predictZoneRiskProbability(double lat, double lng) {
    Node node = new Node(lat, lng);
    double risk = 0;
    for (UnsafeLocation loc : unsafeStore.getUnsafeLocations()) {
      Node unsafeNode = new Node(loc.latitude, loc.longitude);
      double dist = node.distanceTo(unsafeNode);
      if (dist <= 1.2) {
        double proximity = 1.0 - (dist / 1.2);
        risk += effectiveReportWeight(loc) * proximity * 2.5;
      }
    }
    if (isNight()) risk += 10;
    if (countPoisNear(node, PoiType.POLICE, 1.0) == 0) risk += 8;
    if (countPoisNear(node, PoiType.METRO, 0.8) == 0 && isNight()) risk += 6;
    return Math.min(95, Math.max(5, risk));
  }

  public SafetyInsight analyzeLocation(Node node, TransportMode mode) {
    double score = calculateNodeSafety(node, mode);
    double confidence = computeConfidence(node);
    double predicted = predictSafetyForward(node, mode, 30);
    String trend = "STABLE";
    if (predicted < score - 5) trend = "RISING_RISK";
    else if (predicted > score + 5) trend = "IMPROVING";

    List<String> factors = new ArrayList<>();
    factors.add("XAI: Gaussian POI + community proxy (no NCRB — roadmap ready)");
    int police = countPoisNear(node, PoiType.POLICE, 1.0);
    if (police > 0) factors.add("POI coverage: " + police + " police nearby");
    double exposure = nearbyReportExposure(node);
    if (exposure > 0) factors.add("Community exposure: " + (int) exposure + " weighted reports");
    else factors.add("Low community report density");
    if (isNight()) factors.add("Night-time risk elevation applied");
    factors.add("Confidence " + (int) confidence + "% from POI + report freshness");

    return new SafetyInsight(score, confidence, predicted, trend,
        getSafetyStatus(score), factors);
  }

  private double nearbyReportExposure(Node node) {
    double total = 0;
    for (UnsafeLocation loc : unsafeStore.getUnsafeLocations()) {
      Node unsafeNode = new Node(loc.latitude, loc.longitude);
      if (node.distanceTo(unsafeNode) <= COMMUNITY_RADIUS_KM) {
        total += effectiveReportWeight(loc);
      }
    }
    return total;
  }

  private int countPoisNear(Node node, PoiType type, double radiusKm) {
    int count = 0;
    for (double[] poi : poiList) {
      if ((int) poi[2] != type.code) continue;
      if (new Node(poi[0], poi[1]).distanceTo(node) <= radiusKm) count++;
    }
    return count;
  }

  private boolean isNight() {
    int h = LocalTime.now().getHour();
    return h >= 21 || h < 6;
  }

  private double transportModifier(TransportMode mode) {
    return switch (mode) {
      case WALK -> -8;
      case BIKE -> -4;
      case CAR -> 8;
      case BUS -> 4;
    };
  }

  /** Human-readable reasons for route comparison UI. */
  public List<String> buildRouteReasons(List<Node> path, List<Node> comparePath, TransportMode mode) {
    List<String> reasons = new ArrayList<>();
    if (path == null || path.size() < 2) {
      reasons.add("Route scored with Gaussian OSM POI safety model");
      return reasons;
    }

    int police = countUniquePoisNearPath(path, PoiType.POLICE, 1.2);
    int metro = countUniquePoisNearPath(path, PoiType.METRO, 1.0);
    int hospitals = countUniquePoisNearPath(path, PoiType.HOSPITAL, 1.2);
    int busStops = countUniquePoisNearPath(path, PoiType.BUS, 0.6);
    int exposure = reportExposure(path);

    if (comparePath != null && comparePath.size() >= 2) {
      int compareExposure = reportExposure(comparePath);
      int avoided = Math.max(0, compareExposure - exposure);
      if (avoided > 0) {
        reasons.add("Avoids " + avoided + " community report" + (avoided == 1 ? "" : "s"));
      }
    } else if (exposure == 0) {
      reasons.add("No community reports along route");
    }

    if (police > 0) {
      reasons.add("Near " + police + " police station" + (police == 1 ? "" : "s"));
    }
    if (metro > 0) {
      reasons.add("Near " + metro + " metro station" + (metro == 1 ? "" : "s"));
    }
    if (hospitals > 0) {
      reasons.add("Near " + hospitals + " hospital" + (hospitals == 1 ? "" : "s"));
    }
    if (busStops > 0 && reasons.size() < 4) {
      reasons.add("Passes " + busStops + " bus stop" + (busStops == 1 ? "" : "s"));
    }

    if (isNight() && metro > 0) {
      reasons.add("Metro access for safer night travel");
    }

    if (reasons.isEmpty()) {
      reasons.add("Scored using police, metro, hospital & community data");
      reasons.add("Gaussian decay POI model (OpenStreetMap)");
    }
    reasons.add(0, "Explainable Safety Score (XAI)");
    return reasons;
  }

  public double routeConfidence(List<Node> path, TransportMode mode) {
    if (path == null || path.isEmpty()) return 70;
    double sum = 0;
    for (Node n : samplePath(path, 4)) {
      sum += computeConfidence(n);
    }
    return sum / Math.max(1, samplePath(path, 4).size());
  }

  private int countUniquePoisNearPath(List<Node> path, PoiType type, double radiusKm) {
    Set<String> seen = new HashSet<>();
    List<Node> samples = samplePath(path, 3);
    for (Node point : samples) {
      for (double[] poi : poiList) {
        if ((int) poi[2] != type.code) continue;
        Node poiNode = new Node(poi[0], poi[1]);
        if (point.distanceTo(poiNode) <= radiusKm) {
          seen.add(String.format("%.4f,%.4f", poi[0], poi[1]));
        }
      }
    }
    return seen.size();
  }

  private int reportExposure(List<Node> path) {
    double total = 0;
    List<Node> samples = samplePath(path, 2);
    for (UnsafeLocation loc : unsafeStore.getUnsafeLocations()) {
      Node unsafeNode = new Node(loc.latitude, loc.longitude);
      for (Node point : samples) {
        if (point.distanceTo(unsafeNode) <= COMMUNITY_RADIUS_KM) {
          total += effectiveReportWeight(loc);
          break;
        }
      }
    }
    return (int) Math.round(total);
  }

  private List<Node> samplePath(List<Node> path, int step) {
    List<Node> samples = new ArrayList<>();
    for (int i = 0; i < path.size(); i += step) {
      samples.add(path.get(i));
    }
    if (!path.isEmpty() && (path.size() - 1) % step != 0) {
      samples.add(path.get(path.size() - 1));
    }
    return samples;
  }

  public String getSafetyStatus(double score) {
    if (score >= 70) return "SAFE";
    if (score >= 50) return "MODERATE";
    return "RISKY";
  }

  public String getSafetyColor(double score) {
    if (score >= 70) return "#00CC00";
    if (score >= 50) return "#FFAA00";
    return "#FF0000";
  }
}
