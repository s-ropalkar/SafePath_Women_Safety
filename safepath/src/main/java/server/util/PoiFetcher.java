package server.util;

import java.io.*;
import java.net.*;
import java.util.*;

/** Fetches OSM POIs via Overpass for the frontend map (server-side proxy). */
public final class PoiFetcher {

  private PoiFetcher() {}

  public static List<Map<String, Object>> fetch(double south, double west, double north, double east)
      throws IOException {
    String bbox = south + "," + west + "," + north + "," + east;
    String query = "[out:json][timeout:25];"
        + "("
        + "node[amenity=police](" + bbox + ");"
        + "node[amenity=hospital](" + bbox + ");"
        + "node[amenity=hotel](" + bbox + ");"
        + "node[tourism=hotel](" + bbox + ");"
        + "node[amenity=hostel](" + bbox + ");"
        + "node[railway=station](" + bbox + ");"
        + "node[highway=bus_stop](" + bbox + ");"
        + ");"
        + "out body;";

    IOException lastError = null;
    for (String endpoint : OVERPASS_ENDPOINTS) {
      try {
        String json = postOverpass(endpoint, query);
        List<Map<String, Object>> pois = parseElements(json, south, west, north, east);
        if (!pois.isEmpty()) return pois;
      } catch (IOException e) {
        lastError = e;
        System.err.println("[PoiFetcher] " + endpoint + ": " + e.getMessage());
      }
    }
    if (lastError != null) throw lastError;
    return List.of();
  }

  private static final String[] OVERPASS_ENDPOINTS = {
      "https://overpass-api.de/api/interpreter",
      "https://overpass.kumi.systems/api/interpreter",
  };

  private static String postOverpass(String endpoint, String query) throws IOException {
    URL url = new URL(endpoint);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setConnectTimeout(15000);
    conn.setReadTimeout(25000);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

    String body = "data=" + URLEncoder.encode(query, "UTF-8");
    try (OutputStream os = conn.getOutputStream()) {
      os.write(body.getBytes("UTF-8"));
    }

    int code = conn.getResponseCode();
    InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
    if (stream == null) {
      conn.disconnect();
      throw new IOException("Overpass HTTP " + code);
    }

    try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) sb.append(line);
      conn.disconnect();
      if (code >= 400) {
        throw new IOException("Overpass error: " + sb);
      }
      return sb.toString();
    }
  }

  /** Parse Overpass "elements" array — only type=node with lat/lon inside the route bbox. */
  private static List<Map<String, Object>> parseElements(
      String json, double south, double west, double north, double east) {
    List<Map<String, Object>> out = new ArrayList<>();
    int elementsIdx = json.indexOf("\"elements\"");
    if (elementsIdx < 0) return out;
    int arrStart = json.indexOf('[', elementsIdx);
    if (arrStart < 0) return out;
    int arrEnd = matchingClose(json, arrStart);
    if (arrEnd <= arrStart) return out;

    String arrBody = json.substring(arrStart + 1, arrEnd);
    int pos = 0;
    while (pos < arrBody.length()) {
      int objStart = arrBody.indexOf('{', pos);
      if (objStart < 0) break;
      int objEnd = matchingClose(arrBody, objStart);
      if (objEnd < 0) break;

      String el = arrBody.substring(objStart, objEnd + 1);
      pos = objEnd + 1;

      if (!el.contains("\"type\":\"node\"") && !el.contains("\"type\": \"node\"")) continue;

      Double lat = extractJsonNumber(el, "lat");
      Double lon = extractJsonNumber(el, "lon");
      if (lat == null || lon == null) continue;
      if (Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
      if (lat < south || lat > north || lon < west || lon > east) continue;

      String type = classifyTypeFromTags(el);
      if (type == null) continue;

      String name = extractTagValue(el, "name");
      if (name.isEmpty()) name = "Unnamed safe spot";

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("lat", lat);
      row.put("lng", lon);
      row.put("type", type);
      row.put("name", name);
      out.add(row);
    }
    return out;
  }

  private static String classifyTypeFromTags(String el) {
    if (tagEquals(el, "amenity", "police")) return "police";
    if (tagEquals(el, "amenity", "hospital")) return "hospital";
    if (tagEquals(el, "amenity", "hostel")) return "hostel";
    if (tagEquals(el, "amenity", "hotel") || tagEquals(el, "tourism", "hotel")) return "hotel";
    if (tagEquals(el, "highway", "bus_stop")) return "bus";
    if (tagEquals(el, "railway", "station")) return "metro";
    return null;
  }

  private static boolean tagEquals(String el, String key, String value) {
    return el.contains("\"" + key + "\":\"" + value + "\"")
        || el.contains("\"" + key + "\": \"" + value + "\"");
  }

  private static String extractTagValue(String el, String key) {
    String[] patterns = {
        "\"" + key + "\":\"",
        "\"" + key + "\": \""
    };
    for (String needle : patterns) {
      int i = el.indexOf(needle);
      if (i < 0) continue;
      int start = i + needle.length();
      int end = el.indexOf('"', start);
      if (end > start) return el.substring(start, end);
    }
    return "";
  }

  private static Double extractJsonNumber(String json, String key) {
    String[] patterns = { "\"" + key + "\":", "\"" + key + "\": " };
    for (String needle : patterns) {
      int i = json.indexOf(needle);
      if (i < 0) continue;
      int start = i + needle.length();
      while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
      int end = start;
      while (end < json.length()) {
        char c = json.charAt(end);
        if (Character.isDigit(c) || c == '.' || c == '-' || c == 'e' || c == 'E' || c == '+') {
          end++;
        } else {
          break;
        }
      }
      try {
        return Double.parseDouble(json.substring(start, end));
      } catch (NumberFormatException ignored) { /* try next pattern */ }
    }
    return null;
  }

  private static int matchingClose(String s, int open) {
    if (open >= s.length()) return -1;
    char openChar = s.charAt(open);
    char closeChar = openChar == '{' ? '}' : ']';
    int depth = 0;
    boolean inStr = false;
    for (int i = open; i < s.length(); i++) {
      char c = s.charAt(i);
      if (inStr) {
        if (c == '\\') { i++; continue; }
        if (c == '"') inStr = false;
      } else {
        if (c == '"') inStr = true;
        else if (c == openChar) depth++;
        else if (c == closeChar) {
          depth--;
          if (depth == 0) return i;
        }
      }
    }
    return -1;
  }
}
