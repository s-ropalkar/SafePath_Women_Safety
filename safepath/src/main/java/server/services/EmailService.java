package server.services;

import server.db.Database;
import server.util.AppConfig;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SMTP email delivery with STARTTLS (port 587) and SMTPS (port 465).
 * Falls back to MySQL email_outbox table when SMTP is not configured or send fails.
 */
public class EmailService {

  public static boolean isSmtpConfigured() {
    String pass = AppConfig.smtpPassword();
    return AppConfig.has("smtp.host")
        && AppConfig.has("smtp.user")
        && AppConfig.has("smtp.password")
        && !pass.equals("your-gmail-app-password")
        && !pass.equals("your-smtp-password");
  }

  private int queueAll(List<String> emails, String subject, String body) {
    return EmailQueueService.get().enqueueBatch(emails, subject, body);
  }

  /** Synchronous delivery for trip/SOS/arrival — returns count actually sent via SMTP. */
  int sendAllSync(List<String> emails, String subject, String body) {
    int sent = 0;
    for (String email : emails) {
      if (email == null || !email.contains("@")) continue;
      if (sendSync(email.trim(), subject, body)) sent++;
    }
    return sent;
  }

  /** Enqueue for async delivery (non-blocking for HTTP threads). */
  public boolean send(String to, String subject, String body) {
    try {
      queueOutboxOnly(to, subject, body);
      EmailQueueService.get().enqueue(to, subject, body);
      return true;
    } catch (Exception e) {
      System.err.println("[Email] Failed to queue: " + e.getMessage());
      return false;
    }
  }

  /** Blocking SMTP send — used by background worker only. */
  public boolean sendSync(String to, String subject, String body) {
    try {
      queueOutboxOnly(to, subject, body);
      if (!isSmtpConfigured()) {
        System.out.println("[Email] Outbox (SMTP not configured) -> " + to);
        return false;
      }
      if (smtpSend(to, subject, body)) {
        System.out.println("[Email] Sent -> " + to + " | " + subject);
        return true;
      }
      System.err.println("[Email] SMTP failed for " + to + " — kept in outbox");
      return false;
    } catch (Exception e) {
      System.err.println("[Email] Failed: " + e.getMessage());
      if (e.getCause() != null) System.err.println("[Email] Cause: " + e.getCause().getMessage());
      return false;
    }
  }

  public void queueOutboxOnly(String to, String subject, String body) throws IOException {
    try {
      Database.get().insertEmailOutbox(to, subject, body);
    } catch (java.sql.SQLException e) {
      throw new IOException("Database error: " + e.getMessage(), e);
    }
  }

  private void queueEmail(String to, String subject, String body) throws IOException {
    queueOutboxOnly(to, subject, body);
  }

  /** Quick EHLO + AUTH check at startup (no message sent). */
  public static boolean verifySmtpLogin() {
    if (!isSmtpConfigured()) return false;
    try {
      verifySmtpLoginInternal(false);
      return true;
    } catch (Exception first) {
      if (!isCertificateError(first)) {
        System.err.println("[Email] SMTP login check failed: " + first.getMessage());
        System.err.println("[Email] Tip: Gmail needs a 16-char App Password (no spaces). See README.");
        return false;
      }
      try {
        System.err.println("[Email] SMTP TLS cert error — retrying with relaxed trust...");
        verifySmtpLoginInternal(true);
        System.out.println("[Email] SMTP login OK with relaxed TLS trust (local Java CA issue)");
        return true;
      } catch (Exception retry) {
        System.err.println("[Email] SMTP login check failed: " + retry.getMessage());
        return false;
      }
    }
  }

  private static void verifySmtpLoginInternal(boolean trustAllCerts) throws IOException {
    String host = AppConfig.smtpHost();
    int port = AppConfig.smtpPort();
    SmtpClient client = new SmtpClient(host, port, AppConfig.smtpUseSsl(), trustAllCerts);
    try {
      client.connect();
      client.ehlo("safepath");
      if (!AppConfig.smtpUseSsl()) {
        client.startTls();
        client.ehlo("safepath");
      }
      client.authLogin(AppConfig.smtpUser(), AppConfig.smtpPassword());
      client.quit();
    } finally {
      client.close();
    }
  }

  private boolean smtpSend(String to, String subject, String body) throws IOException {
    try {
      return smtpSendInternal(to, subject, body, false);
    } catch (IOException first) {
      if (!isCertificateError(first)) throw first;
      System.err.println("[Email] SMTP TLS cert error — retrying with relaxed trust for " + AppConfig.smtpHost());
      return smtpSendInternal(to, subject, body, true);
    }
  }

