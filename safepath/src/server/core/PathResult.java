package server.core;

import server.graph.Node;
import java.util.List;

public class PathResult {

  public final boolean found;
  public final List<Node> path;
  public final double distance;
  public final double safetyScore;
  public final String type;

  public PathResult(boolean found, List<Node> path,
                    double distance, double safetyScore, String type) {
    this.found       = found;
    this.path        = path;
    this.distance    = distance;
    this.safetyScore = safetyScore;
    this.type        = type;
  }
}
