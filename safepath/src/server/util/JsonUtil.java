package server.util;

import java.util.*;

/**
 * Minimal JSON serialiser + parser.
 * The original parser broke on nested objects (coordinates field).
 * This version correctly tracks bracket depth.
 */
public class JsonUtil {

  // ── Serialisation ──────────────────────────────────────────────────────────

  public static String toJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder("{");
    int i = 0;
    for (Map.Entry<String, Object> e : map.entrySet()) {
      if (i++ > 0) sb.append(',');
      sb.append('"').append(escape(e.getKey())).append("\":");
      sb.append(valueToJson(e.getValue()));
    }
    return sb.append('}').toString();
  }

  @SuppressWarnings("unchecked")
  private static String valueToJson(Object v) {
    if (v == null)          return "null";
    if (v instanceof String)    return "\"" + escape((String) v) + "\"";
    if (v instanceof Number)    return v.toString();
    if (v instanceof Boolean)   return v.toString();
    if (v instanceof double[])  return doubleArrToJson((double[]) v);
    if (v instanceof double[][])return double2dToJson((double[][]) v);
    if (v instanceof List)      return listToJson((List<?>) v);
    if (v instanceof Map)       return toJson((Map<String, Object>) v);
    return "\"" + escape(v.toString()) + "\"";
  }

  private static String listToJson(List<?> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(valueToJson(list.get(i)));
    }
    return sb.append(']').toString();
  }

  private static String doubleArrToJson(double[] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(a[i]);
    }
    return sb.append(']').toString();
  }

  private static String double2dToJson(double[][] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(doubleArrToJson(a[i]));
    }
    return sb.append(']').toString();
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
  }

  // ── Parsing ────────────────────────────────────────────────────────────────

  /**
   * Parses a flat or one-level-nested JSON object.
   * Values that are themselves objects/arrays are kept as raw strings.
   */
  public static Map<String, Object> parseJson(String json) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (json == null) return map;
    json = json.trim();
    if (!json.startsWith("{")) return map;

    // Walk character by character tracking depth
    int i = 1; // skip opening '{'
    int len = json.length();
    while (i < len) {
      // Skip whitespace
      while (i < len && Character.isWhitespace(json.charAt(i))) i++;
      if (i >= len || json.charAt(i) == '}') break;

      // Expect a quoted key
      if (json.charAt(i) != '"') { i++; continue; }
      int keyStart = i + 1;
      i = nextUnescapedQuote(json, keyStart);
      String key = json.substring(keyStart, i);
      i++; // skip closing '"'

      // Skip ':'
      while (i < len && json.charAt(i) != ':') i++;
      i++; // skip ':'
      while (i < len && Character.isWhitespace(json.charAt(i))) i++;

      // Read value
      char c = json.charAt(i);
      String value;
      if (c == '"') {
        int vs = i + 1;
        i = nextUnescapedQuote(json, vs);
        value = json.substring(vs, i);
        i++;
      } else if (c == '{' || c == '[') {
        int end = matchingClose(json, i);
        value = json.substring(i, end + 1);
        i = end + 1;
      } else {
        // number, boolean, null
        int vs = i;
        while (i < len && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
        value = json.substring(vs, i).trim();
      }
      map.put(key, value);

      // Skip comma
      while (i < len && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
    }
    return map;
  }

  private static int nextUnescapedQuote(String s, int from) {
    int i = from;
    while (i < s.length()) {
      if (s.charAt(i) == '\\') { i += 2; continue; }
      if (s.charAt(i) == '"')  return i;
      i++;
    }
    return i;
  }

  private static int matchingClose(String s, int open) {
    char closeChar = s.charAt(open) == '{' ? '}' : ']';
    char openChar  = s.charAt(open);
    int depth = 0, i = open;
    boolean inStr = false;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (inStr) {
        if (c == '\\') { i += 2; continue; }
        if (c == '"')  inStr = false;
      } else {
        if (c == '"')      inStr = true;
        else if (c == openChar)  depth++;
        else if (c == closeChar) { depth--; if (depth == 0) return i; }
      }
      i++;
    }
    return s.length() - 1;
  }
}
