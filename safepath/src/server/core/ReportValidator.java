package server.core;

/** AI-style anomaly checks before community reports affect routing. */
public final class ReportValidator {

  public static final int MAX_REPORTS_PER_DAY = 5;
  public static final int MAX_REPORTS_PER_HOUR = 3;
  public static final double MAX_JUMP_KM = 5.0;

  private ReportValidator() {}

  public static class Result {
    public final boolean allowed;
    public final boolean affectsRouting;
    public final String message;
    public final String anomalyFlags;

    public Result(boolean allowed, boolean affectsRouting, String message, String anomalyFlags) {
      this.allowed = allowed;
      this.affectsRouting = affectsRouting;
      this.message = message;
      this.anomalyFlags = anomalyFlags;
    }
  }

  public static Result validate(int reportsToday, int reportsLastHour,
      double distFromLastKm, boolean hasLastReport) {
    if (reportsToday >= MAX_REPORTS_PER_DAY) {
      return new Result(false, false,
          "Daily report limit reached (5/day). Report reviewed tomorrow.",
          "RATE_LIMIT");
    }

    StringBuilder flags = new StringBuilder();
    boolean affectsRouting = true;

    if (reportsLastHour >= MAX_REPORTS_PER_HOUR) {
      flags.append("VELOCITY_SPIKE;");
      affectsRouting = false;
    }
    if (hasLastReport && distFromLastKm > MAX_JUMP_KM) {
      flags.append("GEO_JUMP;");
      affectsRouting = false;
    }
    if (reportsToday >= 4) {
      flags.append("HIGH_FREQUENCY;");
    }

    String flagStr = flags.length() > 0 ? flags.toString() : "CLEAN";
    String msg = affectsRouting
        ? "Report verified by AI anomaly filter — affects routing when confirmed."
        : "Report saved as unverified — needs more community confirmation before routing impact.";

    return new Result(true, affectsRouting, msg, flagStr);
  }

  public static double severityMultiplier(String severity) {
    if (severity == null) return 1.0;
    return switch (severity.toUpperCase()) {
      case "LOW" -> 0.5;
      case "MEDIUM" -> 1.0;
      case "HIGH" -> 1.5;
      case "CRITICAL" -> 2.0;
      default -> 1.0;
    };
  }
}
