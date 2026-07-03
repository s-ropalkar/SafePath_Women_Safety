package server.graph;

public class Node {

  private final double latitude;
  private final double longitude;
  private final String id;
  private String name;

  public Node(double latitude, double longitude) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.id = String.format("%.6f,%.6f", latitude, longitude);
  }

  public Node(double latitude, double longitude, String name) {
    this(latitude, longitude);
    this.name = name;
  }

  public double getLatitude()  { return latitude; }
  public double getLongitude() { return longitude; }
  public String getId()        { return id; }
  public String getName()      { return name; }
  public void   setName(String name) { this.name = name; }

  /** Haversine distance in km */
  public double distanceTo(Node other) {
    final int R = 6371;
    double lat1 = Math.toRadians(latitude);
    double lat2 = Math.toRadians(other.latitude);
    double dLat = Math.toRadians(other.latitude - latitude);
    double dLng = Math.toRadians(other.longitude - longitude);
    double a = Math.sin(dLat/2)*Math.sin(dLat/2)
             + Math.cos(lat1)*Math.cos(lat2)*Math.sin(dLng/2)*Math.sin(dLng/2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Node)) return false;
    Node o = (Node) obj;
    return Math.abs(latitude - o.latitude) < 0.0001
        && Math.abs(longitude - o.longitude) < 0.0001;
  }

  @Override public int hashCode() { return id.hashCode(); }

  @Override public String toString() {
    return "Node{lat=" + latitude + ", lng=" + longitude
         + (name != null ? ", name='" + name + "'" : "") + "}";
  }
}
