package server.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
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
      Path file = resolveConfigFile();
      if (file != null && Files.exists(file)) {
        try (InputStream in = Files.newInputStream(file)) {
          props.load(in);
        }
        System.out.println("[Config] Loaded " + file.toAbsolutePath());
      } else {
        System.err.println("[Config] No app.properties found — using defaults and environment variables.");
      }
    } catch (IOException e) {
      System.err.println("[Config] Could not load app.properties: " + e.getMessage());
    }
    loaded = true;
  }

  private static Path resolveConfigFile() throws IOException {
    String explicit = System.getenv("SAFEPATH_CONFIG");
    if (explicit != null && !explicit.isBlank()) {
      Path p = Paths.get(explicit.trim()).toAbsolutePath().normalize();
      if (Files.isRegularFile(p)) return p;
      System.err.println("[Config] SAFEPATH_CONFIG points to missing file: " + p);
    }

    Path dir = AppPaths.root().resolve("config");
    Files.createDirectories(dir);
    Path file = dir.resolve("app.properties");
    if (!Files.exists(file)) {
      Path example = dir.resolve("app.properties.example");
      if (Files.exists(example)) {
        Files.copy(example, file);
        System.out.println("[Config] Created " + file.toAbsolutePath() + " — edit MySQL/TiDB credentials.");
      }
    }
    return file;
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
    String renderPort = System.getenv("PORT");
    if (renderPort != null && !renderPort.isBlank()) {
      try { return Integer.parseInt(renderPort.trim()); }
      catch (NumberFormatException ignored) { /* fall through */ }
    }
    String p = get("server.port");
    if (p.isBlank()) return 8080;
    try { return Integer.parseInt(p); } catch (NumberFormatException e) { return 8080; }
  }

  public static String appBaseUrl(int port) {
    String configured = get("app.base.url");
    if (!configured.isBlank()) {
      return configured.endsWith("/") ? configured : configured + "/";
    }
    String renderUrl = System.getenv("RENDER_EXTERNAL_URL");
    if (renderUrl != null && !renderUrl.isBlank()) {
      return renderUrl.endsWith("/") ? renderUrl : renderUrl + "/";
    }
    String lan = detectLanIPv4();
    if (lan != null) {
      return "http://" + lan + ":" + port + "/";
    }
    return localBaseUrl(port);
  }

  public static String localBaseUrl(int port) {
    return "http://localhost:" + port + "/";
  }

  /** First site-local IPv4 (e.g. 192.168.x.x) for email links on same WiFi. */
  public static String detectLanIPv4() {
    try {
      Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
      while (ifaces.hasMoreElements()) {
        NetworkInterface ni = ifaces.nextElement();
        if (!ni.isUp() || ni.isLoopback()) continue;
        for (var addr : ni.getInterfaceAddresses()) {
          InetAddress ip = addr.getAddress();
          if (ip instanceof Inet4Address && !ip.isLoopbackAddress() && ip.isSiteLocalAddress()) {
            return ip.getHostAddress();
          }
        }
      }
    } catch (SocketException ignored) { /* fall through */ }
    return null;
  }

  public static String mysqlHost() {
    return get("mysql.host").isBlank() ? "localhost" : get("mysql.host");
  }

  public static int mysqlPort() {
    String p = get("mysql.port");
    if (p.isBlank()) return 3306;
    try { return Integer.parseInt(p); } catch (NumberFormatException e) { return 3306; }
  }

  public static String mysqlDatabase() {
    return get("mysql.database").isBlank() ? "safepath" : get("mysql.database");
  }

  public static String mysqlUser() {
    return get("mysql.user").isBlank() ? "root" : get("mysql.user");
  }

  public static String mysqlPassword() {
    return get("mysql.password");
  }

  public static String mysqlSslMode() {
    String mode = get("mysql.sslMode");
    return mode.isBlank() ? "REQUIRED" : mode;
  }

  /** JDBC URL for the configured database (TiDB Cloud / MySQL compatible). */
  public static String mysqlJdbcUrl() {
    return buildJdbcUrl("/" + mysqlDatabase());
  }

  /** JDBC URL without database — used for optional CREATE DATABASE. */
  public static String mysqlServerJdbcUrl() {
    return buildJdbcUrl("/");
  }

  private static String buildJdbcUrl(String pathSuffix) {
    return "jdbc:mysql://" + mysqlHost() + ":" + mysqlPort() + pathSuffix
        + "?sslMode=" + mysqlSslMode() + "&serverTimezone=UTC";
  }

  public static boolean mysqlAutoCreateDatabase() {
    return !"false".equalsIgnoreCase(get("mysql.autoCreateDatabase"));
  }

  /** Startup diagnostic: show which file and source each MySQL setting uses. */
  public static void logMysqlConfigSources(Path configFile) {
    ensureLoaded();
    System.out.println("[Config] MySQL settings resolved from:");
    if (configFile != null) {
      System.out.println("[Config]   properties file: " + configFile.toAbsolutePath());
    }
    logMysqlKey("mysql.host");
    logMysqlKey("mysql.port");
    logMysqlKey("mysql.database");
    logMysqlKey("mysql.user");
    logMysqlKey("mysql.password");
    logMysqlKey("mysql.sslMode");
    warnIfPlaceholderCredentials();
  }

  private static void warnIfPlaceholderCredentials() {
    String user = props.getProperty("mysql.user", "").trim();
    String pass = props.getProperty("mysql.password", "").trim();
    boolean badUser = user.contains("REPLACE_WITH") || user.contains("your-tidb")
        || user.equalsIgnoreCase("root");
    boolean badPass = pass.contains("YOUR_TIDB") || pass.contains("your-tidb")
        || pass.equals("your-mysql-password");
    if (badUser || badPass) {
      System.err.println("[Config] *** TiDB credentials in app.properties are still placeholders or invalid.");
      System.err.println("[Config] *** Edit and SAVE this exact file (Ctrl+S):");
      Path cfg = loadedConfigFile();
      if (cfg != null) {
        System.err.println("[Config] ***   " + cfg.toAbsolutePath());
      }
      System.err.println("[Config] *** Set mysql.user from TiDB Console -> Connect (e.g. 2abc12345.root)");
      System.err.println("[Config] *** Current mysql.user in file: " + user);
    }
  }

  private static void logMysqlKey(String key) {
    String envName = envNameForKey(key);
    String envVal = envName != null ? System.getenv(envName) : null;
    String fileVal = props.getProperty(key, "").trim();
    String resolved = get(key);
    if ("mysql.password".equals(key)) {
      fileVal = fileVal.isBlank() ? "(empty)" : "****";
      resolved = resolved.isBlank() ? "(empty)" : "****";
    }
    String source;
    if (envVal != null && !envVal.isBlank()) {
      source = "environment variable " + envName;
    } else if (!fileVal.isBlank() && !"(empty)".equals(fileVal)) {
      source = "config/app.properties";
    } else {
      source = "default fallback in AppConfig";
    }
    System.out.println("[Config]   " + key + " -> " + resolved + " (" + source + ")");
  }

  private static String envNameForKey(String key) {
    return switch (key) {
      case "mysql.host" -> "SAFEPATH_MYSQL_HOST";
      case "mysql.port" -> "SAFEPATH_MYSQL_PORT";
      case "mysql.database" -> "SAFEPATH_MYSQL_DATABASE";
      case "mysql.user" -> "SAFEPATH_MYSQL_USER";
      case "mysql.password" -> "SAFEPATH_MYSQL_PASSWORD";
      case "mysql.sslMode" -> "SAFEPATH_MYSQL_SSL_MODE";
      default -> null;
    };
  }

  public static Path loadedConfigFile() {
    try {
      return resolveConfigFile();
    } catch (IOException e) {
      return null;
    }
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
      case "app.base.url" -> System.getenv("SAFEPATH_APP_BASE_URL");
      case "mysql.host" -> System.getenv("SAFEPATH_MYSQL_HOST");
      case "mysql.port" -> System.getenv("SAFEPATH_MYSQL_PORT");
      case "mysql.database" -> System.getenv("SAFEPATH_MYSQL_DATABASE");
      case "mysql.user" -> System.getenv("SAFEPATH_MYSQL_USER");
      case "mysql.password" -> System.getenv("SAFEPATH_MYSQL_PASSWORD");
      case "mysql.sslMode" -> System.getenv("SAFEPATH_MYSQL_SSL_MODE");
      default -> null;
    };
  }
}
