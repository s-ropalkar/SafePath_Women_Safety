package server.services;

import server.db.Database;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Guardian CRUD backed by MySQL. Alert flows load guardians from the database by userId.
 */
public class GuardianService {

  private final AuthService authService = new AuthService();

  public List<Map<String, Object>> listForUser(String token) throws IOException {
    Map<String, Object> user = authService.userFromToken(token);
    if (user == null) throw new IllegalArgumentException("Not authenticated");
    return listForUserId(String.valueOf(user.get("id")));
  }

  public Map<String, Object> add(String token, String name, String phone, String email) throws IOException {
    Map<String, Object> user = authService.userFromToken(token);
    if (user == null) throw new IllegalArgumentException("Not authenticated");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Guardian name required");
    if ((phone == null || phone.isBlank()) && (email == null || !email.contains("@"))) {
      throw new IllegalArgumentException("Guardian phone or email required");
    }

    Map<String, String> g = new LinkedHashMap<>();
    g.put("id", UUID.randomUUID().toString());
    g.put("userId", String.valueOf(user.get("id")));
    g.put("name", name.trim());
    g.put("phone", phone == null ? "" : phone.trim());
    g.put("email", email == null ? "" : email.trim().toLowerCase());
    g.put("createdAt", String.valueOf(System.currentTimeMillis()));

    try {
      Database.get().insertGuardian(g);
    } catch (SQLException e) {
      throw new IOException("Database error: " + e.getMessage(), e);
    }
    return new LinkedHashMap<>(g);
  }

  public void remove(String token, String guardianId) throws IOException {
    Map<String, Object> user = authService.userFromToken(token);
    if (user == null) throw new IllegalArgumentException("Not authenticated");
    String userId = String.valueOf(user.get("id"));
    try {
      Database.get().deleteGuardian(guardianId, userId);
    } catch (SQLException e) {
      throw new IOException("Database error: " + e.getMessage(), e);
    }
  }

  public List<Map<String, Object>> listForUserId(String userId) throws IOException {
    try {
      List<Map<String, Object>> result = new ArrayList<>();
      for (Map<String, String> g : Database.get().findGuardiansByUserId(userId)) {
        result.add(new LinkedHashMap<>(g));
      }
      return result;
    } catch (SQLException e) {
      throw new IOException("Database error: " + e.getMessage(), e);
    }
  }
}
