package server.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads config/app.properties; environment variables override file values. */
public final class AppConfig {

  private static Properties props;
  private static boolean loaded;

  private AppConfig() {}

  private static synchronized void ensureLoaded() {
    if (loaded) return;
    props = new Properties();
    try {
      Path dir = AppPaths.root().resolve("config");
      Files.createDirectories(dir);
      Path file = dir.resolve("app.properties");
      if (!Files.exists(file)) {
        Path example = dir.resolve("app.properties.example");
        if (Files.exists(example)) {
          Files.copy(example, file);
          System.out.println("[Config] Created " + file.toAbsolutePath() + " — add your SMTP password there.");
        }
      }
      if (Files.exists(file)) {
        try (InputStream in = Files.newInputStream(file)) {
          props.load(in);
        }
        System.out.println("[Config] Loaded " + file.toAbsolutePath());
      }
    } catch (IOException e) {
      System.err.println("[Config] Could not load app.properties: " + e.getMessage());
    }
    loaded = true;
  }

  public static String get(String key) {
    ensureLoaded();
    String env = envForKey(key);
    if (env != null && !env.isBlank()) return env.trim();
    return props.getProperty(key, "").trim();
  }

  public static boolean has(String key) {
    return !get(key).isBlank();
  }

  public static String smtpHost() { return get("smtp.host"); }
  public static int smtpPort() {
    String p = get("smtp.port");
    if (p.isBlank()) return 587;
    try { return Integer.parseInt(p); } catch (NumberFormatException e) { return 587; }
  }
  public static String smtpUser() { return get("smtp.user").trim(); }
  /** Gmail app passwords are often pasted with spaces — strip them for SMTP AUTH. */
  public static String smtpPassword() {
    return get("smtp.password").replace(" ", "").trim();
  }
  public static String smtpFrom() {
    String from = get("smtp.from");
    return from.isBlank() ? smtpUser() : from;
  }
  public static boolean smtpUseSsl() {
    String v = get("smtp.ssl");
    return "true".equalsIgnoreCase(v) || smtpPort() == 465;
  }

  public static boolean smtpTrustAll() {
    return "true".equalsIgnoreCase(get("smtp.trust.all"));
  }

  public static String googleClientId() { return get("google.client.id"); }

  public static int serverPort() {
    String p = get("server.port");
    if (p.isBlank()) return 8080;
    try { return Integer.parseInt(p); } catch (NumberFormatException e) { return 8080; }
  }

  public static String appBaseUrl(int port) {
    return "http://localhost:" + port + "/";
  }

  public static String mysqlHost() { return get("mysql.host").isBlank() ? "localhost" : get("mysql.host"); }
  public static int mysqlPort() {
    String p = get("mysql.port");
    if (p.isBlank()) return 3306;
    try { return Integer.parseInt(p); } catch (NumberFormatException e) { return 3306; }
  }
  public static String mysqlDatabase() { return get("mysql.database").isBlank() ? "safepath" : get("mysql.database"); }
  public static String mysqlUser() { return get("mysql.user").isBlank() ? "root" : get("mysql.user"); }
  public static String mysqlPassword() { return get("mysql.password"); }

  public static String mysqlJdbcUrl() {
    return "jdbc:mysql://" + mysqlHost() + ":" + mysqlPort() + "/" + mysqlDatabase()
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
  }

  private static String envForKey(String key) {
    return switch (key) {
      case "smtp.host" -> System.getenv("SAFEPATH_SMTP_HOST");
      case "smtp.port" -> System.getenv("SAFEPATH_SMTP_PORT");
      case "smtp.user" -> System.getenv("SAFEPATH_SMTP_USER");
      case "smtp.password" -> System.getenv("SAFEPATH_SMTP_PASS");
      case "smtp.from" -> System.getenv("SAFEPATH_FROM_EMAIL");
      case "smtp.ssl" -> System.getenv("SAFEPATH_SMTP_SSL");
      case "smtp.trust.all" -> System.getenv("SAFEPATH_SMTP_TRUST_ALL");
      case "google.client.id" -> System.getenv("SAFEPATH_GOOGLE_CLIENT_ID");
      case "server.port" -> System.getenv("SAFEPATH_PORT");
      case "mysql.host" -> System.getenv("SAFEPATH_MYSQL_HOST");
      case "mysql.port" -> System.getenv("SAFEPATH_MYSQL_PORT");
      case "mysql.database" -> System.getenv("SAFEPATH_MYSQL_DATABASE");
      case "mysql.user" -> System.getenv("SAFEPATH_MYSQL_USER");
      case "mysql.password" -> System.getenv("SAFEPATH_MYSQL_PASSWORD");
      default -> null;
    };
  }
}
