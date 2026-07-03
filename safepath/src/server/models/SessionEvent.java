package server.models;

public class SessionEvent {
  public String type;
  public String message;
  public long timestamp;
  public double lat;
  public double lng;
  public double safetyScore;

  public SessionEvent(String type, String message, double lat, double lng, double safetyScore) {
    this.type = type;
    this.message = message;
    this.timestamp = System.currentTimeMillis();
    this.lat = lat;
    this.lng = lng;
    this.safetyScore = safetyScore;
  }
}
