package server.models;

import server.core.SafetyEngine;
import server.core.SafetyEngine.TransportMode;
import server.graph.Graph;
import server.graph.Node;

public class GraphBuilder {

  private final SafetyEngine safetyEngine;

  public GraphBuilder(SafetyEngine safetyEngine) {
    this.safetyEngine = safetyEngine;
  }

  /** Build graph from Google Maps route coordinates, loading real POIs first */
  public Graph buildGraphFromRoute(double[][] coordinates, TransportMode mode) {
    Graph graph = new Graph();
    if (coordinates == null || coordinates.length < 2) return graph;

    // ── DSA: compute bounding box of route then load real POIs ──
    double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
    double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
    for (double[] c : coordinates) {
      minLat = Math.min(minLat, c[0]); maxLat = Math.max(maxLat, c[0]);
      minLng = Math.min(minLng, c[1]); maxLng = Math.max(maxLng, c[1]);
    }
    safetyEngine.loadPOIsForBoundingBox(minLat, minLng, maxLat, maxLng);

    Node[] nodes = new Node[coordinates.length];
    for (int i = 0; i < coordinates.length; i++) {
      nodes[i] = new Node(coordinates[i][0], coordinates[i][1]);
      graph.addNode(nodes[i]);
    }

    for (int i = 0; i < nodes.length - 1; i++) {
      double s1 = safetyEngine.calculateNodeSafety(nodes[i],     mode);
      double s2 = safetyEngine.calculateNodeSafety(nodes[i + 1], mode);
      double edgeSafety = (s1 + s2) / 2.0;

      graph.addEdge(nodes[i],     nodes[i + 1], edgeSafety);
      graph.addEdge(nodes[i + 1], nodes[i],     edgeSafety); // bidirectional
    }
    return graph;
  }

  /** Merge multiple route alternatives into one graph (for multi-route Dijkstra) */
  public Graph buildGraphFromSegments(double[][][] segments, TransportMode mode) {
    Graph graph = new Graph();
    if (segments == null) return graph;

    // Compute global bounding box across all segments
    double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
    double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
    for (double[][] seg : segments) {
      if (seg == null) continue;
      for (double[] c : seg) {
        minLat = Math.min(minLat, c[0]); maxLat = Math.max(maxLat, c[0]);
        minLng = Math.min(minLng, c[1]); maxLng = Math.max(maxLng, c[1]);
      }
    }
    if (minLat < Double.MAX_VALUE)
      safetyEngine.loadPOIsForBoundingBox(minLat, minLng, maxLat, maxLng);

    for (double[][] route : segments) {
      if (route == null || route.length < 2) continue;
      Node[] nodes = new Node[route.length];
      for (int i = 0; i < route.length; i++) {
        nodes[i] = new Node(route[i][0], route[i][1]);
        graph.addNode(nodes[i]);
      }
      for (int i = 0; i < nodes.length - 1; i++) {
        double s1 = safetyEngine.calculateNodeSafety(nodes[i],     mode);
        double s2 = safetyEngine.calculateNodeSafety(nodes[i + 1], mode);
        double edgeSafety = (s1 + s2) / 2.0;
        graph.addEdge(nodes[i],     nodes[i + 1], edgeSafety);
        graph.addEdge(nodes[i + 1], nodes[i],     edgeSafety);
      }
    }
    return graph;
  }
}
