package server.models;

/** Persistent community unsafe zone loaded from MySQL. */
public class UnsafeLocation {
  public int id;
  public double latitude;
  public double longitude;
  public int reportCount;
  public String reason;
  public long createdAt;

  public UnsafeLocation(int id, double latitude, double longitude,
      int reportCount, String reason, long createdAt) {
    this.id = id;
    this.latitude = latitude;
    this.longitude = longitude;
    this.reportCount = reportCount;
    this.reason = reason;
    this.createdAt = createdAt;
  }
}
