package server.core;

import server.graph.*;

import java.util.*;

/** Yen's algorithm — finds k shortest distinct paths in a graph. */
public class YenPathFinder {

  private final Graph graph;
  private final PathEngine pathEngine;

  public YenPathFinder(Graph graph) {
    this.graph = graph;
    this.pathEngine = new PathEngine(graph);
  }

  public List<PathResult> findKShortestPaths(Node start, Node end, int k) {
    List<PathResult> results = new ArrayList<>();
    PathResult first = pathEngine.shortestPath(start, end);
    if (!first.found) return results;

    results.add(first);
    PriorityQueue<Candidate> candidates = new PriorityQueue<>();

    for (int i = 0; i < results.size() && results.size() < k; i++) {
      PathResult base = results.get(i);
      List<Node> basePath = base.path;

      for (int j = 0; j < basePath.size() - 1; j++) {
        Node spurNode = basePath.get(j);
        List<Node> rootPath = basePath.subList(0, j + 1);

        Set<String> removedEdges = new HashSet<>();
        for (PathResult existing : results) {
          List<Node> p = existing.path;
          if (p.size() > j && pathPrefixEquals(p, rootPath, j + 1)) {
            if (j + 1 < p.size()) {
              removedEdges.add(edgeKey(p.get(j), p.get(j + 1)));
            }
          }
        }

        Graph spurGraph = copyGraphWithoutEdges(removedEdges);
        PathResult spur = new PathEngine(spurGraph).shortestPath(spurNode, end);
        if (!spur.found || spur.path.isEmpty()) continue;

        List<Node> totalPath = new ArrayList<>(rootPath);
        if (totalPath.size() > 1) totalPath.remove(totalPath.size() - 1);
        for (int s = 1; s < spur.path.size(); s++) {
          totalPath.add(spur.path.get(s));
        }

        Candidate c = new Candidate(
            new PathResult(
                true,
                totalPath,
                PathMetrics.calculateDistance(totalPath),
                PathMetrics.calculateAverageSafety(graph, totalPath),
                "SHORTEST"),
            PathMetrics.calculateDistance(totalPath));
        if (!containsPath(results, totalPath)) {
          candidates.offer(c);
        }
      }

      while (!candidates.isEmpty() && results.size() < k) {
        Candidate best = candidates.poll();
        if (!containsPath(results, best.result.path)) {
          results.add(best.result);
          break;
        }
      }
    }
    return results;
  }

  private boolean pathPrefixEquals(List<Node> path, List<Node> prefix, int len) {
    if (path.size() < len || prefix.size() < len) return false;
    for (int i = 0; i < len; i++) {
      if (!path.get(i).equals(prefix.get(i))) return false;
    }
    return true;
  }

  private boolean containsPath(List<PathResult> results, List<Node> path) {
    for (PathResult r : results) {
      if (pathsEqual(r.path, path)) return true;
    }
    return false;
  }

  public static boolean pathsEqual(List<Node> a, List<Node> b) {
    if (a == null || b == null || a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).equals(b.get(i))) return false;
    }
    return true;
  }

  private String edgeKey(Node a, Node b) {
    return a.getId() + "->" + b.getId();
  }

  private Graph copyGraphWithoutEdges(Set<String> removed) {
    Graph copy = new Graph();
    for (Node n : graph.getNodes()) {
      copy.addNode(n);
    }
    for (Node n : graph.getNodes()) {
      for (Edge e : graph.getEdges(n)) {
        String key = edgeKey(n, e.getDestination());
        if (!removed.contains(key)) {
          copy.addEdge(n, e.getDestination(), e.getSafetyScore());
        }
      }
    }
    return copy;
  }

  private static class Candidate implements Comparable<Candidate> {
    final PathResult result;
    final double distance;

    Candidate(PathResult result, double distance) {
      this.result = result;
      this.distance = distance;
    }

    @Override
    public int compareTo(Candidate o) {
      return Double.compare(this.distance, o.distance);
    }
  }
}
