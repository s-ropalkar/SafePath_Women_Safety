package server.graph;

public class Edge {

  private final Node destination;

  private final double distance;

  private double safetyScore;

  public Edge(
      Node destination,
      double distance,
      double safetyScore) {

    this.destination = destination;
    this.distance = distance;

    setSafetyScore(safetyScore);
  }

  public Edge(
      Node destination,
      double distance) {

    this(destination, distance, 50);
  }

  public Node getDestination() {
    return destination;
  }

  public double getDistance() {
    return distance;
  }

  public double getSafetyScore() {
    return safetyScore;
  }

  public void setSafetyScore(
      double safetyScore) {

    this.safetyScore = Math.max(
        0,
        Math.min(
            100,
            safetyScore));
  }

  /**
   * Balanced route cost.
   *
   * Lower cost = better route.
   *
   * Distance contributes positively.
   * Unsafe roads add penalty.
   */
  public double getBalancedCost(
      double alpha,
      double beta) {

    return (alpha * distance)
        +
        (beta * (100 - safetyScore));
  }

  @Override
  public String toString() {

    return "Edge{" +
        "destination=" +
        destination.getId() +
        ", distance=" +
        String.format("%.2f", distance) +
        ", safety=" +
        String.format("%.1f", safetyScore) +
        '}';
  }
}