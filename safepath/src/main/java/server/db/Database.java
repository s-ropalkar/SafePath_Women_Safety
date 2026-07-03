package server.db;

import server.util.AppConfig;

import java.sql.*;
import java.util.*;
import server.models.UnsafeLocation;

/**
 * MySQL storage via JDBC. Users, guardians, auth tokens, and email outbox
 * are persisted in MySQL only (no flat-file database).
 */
public class Database {

  private static Database instance;

  private Database() throws SQLException {
    initSchema();
  }

  public static synchronized Database get() throws SQLException {
    if (instance == null) {
      instance = new Database();
    }
    return instance;
  }

  private void initSchema() throws SQLException {
    String dbName = AppConfig.mysqlDatabase();
    String user = AppConfig.mysqlUser();
    String pass = AppConfig.mysqlPassword();

    if (AppConfig.mysqlAutoCreateDatabase()) {
      try (Connection conn = DriverManager.getConnection(
              AppConfig.mysqlServerJdbcUrl(), user, pass);
           Statement st = conn.createStatement()) {
        st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName
            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
      } catch (SQLException e) {
        System.err.println("[Database] CREATE DATABASE skipped (TiDB Cloud may pre-provision): "
            + e.getMessage());
      }
    }

    try (Connection conn = openConnection();
         Statement st = conn.createStatement()) {
      if (!AppConfig.mysqlAutoCreateDatabase()) {
        repairImportedSchema(st);
      }
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS users (
            id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            email VARCHAR(255) NOT NULL UNIQUE,
            salt VARCHAR(64) NOT NULL,
            password_hash VARCHAR(128) NOT NULL,
            created_at BIGINT NOT NULL
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
          """);
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS guardians (
            id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
            user_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
            name VARCHAR(255) NOT NULL,
            phone VARCHAR(64) NOT NULL DEFAULT '',
            email VARCHAR(255) NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            INDEX idx_guardians_user (user_id)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
          """);
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS auth_tokens (
            token VARCHAR(64) PRIMARY KEY,
            user_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
            created_at BIGINT NOT NULL,
            INDEX idx_tokens_user (user_id)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
          """);
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS email_outbox (
            id VARCHAR(36) PRIMARY KEY,
            recipient VARCHAR(255) NOT NULL,
            subject VARCHAR(512) NOT NULL,
            body MEDIUMTEXT NOT NULL,
            created_at BIGINT NOT NULL,
            INDEX idx_outbox_created (created_at)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS unsafe_locations (
            id INT AUTO_INCREMENT PRIMARY KEY,
            latitude DOUBLE NOT NULL,
            longitude DOUBLE NOT NULL,
            report_count INT NOT NULL DEFAULT 1,
            reason VARCHAR(255),
            created_at BIGINT NOT NULL,
            INDEX idx_unsafe_lat (latitude),
            INDEX idx_unsafe_lng (longitude)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS password_reset_tokens (
            token VARCHAR(64) PRIMARY KEY,
            user_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
            expires_at BIGINT NOT NULL,
            INDEX idx_reset_user (user_id)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
          """);
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS trip_history (
            id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
            user_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
            session_id VARCHAR(64),
            source_label VARCHAR(512) NOT NULL DEFAULT '',
            dest_label VARCHAR(512) NOT NULL DEFAULT '',
            distance_km DOUBLE NOT NULL DEFAULT 0,
            safety_score DOUBLE NOT NULL DEFAULT 50,
            route_type VARCHAR(32) NOT NULL DEFAULT 'balanced',
            rating INT NOT NULL DEFAULT 0,
            completed_at BIGINT NOT NULL,
            INDEX idx_trip_history_user (user_id)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
          """);
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS user_unsafe_reports (
            id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
            user_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
            latitude DOUBLE NOT NULL,
            longitude DOUBLE NOT NULL,
            category VARCHAR(128) NOT NULL DEFAULT '',
            severity VARCHAR(32) NOT NULL DEFAULT '',
            description TEXT,
            reason VARCHAR(255) NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            INDEX idx_user_unsafe_user (user_id)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
          """);
      System.out.println("MySQL schema ready (users, guardians, trip_history, user_unsafe_reports, …)");
    }
  }

