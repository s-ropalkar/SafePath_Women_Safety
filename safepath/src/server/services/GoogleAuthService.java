package server.services;

import server.util.AppConfig;
import server.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Verifies Google Sign-In ID tokens via Google's tokeninfo endpoint. */
public class GoogleAuthService {

  public Map<String, String> verifyIdToken(String idToken) throws IOException {
    if (idToken == null || idToken.isBlank()) {
      throw new IllegalArgumentException("Google ID token is required");
    }

    String clientId = AppConfig.googleClientId();
    if (clientId.isBlank()) {
      throw new IllegalArgumentException(
          "Google sign-in is not configured. Add google.client.id to config/app.properties");
    }

    String url = "https://oauth2.googleapis.com/tokeninfo?id_token="
        + URLEncoder.encode(idToken.trim(), StandardCharsets.UTF_8);
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(12_000);
    conn.setReadTimeout(12_000);

    int code = conn.getResponseCode();
    String body = readBody(conn, code >= 400);
    if (code != 200) {
      throw new IllegalArgumentException("Invalid Google token. Sign in again.");
    }

    Map<String, Object> info = JsonUtil.parseJson(body);
    String aud = str(info.get("aud"));
    String email = str(info.get("email"));
    String name = str(info.get("name"));
    String emailVerified = str(info.get("email_verified"));

    if (!clientId.equals(aud)) {
      throw new IllegalArgumentException("Google token audience mismatch");
    }
    if (email.isBlank() || !email.contains("@")) {
      throw new IllegalArgumentException("Google account has no email");
    }
    if (!"true".equalsIgnoreCase(emailVerified)) {
      throw new IllegalArgumentException("Google email is not verified");
    }
    if (name.isBlank()) {
      name = email.split("@")[0];
    }

    return Map.of("email", email.toLowerCase(), "name", name);
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  private static String readBody(HttpURLConnection conn, boolean error) throws IOException {
    try (BufferedReader r = new BufferedReader(new InputStreamReader(
        error ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = r.readLine()) != null) sb.append(line);
      return sb.toString();
    }
  }
}
