package server.store;

import java.util.*;
import server.models.UserSession;

/**
 * In-memory trip sessions (GPS, route, alerts). Guardian data is in MySQL only.
 */
public class SessionStore {
  private Map<String, UserSession> sessions;

  public SessionStore() {
    this.sessions = new HashMap<>();
  }

  public void createSession(String sessionId) {
    UserSession session = new UserSession(sessionId);
    session.viewKey = java.util.UUID.randomUUID().toString();
    sessions.put(sessionId, session);
  }

  public UserSession getSession(String sessionId) {
    return sessions.get(sessionId);
  }

  public void updateSession(String sessionId, double lat, double lng) {
    UserSession session = sessions.get(sessionId);
    if (session != null) {
      session.updateLocation(lat, lng);
    }
  }

  public void updateSafety(String sessionId, double score, String status) {
    UserSession session = sessions.get(sessionId);
    if (session != null) {
      session.updateSafety(score, status);
    }
  }

  public void setTravelerPhone(String sessionId, String phone) {
    UserSession session = sessions.get(sessionId);
    if (session != null) {
      session.travelerPhone = phone;
    }
  }

  public void addEvent(String sessionId, server.models.SessionEvent event) {
    UserSession session = sessions.get(sessionId);
    if (session != null) {
      session.addEvent(event);
    }
  }
  
  public void removeSession(String sessionId) {
    sessions.remove(sessionId);
  }
}
