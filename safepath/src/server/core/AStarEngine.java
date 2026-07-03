package server.core;

import server.graph.*;

import java.util.*;

/** A* search for balanced route: f(n) = g(n) + h(n). */
public class AStarEngine {

  private static final double ALPHA = 0.6;
  private static final double BETA = 0.4;

  private final Graph graph;

  public AStarEngine(Graph graph) {
    this.graph = graph;
  }

  public PathResult balancedPath(Node start, Node end) {
    Node actualStart = GraphUtils.findNearestNode(graph, start);
    Node actualEnd = GraphUtils.findNearestNode(graph, end);
    if (actualStart == null || actualEnd == null) {
      return new PathResult(false, new ArrayList<>(), 0, 0, "BALANCED");
    }

    Map<String, Double> gScore = new HashMap<>();
    Map<String, Node> cameFrom = new HashMap<>();
    PriorityQueue<AStarNode> open = new PriorityQueue<>();

    for (Node n : graph.getNodes()) {
      gScore.put(n.getId(), Double.MAX_VALUE);
    }
    gScore.put(actualStart.getId(), 0.0);
    open.offer(new AStarNode(actualStart, heuristic(actualStart, actualEnd)));

    Node matchedEnd = null;
    while (!open.isEmpty()) {
      AStarNode current = open.poll();
      if (current.node.equals(actualEnd)) {
        matchedEnd = current.node;
        break;
      }

      for (Edge edge : graph.getEdges(current.node)) {
        Node next = edge.getDestination();
        double edgeCost = ALPHA * edge.getDistance()
            + BETA * (100.0 - edge.getSafetyScore());
        double tentative = gScore.get(current.node.getId()) + edgeCost;

        if (tentative < gScore.get(next.getId())) {
          cameFrom.put(next.getId(), current.node);
          gScore.put(next.getId(), tentative);
          double f = tentative + heuristic(next, actualEnd);
          open.offer(new AStarNode(next, f));
        }
      }
    }

    return buildResult(actualStart, matchedEnd != null ? matchedEnd : actualEnd, cameFrom, "BALANCED");
  }

  private double heuristic(Node from, Node to) {
    return from.distanceTo(to);
  }

  private PathResult buildResult(Node start, Node end, Map<String, Node> cameFrom, String type) {
    List<Node> path = new ArrayList<>();
    Node current = end;
    while (current != null) {
      path.add(0, current);
      current = cameFrom.get(current.getId());
    }
    if (path.isEmpty() || !path.get(0).equals(start)) {
      return new PathResult(false, path, 0, 0, type);
    }
    return new PathResult(
        true,
        path,
        PathMetrics.calculateDistance(path),
        PathMetrics.calculateAverageSafety(graph, path),
        type);
  }

  private static class AStarNode implements Comparable<AStarNode> {
    final Node node;
    final double f;

    AStarNode(Node node, double f) {
      this.node = node;
      this.f = f;
    }

    @Override
    public int compareTo(AStarNode o) {
      return Double.compare(this.f, o.f);
    }
  }
}
