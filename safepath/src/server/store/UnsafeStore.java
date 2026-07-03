package server.store;

import server.db.Database;
import server.models.UnsafeLocation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MySQL-backed unsafe location access. Reports persist across restarts.
 * SafetyEngine reads the cached list — no in-memory spatial tree.
 */
public class UnsafeStore {

  private List<UnsafeLocation> cachedLocations = new ArrayList<>();

  public UnsafeStore() {
    refreshCache();
  }

  /** Persist a report; merge into an existing nearby location when possible. */
  public void reportUnsafe(double lat, double lng, String reason) throws SQLException {
    Database.get().addUnsafeReport(lat, lng, reason);
    refreshCache();
  }

  /** All unsafe locations from MySQL (cached after each report). */
  public List<UnsafeLocation> getUnsafeLocations() {
    return Collections.unmodifiableList(cachedLocations);
  }

  public void refreshCache() {
    try {
      cachedLocations = Database.get().findUnsafeLocations();
    } catch (SQLException e) {
      System.err.println("[UnsafeStore] Failed to load unsafe locations: " + e.getMessage());
      cachedLocations = new ArrayList<>();
    }
  }
}
