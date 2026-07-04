package server.services;

import server.db.Database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.*;

public class AuthService {

  private final SecureRandom random = new SecureRandom();
  private final GoogleAuthService googleAuthService = new GoogleAuthService();

  public Map<String, Object> register(String name, String email, String password) throws IOException {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
    if (email == null || !email.contains("@")) throw new IllegalArgumentException("Valid email is required");
    if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters");

    try {
      Database db = Database.get();
      String normalized = email.trim().toLowerCase();
      if (db.emailExists(normalized)) {
        throw new IllegalArgumentException("Email already registered");
      }

      String salt = randomSalt();
      String hash = hashPassword(password, salt);
      String userId = UUID.randomUUID().toString();

      Map<String, String> user = new LinkedHashMap<>();
      user.put("id", userId);
      user.put("name", name.trim());
      user.put("email", normalized);
      user.put("salt", salt);
      user.put("passwordHash", hash);
      user.put("createdAt", String.valueOf(System.currentTimeMillis()));

      db.insertUser(user);
      return issueToken(user);
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  public Map<String, Object> login(String email, String password) throws IOException {
    try {
      Database db = Database.get();
      String normalized = email.trim().toLowerCase();
      Map<String, String> found = db.findUserByEmail(normalized);
      if (found == null) throw new IllegalArgumentException("Invalid email or password");

      String hash = hashPassword(password, found.get("salt"));
      if (!hash.equals(found.get("passwordHash"))) {
        throw new IllegalArgumentException("Invalid email or password");
      }

      return issueToken(found);
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  public Map<String, Object> loginWithGoogleIdToken(String idToken) throws IOException {
    Map<String, String> profile = googleAuthService.verifyIdToken(idToken);
    return findOrCreateGoogleUser(profile.get("email"), profile.get("name"));
  }

  private Map<String, Object> findOrCreateGoogleUser(String email, String name) throws IOException {
    if (name == null || name.isBlank()) {
      name = email.split("@")[0];
    }
    try {
      Database db = Database.get();
      Map<String, String> existing = db.findUserByEmail(email);
      if (existing != null) return issueToken(existing);

      String salt = randomSalt();
      String hash = hashPassword(UUID.randomUUID().toString(), salt);
      Map<String, String> user = new LinkedHashMap<>();
      user.put("id", UUID.randomUUID().toString());
      user.put("name", name.trim());
      user.put("email", email.toLowerCase());
      user.put("salt", salt);
      user.put("passwordHash", hash);
      user.put("createdAt", String.valueOf(System.currentTimeMillis()));
      db.insertUser(user);
      return issueToken(user);
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  private Map<String, Object> issueToken(Map<String, String> found) throws IOException {
    try {
      String token = UUID.randomUUID().toString();
      Database.get().insertToken(token, found.get("id"));
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("token", token);
      result.put("userId", found.get("id"));
      result.put("name", found.get("name"));
      result.put("email", found.get("email"));
      return result;
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  public Map<String, Object> userFromToken(String token) throws IOException {
    if (token == null || token.isBlank()) return null;
    try {
      Database db = Database.get();
      String userId = db.findUserIdByToken(token);
      if (userId == null) return null;
      Map<String, String> u = db.findUserById(userId);
      if (u == null) return null;
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("id", u.get("id"));
      out.put("name", u.get("name"));
      out.put("email", u.get("email"));
      return out;
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  /** Creates a reset token and queues email (always returns success to avoid email enumeration). */
  public Map<String, Object> requestPasswordReset(String email) throws IOException {
    if (email == null || !email.contains("@")) {
      throw new IllegalArgumentException("Valid email is required");
    }
    try {
      Database db = Database.get();
      String normalized = email.trim().toLowerCase();
      Map<String, String> user = db.findUserByEmail(normalized);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("status", "success");
      result.put("message", "If that email is registered, reset instructions were sent.");
      if (user == null) return result;

      String token = UUID.randomUUID().toString().replace("-", "");
      long expires = System.currentTimeMillis() + 60L * 60 * 1000;
      db.deletePasswordResetTokensForUser(user.get("id"));
      db.insertPasswordResetToken(token, user.get("id"), expires);

      int port = server.util.AppConfig.serverPort();
      String base = server.util.AppConfig.appBaseUrl(port);
      String localBase = server.util.AppConfig.localBaseUrl(port);
      String resetLink = base + "reset-password.html?token=" + token;
      String subject = "SafePath AI - Password Reset";
      StringBuilder body = new StringBuilder();
      body.append("Hello ").append(user.get("name")).append(",\n\n")
          .append("Reset your SafePath password (valid 1 hour):\n\n")
          .append(resetLink).append("\n\n")
          .append("Keep SafePath running, then open the link in your browser.\n");
      if (!base.equals(localBase)) {
        body.append("On this PC only: ").append(localBase)
            .append("reset-password.html?token=").append(token).append("\n\n");
      }
      body.append("If you did not request this, ignore this email.");
      boolean delivered = new EmailService().sendSync(normalized, subject, body.toString());
      result.put("emailQueued", true);
      result.put("emailDelivered", delivered);
      if (resetLink.contains("localhost") || resetLink.contains("127.0.0.1")) {
        System.out.println("[PasswordReset] Dev reset link: " + resetLink);
      }
      if (!delivered) {
        result.put("deliveryNote", EmailService.isSmtpConfigured()
            ? "SMTP send failed — check server console. Reset link saved in email_outbox table."
            : "SMTP not configured — reset link saved in email_outbox only.");
      }
      return result;
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  public boolean isResetTokenValid(String token) throws IOException {
    if (token == null || token.isBlank()) return false;
    try {
      return Database.get().findUserIdByResetToken(token.trim()) != null;
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  public void resetPassword(String token, String newPassword) throws IOException {
    if (token == null || token.isBlank()) throw new IllegalArgumentException("Reset token is required");
    token = token.trim();
    if (newPassword == null || newPassword.length() < 6) {
      throw new IllegalArgumentException("Password must be at least 6 characters");
    }
    try {
      Database db = Database.get();
      String userId = db.findUserIdByResetToken(token);
      if (userId == null) throw new IllegalArgumentException("Invalid or expired reset token");

      String salt = randomSalt();
      String hash = hashPassword(newPassword, salt);
      db.updateUserPassword(userId, salt, hash);
      db.deletePasswordResetToken(token);
    } catch (SQLException e) {
      throw dbError(e);
    }
  }

  private static IOException dbError(SQLException e) {
    return new IOException("Database error: " + e.getMessage(), e);
  }

  private String randomSalt() {
    byte[] bytes = new byte[16];
    random.nextBytes(bytes);
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  private String hashPassword(String password, String salt) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : dig) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
