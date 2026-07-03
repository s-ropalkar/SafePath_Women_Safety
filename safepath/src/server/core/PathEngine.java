package server.core;

import server.graph.*;

import java.util.*;

/** Dijkstra shortest and safest path search. */
public class PathEngine {

  private final Graph graph;

  public PathEngine(Graph graph) {
    this.graph = graph;
  }

  public PathResult shortestPath(Node start, Node end) {
    Node actualStart = GraphUtils.findNearestNode(graph, start);
    Node actualEnd = GraphUtils.findNearestNode(graph, end);
    if (actualStart == null || actualEnd == null) {
      return new PathResult(false, new ArrayList<>(), 0, 0, "SHORTEST");
    }

    Map<String, Double> distance = new HashMap<>();
    Map<String, Node> previous = new HashMap<>();
    PriorityQueue<NodeDistance> pq = new PriorityQueue<>();

    for (Node node : graph.getNodes()) {
      distance.put(node.getId(), Double.MAX_VALUE);
    }
    distance.put(actualStart.getId(), 0.0);
    pq.offer(new NodeDistance(actualStart, 0));

    Node matchedEnd = null;
    while (!pq.isEmpty()) {
      NodeDistance current = pq.poll();
      if (current.node.equals(actualEnd)) {
        matchedEnd = current.node;
        break;
      }

      for (Edge edge : graph.getEdges(current.node)) {
        Node next = edge.getDestination();
        double newDistance = distance.get(current.node.getId()) + edge.getDistance();
        if (newDistance < distance.get(next.getId())) {
          distance.put(next.getId(), newDistance);
          previous.put(next.getId(), current.node);
          pq.offer(new NodeDistance(next, newDistance));
        }
      }
    }

    return buildResult(actualStart, matchedEnd != null ? matchedEnd : actualEnd, previous, "SHORTEST");
  }

  public PathResult safestPath(Node start, Node end) {
    Node actualStart = GraphUtils.findNearestNode(graph, start);
    Node actualEnd = GraphUtils.findNearestNode(graph, end);
    if (actualStart == null || actualEnd == null) {
      return new PathResult(false, new ArrayList<>(), 0, 0, "SAFEST");
    }

    Map<String, Double> safety = new HashMap<>();
    Map<String, Node> previous = new HashMap<>();
    PriorityQueue<NodeSafety> pq = new PriorityQueue<>();

    for (Node node : graph.getNodes()) {
      safety.put(node.getId(), Double.NEGATIVE_INFINITY);
    }
    safety.put(actualStart.getId(), 100.0);
    pq.offer(new NodeSafety(actualStart, 100));

    Node matchedEnd = null;
    while (!pq.isEmpty()) {
      NodeSafety current = pq.poll();
      if (current.node.equals(actualEnd)) {
        matchedEnd = current.node;
        break;
      }

      for (Edge edge : graph.getEdges(current.node)) {
        Node next = edge.getDestination();
        double newSafety = Math.min(safety.get(current.node.getId()), edge.getSafetyScore());
        if (newSafety > safety.get(next.getId())) {
          safety.put(next.getId(), newSafety);
          previous.put(next.getId(), current.node);
          pq.offer(new NodeSafety(next, newSafety));
        }
      }
    }

    return buildResult(actualStart, matchedEnd != null ? matchedEnd : actualEnd, previous, "SAFEST");
  }

  private PathResult buildResult(Node start, Node end, Map<String, Node> previous, String type) {
    List<Node> path = new ArrayList<>();
    Node current = end;
    while (current != null) {
      path.add(0, current);
      current = previous.get(current.getId());
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

  private static class NodeDistance implements Comparable<NodeDistance> {
    Node node;
    double value;

    NodeDistance(Node node, double value) {
      this.node = node;
      this.value = value;
    }

    public int compareTo(NodeDistance o) {
      return Double.compare(value, o.value);
    }
  }

  private static class NodeSafety implements Comparable<NodeSafety> {
    Node node;
    double value;

    NodeSafety(Node node, double value) {
      this.node = node;
      this.value = value;
    }

    public int compareTo(NodeSafety o) {
      return Double.compare(o.value, value);
    }
  }
}