  private static boolean isCertificateError(Throwable e) {
    while (e != null) {
      String msg = e.getMessage();
      if (msg != null && (msg.contains("PKIX") || msg.contains("certificate")
          || msg.contains("CertPath") || msg.contains("SSLHandshake"))) {
        return true;
      }
      e = e.getCause();
    }
    return false;
  }

  private boolean smtpSendInternal(String to, String subject, String body, boolean trustAllCerts)
      throws IOException {
    String host = AppConfig.smtpHost();
    int port = AppConfig.smtpPort();
    String user = AppConfig.smtpUser();
    String pass = AppConfig.smtpPassword();
    String from = AppConfig.smtpFrom();

    SmtpClient client = new SmtpClient(host, port, AppConfig.smtpUseSsl(), trustAllCerts);
    try {
      client.connect();
      client.ehlo("safepath");
      if (!AppConfig.smtpUseSsl()) {
        client.startTls();
        client.ehlo("safepath");
      }
      client.authLogin(user, pass);
      client.mailFrom(from);
      client.rcptTo(to);
      client.data(buildMessage(from, to, subject, body));
      client.quit();
      return true;
    } finally {
      client.close();
    }
  }

  private static SSLSocketFactory sslFactory(boolean trustAllCerts) throws IOException {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      if (trustAllCerts || AppConfig.smtpTrustAll()) {
        TrustManager[] trust = new TrustManager[] {
          new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
          }
        };
        ctx.init(null, trust, new SecureRandom());
      } else {
        ctx.init(null, null, new SecureRandom());
      }
      return ctx.getSocketFactory();
    } catch (GeneralSecurityException e) {
      throw new IOException("TLS init failed: " + e.getMessage(), e);
    }
  }

  private static void configureSslSocket(SSLSocket ssl, String host) {
    ssl.setEnabledProtocols(new String[] { "TLSv1.2", "TLSv1.3" });
    SSLParameters params = ssl.getSSLParameters();
    params.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(host)));
    params.setEndpointIdentificationAlgorithm("HTTPS");
    ssl.setSSLParameters(params);
  }

  private String buildMessage(String from, String to, String subject, String body) {
    return "From: SafePath AI <" + from + ">\r\n"
        + "To: " + to + "\r\n"
        + "Subject: " + subject + "\r\n"
        + "MIME-Version: 1.0\r\n"
        + "Content-Type: text/plain; charset=UTF-8\r\n"
        + "\r\n"
        + body.replace("\n", "\r\n");
  }

  public int sendTripStarted(String userName, String source, String destination,
      String trackingLink, List<String> emails) {
    String subject = "SafePath AI Alert - Trip Started";
    String body = "SafePath AI Alert\n\n"
        + "User: " + userName + "\nTrip Started\n\n"
        + "Source: " + source + "\nDestination: " + destination + "\n\n"
        + "Live Tracking Link:\n" + trackingLink;
    return sendAllSync(emails, subject, body);
  }

  public int sendModerateAlert(String userName, double score, double lat, double lng,
      String link, List<String> emails) {
    String subject = "SafePath AI - Moderate Safety (" + (int) score + "/100)";
    String body = "SafePath AI Notice\n\nUser: " + userName + "\nSafety Status: MODERATE\n"
        + "Safety Score: " + (int) score + "/100\n\nCurrent Location: " + lat + ", " + lng + "\n"
        + "Live Tracking: " + link + "\n\nPlease stay aware of the journey.";
    return queueAll(emails, subject, body);
  }

  public int sendHighRiskAlert(String userName, double score, double lat, double lng,
      String link, List<String> emails) {
    String subject = "SafePath AI - High Risk Alert";
    String body = "High Risk Alert\n\nUser: " + userName + "\n\nCurrent Location:\n"
        + lat + ", " + lng + "\nhttps://www.google.com/maps?q=" + lat + "," + lng + "\n\n"
        + "Safety Score: " + (int) score + "/100\n\n"
        + "Please monitor the user's journey immediately.\nLive Tracking Link:\n" + link;
    return queueAll(emails, subject, body);
  }

  public int sendSafeStatus(String userName, double score, String link, List<String> emails) {
    String subject = "SafePath AI - User is Safe (" + (int) score + "/100)";
    String body = "SafePath AI Update\n\nUser: " + userName + "\nSafety Status: SAFE\n"
        + "Safety Score: " + (int) score + "/100\n\nLive Tracking: " + link;
    return queueAll(emails, subject, body);
  }

  public int sendSafeArrival(String userName, double lat, double lng, double score,
      int rating, String link, List<String> emails) {
    String subject = "SafePath AI - " + userName + " Reached Safely";
    String ratingLine = (rating >= 1 && rating <= 5)
        ? "Trip rating: " + rating + "/5 stars\n"
        : "";
    String body = "SafePath AI Update\n\n"
        + userName + " has confirmed safe arrival.\n\n"
        + ratingLine
        + "Safety Score: " + (int) score + "/100\n"
        + "Last known location: " + lat + ", " + lng + "\n"
        + "https://www.google.com/maps?q=" + lat + "," + lng + "\n\n"
        + "Live tracking link:\n" + link;
    return sendAllSync(emails, subject, body);
  }

  public int sendEmergencySos(String userName, String details, double lat, double lng,
      double score, String link, List<String> emails) {
    String subject = "SafePath AI - Emergency SOS from " + userName;
    String body = "EMERGENCY SOS ALERT\n\n"
        + "User: " + userName + "\n\n"
        + details + "\n\n"
        + "Current Location:\n" + lat + ", " + lng + "\n"
        + "https://www.google.com/maps?q=" + lat + "," + lng + "\n\n"
        + "Safety Score: " + (int) score + "/100\n\n"
        + "Please contact the user immediately.\nLive tracking:\n" + link;
    return sendAllSync(emails, subject, body);
  }

  /** Minimal blocking SMTP client with STARTTLS support. */
  private static final class SmtpClient {
    private final String host;
    private final int port;
    private final boolean implicitSsl;
    private final boolean trustAllCerts;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    SmtpClient(String host, int port, boolean implicitSsl, boolean trustAllCerts) {
      this.host = host;
      this.port = port;
      this.implicitSsl = implicitSsl;
      this.trustAllCerts = trustAllCerts;
    }

    void connect() throws IOException {
      if (implicitSsl) {
        SSLSocket ssl = (SSLSocket) sslFactory(trustAllCerts).createSocket(host, port);
        configureSslSocket(ssl, host);
        if (trustAllCerts) {
          SSLParameters params = ssl.getSSLParameters();
          params.setEndpointIdentificationAlgorithm(null);
          ssl.setSSLParameters(params);
        }
        ssl.startHandshake();
        socket = ssl;
      } else {
        socket = new Socket(host, port);
      }
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      expect(220);
    }

    void ehlo(String clientHost) throws IOException {
      cmd("EHLO " + clientHost);
      readMultiline(250);
    }

    void startTls() throws IOException {
      cmd("STARTTLS");
      expect(220);
      SSLSocket ssl = (SSLSocket) sslFactory(trustAllCerts).createSocket(socket, host, port, true);
      configureSslSocket(ssl, host);
      if (trustAllCerts) {
        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm(null);
        ssl.setSSLParameters(params);
      }
      ssl.startHandshake();
      socket = ssl;
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    void authLogin(String user, String pass) throws IOException {
      cmd("AUTH LOGIN");
      expect(334);
      cmd(Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
      expect(334);
      cmd(Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
      expect(235);
    }

    void mailFrom(String from) throws IOException {
      cmd("MAIL FROM:<" + from + ">");
      expect(250);
    }

    void rcptTo(String to) throws IOException {
      cmd("RCPT TO:<" + to + ">");
      expect(250);
    }

    void data(String message) throws IOException {
      cmd("DATA");
      expect(354);
      writeRaw(message + "\r\n.\r\n");
      expect(250);
    }

    void quit() throws IOException {
      cmd("QUIT");
      try { expect(221); } catch (IOException ignored) { /* server may close */ }
    }

    void close() {
      try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void cmd(String line) throws IOException {
      writeRaw(line + "\r\n");
    }

    private void writeRaw(String s) throws IOException {
      out.write(s);
      out.flush();
    }

    private void expect(int code) throws IOException {
      String line = in.readLine();
      if (line == null || !line.startsWith(String.valueOf(code))) {
        throw new IOException("SMTP expected " + code + " but got: " + line);
      }
    }

    private void readMultiline(int code) throws IOException {
      String prefix = String.valueOf(code);
      String line = in.readLine();
      if (line == null || !(line.startsWith(prefix) || line.startsWith(prefix + "-"))) {
        throw new IOException("SMTP expected " + code + " multiline but got: " + line);
      }
      while (line != null && line.startsWith(prefix + "-")) {
        line = in.readLine();
      }
    }
  }
}
