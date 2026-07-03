package server.core;

import server.graph.Edge;
import server.graph.Graph;
import server.graph.GraphUtils;
import server.graph.Node;

import java.util.List;

/** Shared path distance and safety calculations for routing engines. */
public final class PathMetrics {

  private PathMetrics() {}

  public static double calculateDistance(List<Node> path) {
    if (path == null || path.size() < 2) return 0;
    double total = 0;
    for (int i = 0; i < path.size() - 1; i++) {
      total += path.get(i).distanceTo(path.get(i + 1));
    }
    return total;
  }

  public static double calculateAverageSafety(Graph graph, List<Node> path) {
    if (graph == null || path == null || path.size() < 2) return 50;
    double sum = 0;
    int count = 0;
    for (int i = 0; i < path.size() - 1; i++) {
      Edge edge = GraphUtils.findEdge(graph, path.get(i), path.get(i + 1));
      if (edge != null) {
        sum += edge.getSafetyScore();
        count++;
      }
    }
    return count == 0 ? 50 : sum / count;
  }

  public static double calculateMinSafety(Graph graph, List<Node> path) {
    if (graph == null || path == null || path.size() < 2) return 50;
    double min = 100;
    int count = 0;
    for (int i = 0; i < path.size() - 1; i++) {
      Edge edge = GraphUtils.findEdge(graph, path.get(i), path.get(i + 1));
      if (edge != null) {
        min = Math.min(min, edge.getSafetyScore());
        count++;
      }
    }
    return count == 0 ? 50 : min;
  }
}
