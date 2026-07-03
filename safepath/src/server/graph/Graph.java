package server.graph;

import java.util.*;

public class Graph {

  private final Map<String, Node> nodes;
  private final Map<String, List<Edge>> adjacencyList;

  public Graph() {

    this.nodes = new HashMap<>();
    this.adjacencyList = new HashMap<>();
  }

  // =====================================
  // NODE OPERATIONS
  // =====================================

  public void addNode(Node node) {

    if (!nodes.containsKey(node.getId())) {

      nodes.put(node.getId(), node);

      adjacencyList.put(
          node.getId(),
          new ArrayList<>());
    }
  }

  public boolean containsNode(String id) {
    return nodes.containsKey(id);
  }

  public Node getNode(String id) {
    return nodes.get(id);
  }

  public Collection<Node> getNodes() {
    return nodes.values();
  }

  public int getSize() {
    return nodes.size();
  }

  // =====================================
  // EDGE OPERATIONS
  // =====================================

  public void addEdge(
      Node source,
      Node destination,
      double safetyScore) {

    addNode(source);
    addNode(destination);

    double distance = source.distanceTo(destination);

    Edge edge = new Edge(
        destination,
        distance,
        safetyScore);

    adjacencyList
        .get(source.getId())
        .add(edge);
  }

  public void addEdge(
      Node source,
      Node destination) {

    addEdge(
        source,
        destination,
        50);
  }

  public List<Edge> getEdges(Node node) {

    return adjacencyList.getOrDefault(
        node.getId(),
        Collections.emptyList());
  }

  public int getEdgeCount() {

    int count = 0;

    for (List<Edge> edges : adjacencyList.values()) {

      count += edges.size();
    }

    return count;
  }

  // =====================================
  // DYNAMIC SAFETY UPDATE
  // =====================================

  public void updateEdgeSafety(
      Node source,
      Node destination,
      double newSafetyScore) {

    List<Edge> edges = adjacencyList.get(
        source.getId());

    if (edges == null) {
      return;
    }

    for (Edge edge : edges) {

      if (edge.getDestination()
          .equals(destination)) {

        edge.setSafetyScore(
            newSafetyScore);

        break;
      }
    }
  }

  // =====================================
  // DEBUGGING
  // =====================================

  @Override
  public String toString() {

    StringBuilder sb = new StringBuilder();

    sb.append("Graph Summary\n");
    sb.append("Nodes : ")
        .append(getSize())
        .append("\n");

    sb.append("Edges : ")
        .append(getEdgeCount())
        .append("\n\n");

    for (String nodeId : adjacencyList.keySet()) {

      sb.append(nodeId)
          .append(" -> ")
          .append(adjacencyList.get(nodeId))
          .append("\n");
    }

    return sb.toString();
  }
}