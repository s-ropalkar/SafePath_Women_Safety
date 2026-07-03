package server.core;

import java.util.ArrayList;
import java.util.List;

/** Explainable AI (XAI) safety analysis for a location or route. */
public class SafetyInsight {

  public final double safetyScore;
  public final double confidence;
  public final double predictedScore30Min;
  public final String trend;
  public final String status;
  public final List<String> xaiFactors;

  public SafetyInsight(double safetyScore, double confidence, double predictedScore30Min,
      String trend, String status, List<String> xaiFactors) {
    this.safetyScore = safetyScore;
    this.confidence = confidence;
    this.predictedScore30Min = predictedScore30Min;
    this.trend = trend;
    this.status = status;
    this.xaiFactors = xaiFactors != null ? xaiFactors : List.of();
  }
}
