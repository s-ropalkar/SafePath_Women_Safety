package server.graph;

/** Shared graph helpers used by routing engines. */
public final class GraphUtils {

  private GraphUtils() {}

  public static Node findNearestNode(Graph graph, Node target) {
    if (graph == null || target == null) return null;
    Node nearest = null;
    double minDist = Double.MAX_VALUE;
    for (Node node : graph.getNodes()) {
      double dist = target.distanceTo(node);
      if (dist < minDist) {
        minDist = dist;
        nearest = node;
      }
    }
    return nearest;
  }

  public static Edge findEdge(Graph graph, Node from, Node to) {
    if (graph == null || from == null || to == null) return null;
    for (Edge edge : graph.getEdges(from)) {
      if (edge.getDestination().equals(to)) return edge;
    }
    return null;
  }
}
