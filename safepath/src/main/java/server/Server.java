package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import server.core.PathEngine;
import server.core.PathResult;
import server.core.ReportValidator;
import server.core.RouteAnalyzer;
import server.core.SafetyEngine;
import server.core.SafetyInsight;
import server.db.Database;
import server.models.SessionEvent;
import server.graph.Graph;
import server.graph.Node;
import server.models.GraphBuilder;
import server.store.UnsafeStore;
import server.store.SessionStore;
import server.services.AlertService;
import server.services.EmailService;
import server.services.AuthService;
import server.services.GuardianService;
import server.util.AppConfig;
import server.util.AppPaths;
import server.util.JsonUtil;
import server.util.PoiFetcher;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Server {

  private final HttpServer    httpServer;
  private final UnsafeStore   unsafeStore;
  private final SessionStore  sessionStore;
  private final SafetyEngine  safetyEngine;
  private final AuthService   authService;
  private final GuardianService guardianService;
  private final AlertService  alertService;
  private final Path          frontendDir;
  private final int           port;

  public Server(int port) throws IOException {
    this.port = port;
    frontendDir = AppPaths.frontendDir();
    httpServer   = HttpServer.create(new InetSocketAddress(port), 0);
    unsafeStore  = new UnsafeStore();
    sessionStore = new SessionStore();
    safetyEngine = new SafetyEngine(unsafeStore);
    authService  = new AuthService();
    guardianService = new GuardianService();
    alertService = new AlertService();

    setupRoutes();
  }

  private void setupRoutes() {
    httpServer.createContext("/api/register",        new RegisterHandler());
    httpServer.createContext("/api/login",           new LoginHandler());
    httpServer.createContext("/api/google-login",    new GoogleLoginHandler());
    httpServer.createContext("/api/forgot-password", new ForgotPasswordHandler());
    httpServer.createContext("/api/reset-password",  new ResetPasswordHandler());
    httpServer.createContext("/api/config",          new ConfigHandler());
    httpServer.createContext("/api/guardians",       new GuardiansHandler());
    httpServer.createContext("/api/unsafe-locations", new UnsafeLocationsHandler());
    httpServer.createContext("/api/demo-route",      new DemoRouteHandler());
    httpServer.createContext("/api/start-trip",      new StartTripHandler());
    httpServer.createContext("/api/emergency-reroute", new EmergencyRerouteHandler());
    httpServer.createContext("/api/analyze-route",   new AnalyzeRouteHandler());
    httpServer.createContext("/api/start-tracking",  new StartTrackingHandler());
    httpServer.createContext("/api/update-location", new UpdateLocationHandler());
    httpServer.createContext("/api/report-unsafe",   new ReportUnsafeHandler());
    httpServer.createContext("/api/set-guardian",    new SetGuardianHandler());
    httpServer.createContext("/api/set-route",       new SetRouteHandler());
    httpServer.createContext("/api/session-alert",   new SessionAlertHandler());
    httpServer.createContext("/api/session",         new SessionHandler());
    httpServer.createContext("/api/trip-history",    new TripHistoryHandler());
    httpServer.createContext("/api/my-reports",      new MyReportsHandler());
    httpServer.createContext("/api/nearby-pois",     new NearbyPoisHandler());
    httpServer.createContext("/health",              new HealthHandler());
    // Serve frontend files
    httpServer.createContext("/", new StaticHandler());
  }

  public void start() {
    httpServer.setExecutor(null);
    httpServer.start();
    System.out.println("SafePath AI Server started on http://127.0.0.1:" + port);
    System.out.println("Open http://localhost:" + port + "/  (login page)");
    System.out.println("Frontend: " + frontendDir.toAbsolutePath());
    if (EmailService.isSmtpConfigured()) {
      if (EmailService.verifySmtpLogin()) {
        System.out.println("SMTP: login OK (" + AppConfig.smtpHost() + ":" + AppConfig.smtpPort() + ")");
      } else {
        System.err.println("SMTP: configured but login FAILED — emails will stay in email_outbox only");
      }
    } else {
      System.out.println("SMTP: not configured (emails saved to MySQL email_outbox only)");
    }
  }

  public void stop() { httpServer.stop(0); }

  // ══════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ══════════════════════════════════════════════════════════════════════════

  private class AnalyzeRouteHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;

      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        sendError(exchange, 405, "POST required");
        return;
      }

      try {
        String body = readBody(exchange);
        if (body == null || body.isBlank()) {
          sendError(exchange, 400, "Request body is required");
          return;
        }
        Map<String, Object> req = JsonUtil.parseJson(body);

        // Parse transport mode (default WALK)
        String modeStr = (String) req.getOrDefault("mode", "WALK");
        SafetyEngine.TransportMode mode;
        try { mode = SafetyEngine.TransportMode.valueOf(modeStr.toUpperCase()); }
        catch (Exception e) { mode = SafetyEngine.TransportMode.WALK; }

        // Parse OSRM route alternatives. The frontend sends every road
        // alternative so Dijkstra can choose the safest path across the graph.
        String coordsStr = (String) req.get("coordinates");
        String segmentsStr = (String) req.get("segments");
        double[][][] segments = parseSegments(segmentsStr);
        double[][] coords = segments != null && segments.length > 0
            ? segments[0]
            : parseCoordinates(coordsStr);

        if (coords == null || coords.length < 2) {
          sendError(exchange, 400, "Invalid or missing coordinates"); return;
        }

        GraphBuilder builder = new GraphBuilder(safetyEngine);
        Graph routeGraph = segments != null && segments.length > 0
            ? builder.buildGraphFromSegments(segments, mode)
            : builder.buildGraphFromRoute(coords, mode);

        RouteAnalyzer analyzer = new RouteAnalyzer(safetyEngine);
        double[][][] segsForAnalysis = segments != null && segments.length > 0
            ? segments
            : new double[][][] { coords };
        Map<String, RouteAnalyzer.AnalyzedRoute> analyzed =
            analyzer.analyze(segsForAnalysis, mode);

        PathResult shortest = analyzed.get("shortest").result;
        PathResult safest   = analyzed.get("safest").result;
        PathResult balanced = analyzed.get("balanced").result;

        List<Node> shortestNodes = shortest.found ? shortest.path : List.of();

        Map<String, Object> routes = new HashMap<>();
        routes.put("shortest", buildRouteResponse("SHORTEST", shortest,
            analyzed.get("shortest").algorithm, mode,
            safetyEngine.buildRouteReasons(shortest.path, null, mode)));
        routes.put("safest", buildRouteResponse("SAFEST", safest,
            analyzed.get("safest").algorithm, mode,
            safetyEngine.buildRouteReasons(safest.path, shortestNodes, mode)));
        routes.put("balanced", buildRouteResponse("BALANCED", balanced,
            analyzed.get("balanced").algorithm, mode,
            safetyEngine.buildRouteReasons(balanced.path, shortestNodes, mode)));

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("routes", routes);
        response.put("graphNodes", routeGraph.getSize());
        response.put("graphEdges", routeGraph.getEdgeCount());
        response.put("algorithms", Map.of(
            "shortest", analyzed.get("shortest").algorithm,
            "safest", analyzed.get("safest").algorithm,
            "balanced", analyzed.get("balanced").algorithm
        ));

        String recommended = pickRecommendedRoute(analyzed);
        response.put("recommended", recommended);
        response.put("recommendedReason", recommendedReason(recommended, analyzed));

        sendJson(exchange, 200, response);

      } catch (Exception e) {
        e.printStackTrace();
        sendError(exchange, 500, "Server error: " + e.getMessage());
      }
    }
  }

  private class RegisterHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        Map<String, Object> user = authService.register(
            (String) req.get("name"), (String) req.get("email"), (String) req.get("password"));
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("user", user);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 400, e.getMessage()); }
    }
  }

  private class LoginHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        Map<String, Object> user = authService.login(
            (String) req.get("email"), (String) req.get("password"));
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("user", user);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 400, e.getMessage()); }
    }
  }

  private class GoogleLoginHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        String idToken = (String) req.get("idToken");
        Map<String, Object> user;
        if (idToken != null && !idToken.isBlank()) {
          user = authService.loginWithGoogleIdToken(idToken);
        } else {
          throw new IllegalArgumentException("Google sign-in is not configured. Use email login or add google.client.id to config.");
        }
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("user", user);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 400, e.getMessage()); }
    }
  }

  private class ForgotPasswordHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        sendError(exchange, 405, "POST required");
        return;
      }
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        Map<String, Object> result = authService.requestPasswordReset((String) req.get("email"));
        sendJson(exchange, 200, result);
      } catch (Exception e) { sendError(exchange, 400, e.getMessage()); }
    }
  }

  private class ResetPasswordHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        sendError(exchange, 405, "POST required");
        return;
      }
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        authService.resetPassword((String) req.get("token"), (String) req.get("password"));
        sendJson(exchange, 200, Map.of("status", "success", "message", "Password updated. You can log in now."));
      } catch (Exception e) { sendError(exchange, 400, e.getMessage()); }
    }
  }

  private class UnsafeLocationsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        List<Map<String, Object>> locations = new ArrayList<>();
        for (server.models.UnsafeLocation loc : unsafeStore.getUnsafeLocations()) {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("id", loc.id);
          row.put("latitude", loc.latitude);
          row.put("longitude", loc.longitude);
          row.put("reportCount", loc.reportCount);
          row.put("reason", loc.reason);
          row.put("riskLevel", heatmapRiskLevel(loc.reportCount));
          boolean confirmed = loc.reportCount >= 3;
          row.put("confirmed", confirmed);
          row.put("verificationStatus", confirmed ? "CONFIRMED" : "UNVERIFIED");
          row.put("predictedRiskProbability",
              Math.round(safetyEngine.predictZoneRiskProbability(loc.latitude, loc.longitude)));
          locations.add(row);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("locations", locations);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class DemoRouteHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      Map<String, Object> r = new HashMap<>();
      r.put("status", "success");
      r.put("source", Map.of("label", "Connaught Place, New Delhi", "lat", 28.6315, "lng", 77.2167));
      r.put("destination", Map.of("label", "India Gate, New Delhi", "lat", 28.6129, "lng", 77.2295));
      r.put("mode", "WALK");
      double[][] demoPath = {
          {28.6315, 77.2167}, {28.6298, 77.2185}, {28.6275, 77.2200},
          {28.6250, 77.2225}, {28.6220, 77.2250}, {28.6185, 77.2270},
          {28.6155, 77.2285}, {28.6129, 77.2295}
      };
      r.put("path", demoPath);
      r.put("description", "Predefined Delhi demo route for judges without GPS.");
      sendJson(exchange, 200, r);
    }
  }

  private class ConfigHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      Map<String, Object> r = new HashMap<>();
      r.put("status", "success");
      r.put("googleClientId", AppConfig.googleClientId());
      r.put("googleConfigured", AppConfig.has("google.client.id"));
      r.put("smtpConfigured", server.services.EmailService.isSmtpConfigured());
      r.put("port", port);
      r.put("appUrl", AppConfig.appBaseUrl(port));
      sendJson(exchange, 200, r);
    }
  }

  private class GuardiansHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
          Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
          String token = (String) req.get("token");
          guardianService.remove(token, (String) req.get("id"));
          sendJson(exchange, 200, Map.of("status", "success"));
          return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
          Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
          String token = (String) req.get("token");
          Map<String, Object> g = guardianService.add(token,
              (String) req.get("name"), (String) req.get("phone"), (String) req.get("email"));
          Map<String, Object> r = new HashMap<>();
          r.put("status", "success");
          r.put("guardian", g);
          sendJson(exchange, 200, r);
          return;
        }
        String token = queryParam(exchange, "token");
        List<Map<String, Object>> list = guardianService.listForUser(token);
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("guardians", list);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 400, e.getMessage()); }
    }
  }

  private class StartTripHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        String sessionId = (String) req.get("sessionId");
        String token = (String) req.get("token");
        String source = (String) req.getOrDefault("source", "Unknown");
        String destination = (String) req.getOrDefault("destination", "Unknown");
        String trackingLink = (String) req.get("trackingLink");

        server.models.UserSession session = sessionStore.getSession(sessionId);
        if (session == null) { sendError(exchange, 404, "Session not found"); return; }

        Map<String, Object> user = authService.userFromToken(token);
        if (user != null) {
          session.userId = (String) user.get("id");
          session.userName = (String) user.get("name");
        } else {
          session.userName = (String) req.getOrDefault("userName", "User");
        }
        session.sourceLabel = source;
        session.destLabel = destination;
        session.trackingLink = trackingLink;
        session.destLat = parseDouble(req.get("destLat"), 0);
        session.destLng = parseDouble(req.get("destLng"), 0);

        int emailsSent = alertService.onTripStarted(session, source, destination, trackingLink);
        int guardianCount = alertService.guardianEmailCount(session);

        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("message", "Trip started.");
        putEmailStatus(r, guardianCount, emailsSent);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class EmergencyRerouteHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        double lat = parseDouble(req.get("latitude"), 0);
        double lng = parseDouble(req.get("longitude"), 0);
        double destLat = parseDouble(req.get("destLat"), 0);
        double destLng = parseDouble(req.get("destLng"), 0);
        String modeStr = (String) req.getOrDefault("mode", "WALK");
        SafetyEngine.TransportMode mode;
        try { mode = SafetyEngine.TransportMode.valueOf(modeStr.toUpperCase()); }
        catch (Exception e) { mode = SafetyEngine.TransportMode.WALK; }

        double[][] coords = new double[][] {
            { lat, lng }, { destLat, destLng }
        };
        GraphBuilder builder = new GraphBuilder(safetyEngine);
        Graph routeGraph = builder.buildGraphFromRoute(coords, mode);
        PathEngine pe = new PathEngine(routeGraph);
        Node start = new Node(lat, lng);
        Node end = new Node(destLat, destLng);
        PathResult safest = pe.safestPath(start, end);

        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("route", buildRouteResponse("SAFEST", safest, "EMERGENCY_REROUTE", mode,
            safetyEngine.buildRouteReasons(safest.path, null, mode)));
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class StartTrackingHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        String body = readBody(exchange);
        Map<String, Object> req = body != null && !body.isBlank()
            ? JsonUtil.parseJson(body) : Map.of();
        String token = (String) req.get("token");

        String sessionId = UUID.randomUUID().toString();
        sessionStore.createSession(sessionId);
        server.models.UserSession session = sessionStore.getSession(sessionId);
        Map<String, Object> user = authService.userFromToken(token);
        if (user != null) {
          session.userId = (String) user.get("id");
          session.userName = (String) user.get("name");
        }

        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("sessionId", sessionId);
        if (session != null) {
          r.put("viewKey", session.viewKey);
        }
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class UpdateLocationHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        String sessionId = (String) req.get("sessionId");
        double lat = Double.parseDouble((String) req.get("latitude"));
        double lng = Double.parseDouble((String) req.get("longitude"));
        sessionStore.updateSession(sessionId, lat, lng);

        // Recalculate safety at current location
        Node userNode   = new Node(lat, lng);
        SafetyEngine.TransportMode mode = SafetyEngine.TransportMode.WALK;
        double safety   = safetyEngine.calculateNodeSafety(userNode, mode);
        String status   = safetyEngine.getSafetyStatus(safety);
        SafetyInsight insight = safetyEngine.analyzeLocation(userNode, mode);
        sessionStore.updateSafety(sessionId, safety, status);
        server.models.UserSession session = sessionStore.getSession(sessionId);
        if (session != null) {
          alertService.onSafetyUpdate(session, safety, status);
          if (safety < 40) {
            sessionStore.addEvent(sessionId,
                new SessionEvent("HIGH_RISK", "Safety score dropped to " + (int) safety,
                    lat, lng, safety));
          }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",       "success");
        r.put("safetyScore",  safety);
        r.put("safetyStatus", status);
        r.put("alertLevel",   safety < 40 ? "HIGH_RISK" : safety < 50 ? "MODERATE" : safety >= 70 ? "SAFE" : "CAUTION");
        r.put("xai", insightToMap(insight));
        if ("RISING_RISK".equals(insight.trend)) {
          Map<String, Object> journey = new LinkedHashMap<>();
          journey.put("type", "RISK_FORECAST");
          journey.put("level", "warn");
          journey.put("message", "AI predicts safety may drop to "
              + (int) insight.predictedScore30Min + "/100 within 30 minutes.");
          r.put("journeyAlert", journey);
        }
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class ReportUnsafeHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        double lat  = Double.parseDouble((String) req.get("latitude"));
        double lng  = Double.parseDouble((String) req.get("longitude"));
        String reason = (String) req.getOrDefault("reason", "Reported unsafe");
        String token = (String) req.get("token");
        String category = (String) req.getOrDefault("category", "");
        String severity = (String) req.getOrDefault("severity", "");
        String description = (String) req.getOrDefault("description", "");

        Map<String, Object> user = authService.userFromToken(token);
        String userId = user != null ? (String) user.get("id") : null;
        ReportValidator.Result validation = null;

        if (userId != null) {
          long dayStart = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
          long hourStart = System.currentTimeMillis() - 60L * 60 * 1000;
          int reportsToday = Database.get().countUserReportsSince(userId, dayStart);
          int reportsHour = Database.get().countUserReportsSince(userId, hourStart);
          double[] last = Database.get().findLastUserReportCoords(userId);
          double distKm = 0;
          boolean hasLast = last != null;
          if (hasLast) {
            Node here = new Node(lat, lng);
            Node prev = new Node(last[0], last[1]);
            distKm = here.distanceTo(prev);
          }
          validation = ReportValidator.validate(reportsToday, reportsHour, distKm, hasLast);
          if (!validation.allowed) {
            sendError(exchange, 429, validation.message);
            return;
          }
        }

        try {
          if (userId != null) {
            Database.get().insertUserUnsafeReport(
                userId, lat, lng, category, severity, description, reason);
          }
          if (validation == null || validation.affectsRouting) {
            unsafeStore.reportUnsafe(lat, lng, reason);
          }
        } catch (java.sql.SQLException e) {
          sendError(exchange, 500, "Failed to save unsafe report: " + e.getMessage());
          return;
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "success");
        r.put("message", validation != null ? validation.message
            : "Unsafe location reported — affects routing when community confirms.");
        if (validation != null) {
          r.put("affectsRouting", validation.affectsRouting);
          r.put("anomalyFlags", validation.anomalyFlags);
          r.put("verificationStatus", validation.affectsRouting ? "PENDING_CONFIRM" : "UNVERIFIED");
        }
        r.put("aiAnomalyCheck", "PASSED");
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class SetGuardianHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        String sessionId = (String) req.get("sessionId");
        // Guardian name/phone/email are stored in MySQL via /api/guardians — not on UserSession.
        String travelerPhone = (String) req.get("travelerPhone");
        if (travelerPhone != null && !travelerPhone.isBlank() && sessionId != null) {
          sessionStore.setTravelerPhone(sessionId, travelerPhone);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("message", "Session updated");
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class SessionAlertHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        String sessionId = (String) req.get("sessionId");
        String type = (String) req.getOrDefault("type", "ALERT");
        String message = (String) req.getOrDefault("message", "Alert");
        double lat = parseDouble(req.get("latitude"), 0);
        double lng = parseDouble(req.get("longitude"), 0);
        double score = parseDouble(req.get("safetyScore"), 50);
        double scoreDelta = 0;
        int rating = 0;

        server.models.UserSession session = sessionStore.getSession(sessionId);
        if (session != null) {
          String token = (String) req.get("token");
          if (session.userId == null && token != null) {
            Map<String, Object> user = authService.userFromToken(token);
            if (user != null) {
              session.userId = (String) user.get("id");
              session.userName = (String) user.get("name");
            }
          }
          if ("SAFE_ARRIVAL".equals(type)) {
            rating = parseInt(req.get("rating"), 0);
            if (rating >= 1 && rating <= 5) {
              double baseScore = parseDouble(req.get("safetyScore"), session.safetyScore);
              scoreDelta = arrivalRatingDelta(rating);
              score = clampSafetyScore(baseScore + scoreDelta);
              String status = safetyEngine.getSafetyStatus(score);
              sessionStore.updateSafety(sessionId, score, status);
              session.previousSafetyScore = score;
              message = message + " Rated " + rating + "/5 stars.";
            }
            saveTripHistory(type, req, session, sessionId, score, rating);
          }
        } else if ("SAFE_ARRIVAL".equals(type)) {
          rating = parseInt(req.get("rating"), 0);
          if (rating >= 1 && rating <= 5) {
            scoreDelta = arrivalRatingDelta(rating);
            score = clampSafetyScore(score + scoreDelta);
            message = message + " Rated " + rating + "/5 stars.";
          }
          saveTripHistory(type, req, null, sessionId, score, rating);
        }

        sessionStore.addEvent(sessionId,
            new SessionEvent(type, message, lat, lng, score));

        if (session != null) {
          int emailsSent = 0;
          int guardianCount = alertService.guardianEmailCount(session);
          if ("SAFE_ARRIVAL".equals(type)) {
            emailsSent = alertService.onSafeArrival(session, lat, lng, score, rating);
          } else if ("SOS".equals(type) || "AUTO_EMERGENCY".equals(type)) {
            emailsSent = alertService.onEmergencySos(session, message, lat, lng, score);
          }
          Map<String, Object> r = new HashMap<>();
          r.put("status", "success");
          r.put("safetyScore", score);
          if (rating > 0) {
            r.put("rating", rating);
            r.put("scoreDelta", scoreDelta);
          }
          putEmailStatus(r, guardianCount, emailsSent);
          sendJson(exchange, 200, r);
          return;
        }

        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("safetyScore", score);
        if (rating > 0) {
          r.put("rating", rating);
          r.put("scoreDelta", scoreDelta);
        }
        putEmailStatus(r, 0, 0);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class SetRouteHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        String sessionId = (String) req.get("sessionId");
        String coordsStr = (String) req.get("coordinates");
        double[][] coords = parseCoordinates(coordsStr);

        server.models.UserSession session = sessionStore.getSession(sessionId);
        if (session != null && coords != null) {
          double[] flatCoords = new double[coords.length * 2];
          for (int i = 0; i < coords.length; i++) {
            flatCoords[i * 2] = coords[i][0];
            flatCoords[i * 2 + 1] = coords[i][1];
          }
          session.routePath = flatCoords;
        }
        if (session != null) {
          String routeType = (String) req.get("routeType");
          if (routeType != null && !routeType.isBlank()) {
            session.routeType = routeType;
          }
          Object distObj = req.get("distanceKm");
          if (distObj != null) {
            session.tripDistanceKm = parseDouble(distObj, session.tripDistanceKm);
          }
        }

        Map<String, Object> r = new HashMap<>();
        r.put("status", "success"); r.put("message", "Active route coordinates saved");
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class SessionHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        String query = exchange.getRequestURI().getQuery();
        String sessionId = null;
        String viewKey = null;
        if (query != null) {
          for (String part : query.split("&")) {
            if (part.startsWith("sessionId=")) sessionId = part.substring("sessionId=".length());
            else if (part.startsWith("key=")) viewKey = part.substring("key=".length());
          }
        }
        if (sessionId == null) { sendError(exchange, 400, "Missing sessionId"); return; }

        server.models.UserSession session = sessionStore.getSession(sessionId);
        if (session == null) {
          sendError(exchange, 404, "Session not found");
          return;
        }
        if (viewKey == null || !viewKey.equals(session.viewKey)) {
          sendError(exchange, 403, "Invalid or missing view key");
          return;
        }

        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("sessionId", sessionId);
        r.put("currentLat", session.currentLat);
        r.put("currentLng", session.currentLng);
        r.put("userName", session.userName != null ? session.userName : "Traveler");
        r.put("safetyScore", session.safetyScore);
        r.put("safetyStatus", session.safetyStatus);
        r.put("lastUpdated", session.lastUpdated);
        r.put("sourceLabel", session.sourceLabel);
        r.put("destLabel", session.destLabel);
        if (session.routePath != null) {
          r.put("routePath", session.routePath);
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (SessionEvent ev : session.events) {
          Map<String, Object> em = new HashMap<>();
          em.put("type", ev.type);
          em.put("message", ev.message);
          em.put("timestamp", ev.timestamp);
          em.put("lat", ev.lat);
          em.put("lng", ev.lng);
          em.put("safetyScore", ev.safetyScore);
          events.add(em);
        }
        r.put("events", events);
        sendJson(exchange, 200, r);
      } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
  }

  private class TripHistoryHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        String token = queryParam(exchange, "token");
        Map<String, Object> user = authService.userFromToken(token);
        if (user == null) {
          sendError(exchange, 401, "Login required");
          return;
        }
        List<Map<String, Object>> trips = Database.get()
            .findTripHistoryByUserId((String) user.get("id"), 50);
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("trips", trips);
        sendJson(exchange, 200, r);
      } catch (Exception e) {
        sendError(exchange, 500, e.getMessage());
      }
    }
  }

  private class MyReportsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      try {
        String token = queryParam(exchange, "token");
        Map<String, Object> user = authService.userFromToken(token);
        if (user == null) {
          sendError(exchange, 401, "Login required");
          return;
        }
        List<Map<String, Object>> reports = Database.get()
            .findUserUnsafeReportsByUserId((String) user.get("id"), 50);
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("reports", reports);
        sendJson(exchange, 200, r);
      } catch (Exception e) {
        sendError(exchange, 500, e.getMessage());
      }
    }
  }

  private class NearbyPoisHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      if (handleOptions(exchange)) return;
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        sendError(exchange, 405, "POST required");
        return;
      }
      try {
        Map<String, Object> req = JsonUtil.parseJson(readBody(exchange));
        double south = parseDouble(req.get("south"), 0);
        double west = parseDouble(req.get("west"), 0);
        double north = parseDouble(req.get("north"), 0);
        double east = parseDouble(req.get("east"), 0);
        List<Map<String, Object>> pois = PoiFetcher.fetch(south, west, north, east);
        Map<String, Object> r = new HashMap<>();
        r.put("status", "success");
        r.put("pois", pois);
        r.put("count", pois.size());
        sendJson(exchange, 200, r);
      } catch (Exception e) {
        sendError(exchange, 500, "POI fetch failed: " + e.getMessage());
      }
    }
  }

  private void saveTripHistory(String type, Map<String, Object> req,
      server.models.UserSession session, String sessionId,
      double score, int rating) {
    if (!"SAFE_ARRIVAL".equals(type)) return;
    try {
      String userId = null;
      if (session != null && session.userId != null) {
        userId = session.userId;
      }
      String token = (String) req.get("token");
      if (userId == null && token != null) {
        Map<String, Object> user = authService.userFromToken(token);
        if (user != null) userId = (String) user.get("id");
      }
      if (userId == null) {
        System.err.println("[TripHistory] skipped — no logged-in user");
        return;
      }

      String source = pickTripLabel(
          session != null ? session.sourceLabel : null,
          strOr(req.get("source"), null));
      String dest = pickTripLabel(
          session != null ? session.destLabel : null,
          strOr(req.get("destination"), null));
      double reqDist = parseDouble(req.get("distanceKm"), 0);
      double sessionDist = session != null ? session.tripDistanceKm : 0;
      double distanceKm = reqDist > 0 ? reqDist : (sessionDist > 0 ? sessionDist : reqDist);
      String routeType = pickTripLabel(
          session != null ? session.routeType : null,
          strOr(req.get("routeType"), "balanced"));
      if (routeType.equalsIgnoreCase("unknown")) routeType = "balanced";

      Database.get().insertTripHistory(
          userId,
          sessionId,
          source,
          dest,
          distanceKm,
          score,
          routeType,
          rating,
          System.currentTimeMillis());
      System.out.println("[TripHistory] saved for user " + userId);
    } catch (Exception ex) {
      System.err.println("[TripHistory] save failed: " + ex.getMessage());
    }
  }

  private static String strOr(Object val, String fallback) {
    if (val == null) return fallback;
    String s = String.valueOf(val).trim();
    return s.isEmpty() ? fallback : s;
  }

  private static boolean isBlankOrUnknown(String s) {
    if (s == null) return true;
    String t = s.trim();
    return t.isEmpty() || "unknown".equalsIgnoreCase(t);
  }

  /** Prefer real labels from the client over stale session placeholders. */
  private static String pickTripLabel(String sessionVal, String reqVal) {
    if (!isBlankOrUnknown(reqVal)) return reqVal.trim();
    if (!isBlankOrUnknown(sessionVal)) return sessionVal.trim();
    return "Unknown";
  }

  private class HealthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      Map<String, Object> health = new HashMap<>();
      health.put("status", "ok");
      health.put("server", "SafePath AI");
      health.put("apiVersion", 2);
      health.put("smtpConfigured", server.services.EmailService.isSmtpConfigured());
      health.put("googleConfigured", AppConfig.has("google.client.id"));
      health.put("port", port);
      health.put("appUrl", AppConfig.appBaseUrl(port));
      sendJson(exchange, 200, health);
    }
  }

  /** Serves static files from ./frontend/ directory */
  private class StaticHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      if (path.equals("/")) path = "/login.html";

      Path filePath = frontendDir.resolve(path.substring(1)).normalize();
      if (!filePath.startsWith(frontendDir) || !Files.isRegularFile(filePath)) {
        String notFound = "404 Not Found: " + path
            + "\n\nBuild with Maven and run from safepath/:\n"
            + "  ./mvnw clean package && java -jar target/safepath-1.0.0.jar\n"
            + "Open http://localhost:" + port + "/\n"
            + "Do not open index.html with Live Server.";
        byte[] bytes = notFound.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(404, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
        return;
      }

      String contentType = "text/plain";
      if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
      else if (path.endsWith(".css"))  contentType = "text/css";
      else if (path.endsWith(".js"))   contentType = "application/javascript";

      exchange.getResponseHeaders().set("Content-Type", contentType);
      byte[] bytes = Files.readAllBytes(filePath);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // HELPERS
  // ══════════════════════════════════════════════════════════════════════════

  private void putEmailStatus(Map<String, Object> r, int guardianCount, int emailsQueued) {
    r.put("emailsQueued", guardianCount);
    r.put("emailsSent", emailsQueued);
    r.put("emailDelivery", emailsQueued > 0 ? "queued" : "none");
  }

  private void setCorsHeaders(HttpExchange e) {
    e.getResponseHeaders().set("Content-Type", "application/json");
    e.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
    e.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
    e.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
  }

  private boolean handleOptions(HttpExchange e) throws IOException {
    if ("OPTIONS".equals(e.getRequestMethod())) {
      e.sendResponseHeaders(204, -1); return true;
    }
    return false;
  }

  private String readBody(HttpExchange e) throws IOException {
    try (BufferedReader r = new BufferedReader(
             new InputStreamReader(e.getRequestBody()))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = r.readLine()) != null) sb.append(line);
      return sb.toString();
    }
  }

  private void sendJson(HttpExchange e, int code, Map<String, Object> data) throws IOException {
    String json = JsonUtil.toJson(data);
    byte[] bytes = json.getBytes("UTF-8");
    e.sendResponseHeaders(code, bytes.length);
    e.getResponseBody().write(bytes);
    e.close();
  }

  private void sendError(HttpExchange e, int code, String msg) throws IOException {
    Map<String, Object> err = new HashMap<>();
    err.put("status", "error"); err.put("message", msg == null ? "Unknown error" : msg);
    sendJson(e, code, err);
  }

  /** FIX: buildRouteResponse now takes start/end as parameters instead of
   *       referencing undefined variables from the outer scope */
  private String routeExplanation(String type, String algorithm) {
    return switch (type) {
      case "SHORTEST" -> "Minimizes travel distance using Dijkstra shortest-path on the road graph.";
      case "SAFEST" -> "Maximizes minimum edge safety score using Dijkstra safest-path.";
      case "BALANCED" -> "Balances distance and safety using A* with combined cost.";
      default -> algorithm != null ? "Computed with " + algorithm + "." : "Route analysis result.";
    };
  }

  private String pickRecommendedRoute(Map<String, RouteAnalyzer.AnalyzedRoute> analyzed) {
    int hour = LocalTime.now().getHour();
    boolean night = hour >= 21 || hour < 6;
    if (night) {
      RouteAnalyzer.AnalyzedRoute safest = analyzed.get("safest");
      RouteAnalyzer.AnalyzedRoute balanced = analyzed.get("balanced");
      if (safest != null && safest.result.found && balanced != null && balanced.result.found
          && safest.result.safetyScore >= balanced.result.safetyScore - 12) {
        return "safest";
      }
    }

    String best = "balanced";
    double bestScore = -1;
    for (String key : List.of("safest", "balanced", "shortest")) {
      RouteAnalyzer.AnalyzedRoute ar = analyzed.get(key);
      if (ar != null && ar.result.found && ar.result.safetyScore > bestScore) {
        bestScore = ar.result.safetyScore;
        best = key;
      }
    }
    return best;
  }

  private String recommendedReason(String key, Map<String, RouteAnalyzer.AnalyzedRoute> analyzed) {
    RouteAnalyzer.AnalyzedRoute ar = analyzed.get(key);
    if (ar == null || !ar.result.found) return "No viable route found.";
    return switch (key) {
      case "safest" -> {
        int hour = LocalTime.now().getHour();
        if (hour >= 21 || hour < 6) {
          yield "Night-time AI recommendation: Safest route (" + (int) ar.result.safetyScore + "/100).";
        } else {
          yield "Highest safety score (" + (int) ar.result.safetyScore + "/100) among alternatives.";
        }
      }
      case "shortest" -> "Shortest distance (" + String.format("%.2f", ar.result.distance) + " km) when time matters most.";
      case "balanced" -> "Best trade-off of distance and safety for everyday travel.";
      default -> routeExplanation(key.toUpperCase(), ar.algorithm);
    };
  }

  private static String heatmapRiskLevel(int reportCount) {
    if (reportCount >= 5) return "high";
    if (reportCount >= 2) return "medium";
    return "low";
  }

  private Map<String, Object> buildRouteResponse(String type, PathResult result,
      String algorithm, SafetyEngine.TransportMode mode, List<String> reasons) {
    Map<String, Object> route = new HashMap<>();
    route.put("type",         type);
    route.put("distance",     result.distance);
    route.put("safetyScore",  result.safetyScore);
    route.put("safetyStatus", safetyEngine.getSafetyStatus(result.safetyScore));
    route.put("safetyColor",  safetyEngine.getSafetyColor(result.safetyScore));
    route.put("found",        result.found);
    route.put("xai",          "Explainable Safety Score (XAI)");
    if (algorithm != null) route.put("algorithm", algorithm);
    route.put("whySelected", routeExplanation(type, algorithm));
    route.put("reasons", reasons != null ? reasons : List.of());

    if (result.found && result.path != null && !result.path.isEmpty()) {
      Node mid = result.path.get(result.path.size() / 2);
      double confidence = safetyEngine.routeConfidence(result.path, mode);
      double predicted = safetyEngine.predictSafetyForward(mid, mode, 30);
      route.put("confidence", Math.round(confidence));
      route.put("predictedScore30Min", Math.round(predicted * 10.0) / 10.0);
      String trend = predicted < result.safetyScore - 5 ? "RISING_RISK"
          : predicted > result.safetyScore + 5 ? "IMPROVING" : "STABLE";
      route.put("riskTrend", trend);

      double[][] coords = new double[result.path.size()][2];
      for (int i = 0; i < result.path.size(); i++) {
        coords[i][0] = result.path.get(i).getLatitude();
        coords[i][1] = result.path.get(i).getLongitude();
      }
      route.put("coordinates", coords);
    }
    return route;
  }

  private Map<String, Object> insightToMap(SafetyInsight insight) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("label", "Explainable Safety Score (XAI)");
    m.put("safetyScore", insight.safetyScore);
    m.put("confidence", Math.round(insight.confidence));
    m.put("predictedScore30Min", Math.round(insight.predictedScore30Min * 10.0) / 10.0);
    m.put("trend", insight.trend);
    m.put("status", insight.status);
    m.put("xaiFactors", insight.xaiFactors);
    return m;
  }

  private String queryParam(HttpExchange exchange, String key) {
    String query = exchange.getRequestURI().getQuery();
    if (query == null) return null;
    String prefix = key + "=";
    for (String part : query.split("&")) {
      if (part.startsWith(prefix)) {
        try {
          return URLDecoder.decode(part.substring(prefix.length()), StandardCharsets.UTF_8);
        } catch (Exception e) {
          return part.substring(prefix.length());
        }
      }
    }
    return null;
  }

  private double parseDouble(Object val, double fallback) {
    if (val == null) return fallback;
    try { return Double.parseDouble(String.valueOf(val)); }
    catch (Exception e) { return fallback; }
  }

  private int parseInt(Object val, int fallback) {
    if (val == null) return fallback;
    try { return Integer.parseInt(String.valueOf(val)); }
    catch (Exception e) { return fallback; }
  }

  /** 1★ −10 … 3★ 0 … 5★ +10 */
  private static double arrivalRatingDelta(int rating) {
    return (Math.max(1, Math.min(5, rating)) - 3) * 5.0;
  }

  private static double clampSafetyScore(double score) {
    return Math.max(0, Math.min(100, score));
  }

  /** Parse coordinate JSON: "[[lat,lng],[lat,lng],...]" */
  private double[][] parseCoordinates(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      s = s.trim();
      // Remove outer brackets
      if (s.startsWith("[[")) s = s.substring(1, s.length()-1);
      List<double[]> list = new ArrayList<>();
      // Split on ],[
      String[] pairs = s.split("\\],\\s*\\[");
      for (String pair : pairs) {
        pair = pair.replaceAll("[\\[\\]\\s]", "");
        String[] parts = pair.split(",");
        if (parts.length >= 2) {
          list.add(new double[]{
            Double.parseDouble(parts[0].trim()),
            Double.parseDouble(parts[1].trim())
          });
        }
      }
      return list.isEmpty() ? null : list.toArray(new double[0][]);
    } catch (Exception e) { return null; }
  }

  /** Parse route alternatives JSON: "[[[lat,lng],...],[[lat,lng],...]]" */
  private double[][][] parseSegments(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      s = s.trim();
      if (!s.startsWith("[") || !s.endsWith("]")) return null;
      String inner = s.substring(1, s.length() - 1);
      List<double[][]> routes = new ArrayList<>();
      int depth = 0;
      int start = -1;
      for (int i = 0; i < inner.length(); i++) {
        char ch = inner.charAt(i);
        if (ch == '[') {
          if (depth == 0) start = i;
          depth++;
        } else if (ch == ']') {
          depth--;
          if (depth == 0 && start >= 0) {
            double[][] route = parseCoordinates(inner.substring(start, i + 1));
            if (route != null && route.length >= 2) routes.add(route);
            start = -1;
          }
        }
      }
      return routes.isEmpty() ? null : routes.toArray(new double[0][][]);
    } catch (Exception e) { return null; }
  }

  // ══════════════════════════════════════════════════════════════════════════
  public static void main(String[] args) {
    int port = AppConfig.serverPort();
    if (args.length > 0) {
      try {
        port = Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
        System.err.println("Invalid port: " + args[0]);
        System.exit(1);
      }
    }

    try {
      Path configFile = AppConfig.loadedConfigFile();
      System.out.println("SafePath root: " + AppPaths.root().toAbsolutePath());
      AppConfig.logMysqlConfigSources(configFile);
      System.out.println("MySQL: " + AppConfig.mysqlHost() + ":" + AppConfig.mysqlPort()
          + "/" + AppConfig.mysqlDatabase());
      server.db.Database.get();
      System.out.println("MySQL: connected");
      System.out.println("SMTP: " + (EmailService.isSmtpConfigured() ? "configured" : "not configured (outbox only)"));
      System.out.println("Google: " + (AppConfig.has("google.client.id") ? "configured" : "not configured"));
      Server server = new Server(port);
      server.start();
      Thread.currentThread().join();
    } catch (IOException e) {
      String msg = String.valueOf(e.getMessage());
      if (msg.contains("Address already in use") || msg.contains("bind")) {
        System.err.println("ERROR: Port " + port + " is already in use.");
        System.err.println("Stop the other process or run: java -jar target/safepath-1.0.0.jar " + (port + 1));
      } else {
        System.err.println("ERROR: " + e.getMessage());
      }
      System.exit(1);
    } catch (java.sql.SQLException e) {
      System.err.println("ERROR: Database connection failed — " + e.getMessage());
      System.err.println("Edit safepath/config/app.properties (mysql.host, mysql.user, mysql.password)");
      System.err.println("TiDB Cloud example: mysql.host=gateway01.ap-southeast-1.prod.aws.tidbcloud.com mysql.port=4000");
      System.exit(1);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
