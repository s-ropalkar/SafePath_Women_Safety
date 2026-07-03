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
    String query = "[out:json][timeout:15];"
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

    String json = postOverpass(query);
    return parseElements(json);
  }

  private static String postOverpass(String query) throws IOException {
    URL url = new URL("https://overpass-api.de/api/interpreter");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setConnectTimeout(10000);
    conn.setReadTimeout(15000);
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

  private static List<Map<String, Object>> parseElements(String json) {
    List<Map<String, Object>> out = new ArrayList<>();
    int idx = 0;
    while (true) {
      int latIdx = json.indexOf("\"lat\":", idx);
      if (latIdx < 0) break;
      int lonIdx = json.indexOf("\"lon\":", latIdx);
      if (lonIdx < 0) break;

      double lat = readDouble(json, latIdx + 6);
      double lon = readDouble(json, lonIdx + 6);

      int tagStart = json.lastIndexOf("{", latIdx);
      int tagEnd = json.indexOf("}", lonIdx);
      String chunk = (tagStart >= 0 && tagEnd > tagStart)
          ? json.substring(tagStart, tagEnd) : "";

      String type = classifyType(chunk);
      String name = extractTag(chunk, "name");
      if (name.isEmpty()) name = "Unnamed safe spot";

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("lat", lat);
      row.put("lng", lon);
      row.put("type", type);
      row.put("name", name);
      out.add(row);
      idx = lonIdx + 6;
    }
    return out;
  }

  private static String classifyType(String chunk) {
    if (chunk.contains("\"police\"")) return "police";
    if (chunk.contains("\"hospital\"")) return "hospital";
    if (chunk.contains("\"hostel\"")) return "hostel";
    if (chunk.contains("\"hotel\"")) return "hotel";
    if (chunk.contains("bus_stop")) return "bus";
    if (chunk.contains("\"station\"")) return "metro";
    return "hotel";
  }

  private static String extractTag(String chunk, String key) {
    String needle = "\"" + key + "\":\"";
    int i = chunk.indexOf(needle);
    if (i < 0) return "";
    int start = i + needle.length();
    int end = chunk.indexOf('"', start);
    if (end < 0) return "";
    return chunk.substring(start, end);
  }

  private static double readDouble(String s, int start) {
    int end = start;
    while (end < s.length() && (Character.isDigit(s.charAt(end))
        || s.charAt(end) == '.' || s.charAt(end) == '-')) end++;
    try {
      return Double.parseDouble(s.substring(start, end));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
