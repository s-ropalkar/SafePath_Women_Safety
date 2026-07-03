package server.models;

import java.util.*;

/**
 * In-memory state for one active trip (location, route, safety, events).
 * Guardian contacts are NOT stored here — they live in MySQL and are loaded when needed.
 */
public class UserSession {
  public String sessionId;
  /** Secret key required for guardian live view (not guardian PII). */
  public String viewKey;
  public double currentLat;
  public double currentLng;
  public String travelerPhone;
  public long startTime;
  public long lastUpdated;
  public double safetyScore;
  public String safetyStatus;
  public double[] routePath;
  public String userId;
  public String userName;
  public String sourceLabel;
  public String destLabel;
  public double destLat;
  public double destLng;
  public String trackingLink;
  public String routeType = "balanced";
  public double tripDistanceKm;
  public long lastRiskEmailAt;
  public long lastModerateEmailAt;
  public double previousSafetyScore = 50;
  public final LinkedList<SessionEvent> events = new LinkedList<>();
  private static final int MAX_EVENTS = 50;

  public UserSession(String sessionId) {
    this.sessionId = sessionId;
    this.startTime = System.currentTimeMillis();
    this.lastUpdated = this.startTime;
    this.safetyScore = 50.0;
    this.safetyStatus = "MODERATE";
  }

  public void updateLocation(double lat, double lng) {
    this.currentLat = lat;
    this.currentLng = lng;
    this.lastUpdated = System.currentTimeMillis();
  }

  public void updateSafety(double score, String status) {
    this.safetyScore = score;
    this.safetyStatus = status;
    this.lastUpdated = System.currentTimeMillis();
  }

  public void addEvent(SessionEvent event) {
    events.addFirst(event);
    while (events.size() > MAX_EVENTS) {
      events.removeLast();
    }
  }
}