  /** TiDB/MySQL dumps may import FKs with incompatible collations — recreate child tables. */
  private void repairImportedSchema(Statement st) throws SQLException {
    String[] childTables = {
        "user_unsafe_reports", "trip_history", "password_reset_tokens",
        "auth_tokens", "guardians"
    };
    try {
      st.execute("SET FOREIGN_KEY_CHECKS=0");
      for (String table : childTables) {
        st.executeUpdate("DROP TABLE IF EXISTS `" + table + "`");
      }
      System.out.println("[Database] Recreated TiDB-compatible child tables (no legacy FKs)");
    } finally {
      st.execute("SET FOREIGN_KEY_CHECKS=1");
    }
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(AppConfig.mysqlJdbcUrl(),
        AppConfig.mysqlUser(), AppConfig.mysqlPassword());
  }

  public boolean emailExists(String email) throws SQLException {
    String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, email);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  public Map<String, String> findUserByEmail(String email) throws SQLException {
    String sql = "SELECT id, name, email, salt, password_hash, created_at FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, email);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? mapUser(rs) : null;
      }
    }
  }

  public Map<String, String> findUserById(String id) throws SQLException {
    String sql = "SELECT id, name, email, salt, password_hash, created_at FROM users WHERE id = ? LIMIT 1";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? mapUser(rs) : null;
      }
    }
  }

  public void insertUser(Map<String, String> user) throws SQLException {
    String sql = "INSERT INTO users (id, name, email, salt, password_hash, created_at) VALUES (?,?,?,?,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, user.get("id"));
      ps.setString(2, user.get("name"));
      ps.setString(3, user.get("email"));
      ps.setString(4, user.get("salt"));
      ps.setString(5, user.get("passwordHash"));
      ps.setLong(6, Long.parseLong(user.get("createdAt")));
      ps.executeUpdate();
    }
  }

  public String findUserIdByToken(String token) throws SQLException {
    String sql = "SELECT user_id FROM auth_tokens WHERE token = ? LIMIT 1";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, token);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString("user_id") : null;
      }
    }
  }

  public void insertToken(String token, String userId) throws SQLException {
    String sql = "INSERT INTO auth_tokens (token, user_id, created_at) VALUES (?,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, token);
      ps.setString(2, userId);
      ps.setLong(3, System.currentTimeMillis());
      ps.executeUpdate();
    }
  }

  public List<Map<String, String>> findGuardiansByUserId(String userId) throws SQLException {
    // Canonical guardian storage — used by API, alerts, and session read endpoints.
    String sql = "SELECT id, user_id, name, phone, email, created_at FROM guardians WHERE user_id = ? ORDER BY created_at";
    List<Map<String, String>> list = new ArrayList<>();
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(mapGuardian(rs));
      }
    }
    return list;
  }

  public void insertGuardian(Map<String, String> guardian) throws SQLException {
    String sql = "INSERT INTO guardians (id, user_id, name, phone, email, created_at) VALUES (?,?,?,?,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, guardian.get("id"));
      ps.setString(2, guardian.get("userId"));
      ps.setString(3, guardian.get("name"));
      ps.setString(4, guardian.get("phone"));
      ps.setString(5, guardian.get("email"));
      ps.setLong(6, Long.parseLong(guardian.get("createdAt")));
      ps.executeUpdate();
    }
  }

  public void deleteGuardian(String guardianId, String userId) throws SQLException {
    String sql = "DELETE FROM guardians WHERE id = ? AND user_id = ?";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, guardianId);
      ps.setString(2, userId);
      ps.executeUpdate();
    }
  }

  public void insertPasswordResetToken(String token, String userId, long expiresAt) throws SQLException {
    String sql = "INSERT INTO password_reset_tokens (token, user_id, expires_at) VALUES (?,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, token);
      ps.setString(2, userId);
      ps.setLong(3, expiresAt);
      ps.executeUpdate();
    }
  }

  public String findUserIdByResetToken(String token) throws SQLException {
    String sql = "SELECT user_id, expires_at FROM password_reset_tokens WHERE token = ? LIMIT 1";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, token);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return null;
        if (rs.getLong("expires_at") < System.currentTimeMillis()) {
          deletePasswordResetToken(token);
          return null;
        }
        return rs.getString("user_id");
      }
    }
  }

  public void deletePasswordResetToken(String token) throws SQLException {
    String sql = "DELETE FROM password_reset_tokens WHERE token = ?";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, token);
      ps.executeUpdate();
    }
  }

  public void updateUserPassword(String userId, String salt, String passwordHash) throws SQLException {
    String sql = "UPDATE users SET salt = ?, password_hash = ? WHERE id = ?";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, salt);
      ps.setString(2, passwordHash);
      ps.setString(3, userId);
      ps.executeUpdate();
    }
  }

  public void insertEmailOutbox(String to, String subject, String body) throws SQLException {
    String sql = "INSERT INTO email_outbox (id, recipient, subject, body, created_at) VALUES (?,?,?,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, UUID.randomUUID().toString());
      ps.setString(2, to);
      ps.setString(3, subject);
      ps.setString(4, body);
      ps.setLong(5, System.currentTimeMillis());
      ps.executeUpdate();
    }
  }

  private static final double UNSAFE_MERGE_RADIUS_KM = 0.1;

  /**
   * Add or merge an unsafe report. Nearby existing rows (within ~100 m) increment report_count.
   */
  public void addUnsafeReport(double lat, double lng, String reason) throws SQLException {
    UnsafeLocation nearby = findNearbyUnsafeLocation(lat, lng, UNSAFE_MERGE_RADIUS_KM);
    if (nearby != null) {
      incrementReportCount(nearby.id, reason);
      return;
    }
    String sql = "INSERT INTO unsafe_locations (latitude, longitude, report_count, reason, created_at) "
        + "VALUES (?,?,1,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setDouble(1, lat);
      ps.setDouble(2, lng);
      ps.setString(3, reason == null ? "" : reason);
      ps.setLong(4, System.currentTimeMillis());
      ps.executeUpdate();
    }
  }

  public List<UnsafeLocation> findUnsafeLocations() throws SQLException {
    String sql = "SELECT id, latitude, longitude, report_count, reason, created_at "
        + "FROM unsafe_locations ORDER BY created_at";
    List<UnsafeLocation> list = new ArrayList<>();
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        list.add(mapUnsafeLocation(rs));
      }
    }
    return list;
  }

  public void incrementReportCount(int id, String reason) throws SQLException {
    String sql = "UPDATE unsafe_locations SET report_count = report_count + 1, reason = ?, "
        + "created_at = ? WHERE id = ?";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, reason == null ? "" : reason);
      ps.setLong(2, System.currentTimeMillis());
      ps.setInt(3, id);
      ps.executeUpdate();
    }
  }

  public int countUserReportsSince(String userId, long sinceMs) throws SQLException {
    String sql = "SELECT COUNT(*) FROM user_unsafe_reports WHERE user_id = ? AND created_at >= ?";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.setLong(2, sinceMs);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  public double[] findLastUserReportCoords(String userId) throws SQLException {
    String sql = "SELECT latitude, longitude FROM user_unsafe_reports WHERE user_id = ? "
        + "ORDER BY created_at DESC LIMIT 1";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return new double[]{rs.getDouble("latitude"), rs.getDouble("longitude")};
        }
      }
    }
    return null;
  }

  /** Find an existing unsafe location within radiusKm of the given coordinates. */
  public UnsafeLocation findNearbyUnsafeLocation(double lat, double lng, double radiusKm)
      throws SQLException {
    double pad = radiusKm / 111.0;
    String sql = "SELECT id, latitude, longitude, report_count, reason, created_at "
        + "FROM unsafe_locations WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setDouble(1, lat - pad);
      ps.setDouble(2, lat + pad);
      ps.setDouble(3, lng - pad);
      ps.setDouble(4, lng + pad);
      try (ResultSet rs = ps.executeQuery()) {
        UnsafeLocation closest = null;
        double best = Double.MAX_VALUE;
        while (rs.next()) {
          UnsafeLocation loc = mapUnsafeLocation(rs);
          double dist = haversineKm(lat, lng, loc.latitude, loc.longitude);
          if (dist <= radiusKm && dist < best) {
            best = dist;
            closest = loc;
          }
        }
        return closest;
      }
    }
  }

  private static UnsafeLocation mapUnsafeLocation(ResultSet rs) throws SQLException {
    return new UnsafeLocation(
        rs.getInt("id"),
        rs.getDouble("latitude"),
        rs.getDouble("longitude"),
        rs.getInt("report_count"),
        rs.getString("reason"),
        rs.getLong("created_at"));
  }

  private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
    final int R = 6371;
    double latDistance = Math.toRadians(lat2 - lat1);
    double lngDistance = Math.toRadians(lng2 - lng1);
    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private static Map<String, String> mapUser(ResultSet rs) throws SQLException {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("id", rs.getString("id"));
    row.put("name", rs.getString("name"));
    row.put("email", rs.getString("email"));
    row.put("salt", rs.getString("salt"));
    row.put("passwordHash", rs.getString("password_hash"));
    row.put("createdAt", String.valueOf(rs.getLong("created_at")));
    return row;
  }

  private static Map<String, String> mapGuardian(ResultSet rs) throws SQLException {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("id", rs.getString("id"));
    row.put("userId", rs.getString("user_id"));
    row.put("name", rs.getString("name"));
    row.put("phone", rs.getString("phone"));
    row.put("email", rs.getString("email"));
    row.put("createdAt", String.valueOf(rs.getLong("created_at")));
    return row;
  }

  public void insertTripHistory(String userId, String sessionId, String source, String dest,
      double distanceKm, double safetyScore, String routeType, int rating, long completedAt)
      throws SQLException {
    String sql = "INSERT INTO trip_history (id, user_id, session_id, source_label, dest_label, "
        + "distance_km, safety_score, route_type, rating, completed_at) VALUES (?,?,?,?,?,?,?,?,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, UUID.randomUUID().toString());
      ps.setString(2, userId);
      ps.setString(3, sessionId);
      ps.setString(4, source == null ? "" : source);
      ps.setString(5, dest == null ? "" : dest);
      ps.setDouble(6, distanceKm);
      ps.setDouble(7, safetyScore);
      ps.setString(8, routeType == null ? "balanced" : routeType);
      ps.setInt(9, rating);
      ps.setLong(10, completedAt);
      ps.executeUpdate();
    }
  }

  public List<Map<String, Object>> findTripHistoryByUserId(String userId, int limit)
      throws SQLException {
    String sql = "SELECT source_label, dest_label, distance_km, safety_score, route_type, "
        + "rating, completed_at FROM trip_history WHERE user_id = ? "
        + "ORDER BY completed_at DESC LIMIT ?";
    List<Map<String, Object>> list = new ArrayList<>();
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("source", rs.getString("source_label"));
          row.put("destination", rs.getString("dest_label"));
          row.put("distanceKm", rs.getDouble("distance_km"));
          row.put("safetyScore", rs.getDouble("safety_score"));
          row.put("routeType", rs.getString("route_type"));
          row.put("rating", rs.getInt("rating"));
          row.put("completedAt", rs.getLong("completed_at"));
          list.add(row);
        }
      }
    }
    return list;
  }

  public void insertUserUnsafeReport(String userId, double lat, double lng, String category,
      String severity, String description, String reason) throws SQLException {
    String sql = "INSERT INTO user_unsafe_reports (id, user_id, latitude, longitude, category, "
        + "severity, description, reason, created_at) VALUES (?,?,?,?,?,?,?,?,?)";
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, UUID.randomUUID().toString());
      ps.setString(2, userId);
      ps.setDouble(3, lat);
      ps.setDouble(4, lng);
      ps.setString(5, category == null ? "" : category);
      ps.setString(6, severity == null ? "" : severity);
      ps.setString(7, description == null ? "" : description);
      ps.setString(8, reason == null ? "" : reason);
      ps.setLong(9, System.currentTimeMillis());
      ps.executeUpdate();
    }
  }

  public List<Map<String, Object>> findUserUnsafeReportsByUserId(String userId, int limit)
      throws SQLException {
    String sql = "SELECT latitude, longitude, category, severity, description, reason, created_at "
        + "FROM user_unsafe_reports WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
    List<Map<String, Object>> list = new ArrayList<>();
    try (Connection conn = openConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("latitude", rs.getDouble("latitude"));
          row.put("longitude", rs.getDouble("longitude"));
          row.put("category", rs.getString("category"));
          row.put("severity", rs.getString("severity"));
          row.put("description", rs.getString("description"));
          row.put("reason", rs.getString("reason"));
          row.put("createdAt", rs.getLong("created_at"));
          list.add(row);
        }
      }
    }
    return list;
  }
}
