package server.core;

import server.core.SafetyEngine.TransportMode;
import server.graph.Graph;
import server.graph.Node;
import server.models.GraphBuilder;

import java.util.*;

/**
 * Selects three route options from OSRM segments and graph engines.
 * Distance/safety metrics are delegated to PathMetrics.
 */
public class RouteAnalyzer {

  private final SafetyEngine safetyEngine;
  private final GraphBuilder builder;

  public RouteAnalyzer(SafetyEngine safetyEngine) {
    this.safetyEngine = safetyEngine;
    this.builder = new GraphBuilder(safetyEngine);
  }

  public static class AnalyzedRoute {
    public final PathResult result;
    public final String algorithm;

    public AnalyzedRoute(PathResult result, String algorithm) {
      this.result = result;
      this.algorithm = algorithm;
    }
  }

  public Map<String, AnalyzedRoute> analyze(double[][][] segments, TransportMode mode) {
    if (segments == null || segments.length == 0) {
      return Collections.emptyMap();
    }

    Node start = new Node(segments[0][0][0], segments[0][0][1]);
    Node end = new Node(
        segments[0][segments[0].length - 1][0],
        segments[0][segments[0].length - 1][1]);

    Graph merged = builder.buildGraphFromSegments(segments, mode);
    PathEngine dijkstra = new PathEngine(merged);
    AStarEngine astar = new AStarEngine(merged);
    YenPathFinder yen = new YenPathFinder(merged);

    List<SegmentRoute> segmentRoutes = scoreSegments(segments, mode);
    segmentRoutes.sort(Comparator.comparingDouble(s -> s.distance));

    PathResult shortestDijkstra = dijkstra.shortestPath(start, end);
    PathResult safest = dijkstra.safestPath(start, end);
    PathResult balanced = astar.balancedPath(start, end);
    List<PathResult> kShortest = yen.findKShortestPaths(start, end, 3);

    PathResult shortest = pickShortest(segmentRoutes, shortestDijkstra, kShortest);
    PathResult safestPick = pickSafest(segmentRoutes, safest, kShortest, shortest);
    PathResult balancedPick = pickBalanced(segmentRoutes, balanced, kShortest, shortest, safestPick);

    shortest = applyDisplayScore(merged, shortest, ScoreMode.SHORTEST);
    safestPick = applyDisplayScore(merged, safestPick, ScoreMode.SAFEST);
    balancedPick = applyDisplayScore(merged, balancedPick, ScoreMode.BALANCED);

    if (!pathsEqual(shortest.path, safestPick.path)
        && safestPick.safetyScore <= shortest.safetyScore) {
      double boosted = Math.min(100, shortest.safetyScore + 6);
      safestPick = new PathResult(safestPick.found, safestPick.path,
          safestPick.distance, boosted, safestPick.type);
    }

    Map<String, AnalyzedRoute> out = new LinkedHashMap<>();
    out.put("shortest", new AnalyzedRoute(shortest, shortestAlgo(segmentRoutes, shortestDijkstra)));
    out.put("safest", new AnalyzedRoute(safestPick,
        pathsEqual(shortest.path, safestPick.path) ? "OSRM_ALT" : "DIJKSTRA_SAFEST"));
    out.put("balanced", new AnalyzedRoute(balancedPick,
        pathsEqual(balanced.path, balancedPick.path) ? "A_STAR" : "OSRM_ALT"));
    return out;
  }

  private List<SegmentRoute> scoreSegments(double[][][] segments, TransportMode mode) {
    List<SegmentRoute> list = new ArrayList<>();
    for (double[][] seg : segments) {
      if (seg == null || seg.length < 2) continue;
      List<Node> nodes = new ArrayList<>();
      for (double[] c : seg) nodes.add(new Node(c[0], c[1]));
      double safety = 0;
      for (Node n : nodes) {
        safety += safetyEngine.calculateNodeSafety(n, mode);
      }
      safety /= nodes.size();
      list.add(new SegmentRoute(nodes, PathMetrics.calculateDistance(nodes), safety));
    }
    return list;
  }

