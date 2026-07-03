package server.services;

import server.models.UserSession;

import java.io.IOException;
import java.util.*;

/**
 * Email alerts for guardians. Recipient lists are loaded from MySQL (guardians table).
 */
public class AlertService {
  private static final double HIGH_RISK_THRESHOLD = 40;
  private static final long EMAIL_COOLDOWN_MS = 5 * 60 * 1000;

  private final EmailService emailService = new EmailService();
  private final GuardianService guardianService = new GuardianService();

  public void onSafetyUpdate(UserSession session, double newScore, String status) {
    if (session == null || session.userId == null) return;

    try {
      List<String> emails = guardianEmails(session.userId);
      if (emails.isEmpty()) return;

      String link = session.trackingLink != null ? session.trackingLink : "";
      String name = session.userName != null ? session.userName : "User";
      double lat = session.currentLat;
      double lng = session.currentLng;

      if (newScore < HIGH_RISK_THRESHOLD) {
        if (System.currentTimeMillis() - session.lastRiskEmailAt > EMAIL_COOLDOWN_MS) {
          emailService.sendHighRiskAlert(name, newScore, lat, lng, link, emails);
          session.lastRiskEmailAt = System.currentTimeMillis();
        }
      } else if (newScore < 50) {
        if (session.previousSafetyScore >= 50
            && System.currentTimeMillis() - session.lastModerateEmailAt > EMAIL_COOLDOWN_MS) {
          emailService.sendModerateAlert(name, newScore, lat, lng, link, emails);
          session.lastModerateEmailAt = System.currentTimeMillis();
        }
      } else if (newScore >= 70 && session.previousSafetyScore < 70) {
        emailService.sendSafeStatus(name, newScore, link, emails);
      }

      session.previousSafetyScore = newScore;
    } catch (Exception e) {
      System.err.println("[AlertService] " + e.getMessage());
    }
  }

  public int onTripStarted(UserSession session, String source, String destination, String trackingLink) {
    if (session == null || session.userId == null) return 0;
    try {
      List<String> emails = guardianEmails(session.userId);
      if (emails.isEmpty()) return 0;
      String name = session.userName != null ? session.userName : "User";
      return emailService.sendTripStarted(name, source, destination, trackingLink, emails);
    } catch (Exception e) {
      System.err.println("[AlertService] Trip email failed: " + e.getMessage());
      return 0;
    }
  }

  public int guardianEmailCount(UserSession session) {
    if (session == null || session.userId == null) return 0;
    try {
      return guardianEmails(session.userId).size();
    } catch (IOException e) {
      return 0;
    }
  }

  public int onSafeArrival(UserSession session, double lat, double lng, double score, int rating) {
    if (session == null || session.userId == null) return 0;
    try {
      List<String> emails = guardianEmails(session.userId);
      if (emails.isEmpty()) return 0;
      String name = session.userName != null ? session.userName : "User";
      String link = session.trackingLink != null ? session.trackingLink : "";
      return emailService.sendSafeArrival(name, lat, lng, score, rating, link, emails);
    } catch (Exception e) {
      System.err.println("[AlertService] Safe arrival email failed: " + e.getMessage());
      return 0;
    }
  }

  public int onEmergencySos(UserSession session, String message, double lat, double lng, double score) {
    if (session == null || session.userId == null) return 0;
    try {
      List<String> emails = guardianEmails(session.userId);
      if (emails.isEmpty()) return 0;
      String name = session.userName != null ? session.userName : "User";
      String link = session.trackingLink != null ? session.trackingLink : "";
      return emailService.sendEmergencySos(name, message, lat, lng, score, link, emails);
    } catch (Exception e) {
      System.err.println("[AlertService] SOS email failed: " + e.getMessage());
      return 0;
    }
  }

  private List<String> guardianEmails(String userId) throws IOException {
    // Single source of truth: MySQL guardians.email
    List<String> emails = new ArrayList<>();
    for (Map<String, Object> g : guardianService.listForUserId(userId)) {
      String email = String.valueOf(g.get("email"));
      if (email != null && email.contains("@")) emails.add(email);
    }
    return emails;
  }
}