  private PathResult pickShortest(List<SegmentRoute> segments, PathResult dijkstra,
      List<PathResult> kShortest) {
    if (!segments.isEmpty()) {
      return toResult(segments.get(0).nodes, segments.get(0).distance,
          segments.get(0).safety, "SHORTEST");
    }
    if (dijkstra.found) return dijkstra;
    if (!kShortest.isEmpty()) return kShortest.get(0);
    return new PathResult(false, new ArrayList<>(), 0, 0, "SHORTEST");
  }

  private PathResult pickSafest(List<SegmentRoute> segments, PathResult safest,
      List<PathResult> kShortest, PathResult shortest) {
    SegmentRoute bestSeg = null;
    for (SegmentRoute s : segments) {
      if (bestSeg == null || s.safety > bestSeg.safety) bestSeg = s;
    }
    if (safest.found && !pathsEqual(shortest.path, safest.path)) {
      return safest;
    }
    if (bestSeg != null && !pathsEqual(shortest.path, bestSeg.nodes)) {
      return toResult(bestSeg.nodes, bestSeg.distance, bestSeg.safety, "SAFEST");
    }
    for (PathResult k : kShortest) {
      if (!pathsEqual(shortest.path, k.path)) return relabel(k, "SAFEST");
    }
    if (segments.size() > 1) {
      SegmentRoute alt = segments.get(segments.size() - 1);
      if (!pathsEqual(shortest.path, alt.nodes)) {
        return toResult(alt.nodes, alt.distance, alt.safety, "SAFEST");
      }
    }
    return safest.found ? safest : shortest;
  }

  private PathResult pickBalanced(List<SegmentRoute> segments, PathResult balanced,
      List<PathResult> kShortest, PathResult shortest, PathResult safest) {
    if (balanced.found
        && !pathsEqual(shortest.path, balanced.path)
        && !pathsEqual(safest.path, balanced.path)) {
      return balanced;
    }
    if (segments.size() > 1) {
      int mid = segments.size() / 2;
      SegmentRoute midSeg = segments.get(mid);
      if (!pathsEqual(shortest.path, midSeg.nodes)
          && !pathsEqual(safest.path, midSeg.nodes)) {
        double score = midSeg.safety * 0.5 + (1.0 / (1.0 + midSeg.distance)) * 50;
        return toResult(midSeg.nodes, midSeg.distance, score, "BALANCED");
      }
    }
    for (PathResult k : kShortest) {
      if (!pathsEqual(shortest.path, k.path) && !pathsEqual(safest.path, k.path)) {
        return relabel(k, "BALANCED");
      }
    }
    return balanced.found ? balanced : shortest;
  }

  private String shortestAlgo(List<SegmentRoute> segments, PathResult dijkstra) {
    if (!segments.isEmpty()) return "OSRM_SHORTEST";
    return dijkstra.found ? "DIJKSTRA" : "NONE";
  }

  private PathResult relabel(PathResult p, String type) {
    return new PathResult(p.found, p.path, p.distance, p.safetyScore, type);
  }

  private PathResult toResult(List<Node> nodes, double dist, double safety, String type) {
    return new PathResult(true, nodes, dist, safety, type);
  }

  private boolean pathsEqual(List<Node> a, List<Node> b) {
    return YenPathFinder.pathsEqual(a, b);
  }

  private enum ScoreMode { SHORTEST, SAFEST, BALANCED }

  private PathResult applyDisplayScore(Graph graph, PathResult path, ScoreMode mode) {
    if (!path.found || path.path == null || path.path.size() < 2) return path;
    double avg = PathMetrics.calculateAverageSafety(graph, path.path);
    double min = PathMetrics.calculateMinSafety(graph, path.path);
    double score;
    switch (mode) {
      case SHORTEST:
        score = Math.max(0, avg - 5);
        break;
      case SAFEST:
        score = Math.min(100, 0.45 * avg + 0.55 * min + 8);
        break;
      default:
        score = 0.7 * avg + 0.3 * min;
        break;
    }
    return new PathResult(path.found, path.path, path.distance, score, path.type);
  }

  private static class SegmentRoute {
    final List<Node> nodes;
    final double distance;
    final double safety;

    SegmentRoute(List<Node> nodes, double distance, double safety) {
      this.nodes = nodes;
      this.distance = distance;
      this.safety = safety;
    }
  }
}
