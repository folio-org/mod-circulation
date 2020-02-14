package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.json.JsonObject;

public class OverdueFinePolicy extends Policy {
  private final Double overdueFine;
  private final OverdueFineInterval overdueFineInterval;
  private final Double maxOverdueFine;
  private final Double maxOverdueRecallFine;
  private final Boolean ignoreGracePeriodForRecalls;
  private final Boolean countPeriodsWhenServicePointIsClosed;

  private OverdueFinePolicy(String id, String name, Double overdueFine,
    OverdueFineInterval overdueFineInterval, Double maxOverdueFine, Double maxOverdueRecallFine,
    Boolean ignoreGracePeriodForRecalls, Boolean countPeriodsWhenServicePointIsClosed) {
    super(id, name);
    this.overdueFine = overdueFine;
    this.overdueFineInterval = overdueFineInterval;
    this.maxOverdueFine = maxOverdueFine;
    this.maxOverdueRecallFine = maxOverdueRecallFine;
    this.ignoreGracePeriodForRecalls = ignoreGracePeriodForRecalls;
    this.countPeriodsWhenServicePointIsClosed = countPeriodsWhenServicePointIsClosed;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      json.getJsonObject("overdueFine").getDouble("quantity"),
      OverdueFineInterval.fromValue(json.getJsonObject("overdueFine").getString("intervalId")),
      json.getDouble("maxOverdueFine"),
      json.getDouble("maxOverdueRecallFine"),
      json.getBoolean("gracePeriodRecall"),
      json.getBoolean("countClosed"));
  }

  public Double getOverdueFine() {
    return overdueFine;
  }

  public OverdueFineInterval getOverdueFineInterval() {
    return overdueFineInterval;
  }

  public Double getMaxOverdueFine() {
    return maxOverdueFine;
  }

  public Double getMaxOverdueRecallFine() {
    return maxOverdueRecallFine;
  }

  public Boolean getIgnoreGracePeriodForRecalls() {
    return ignoreGracePeriodForRecalls;
  }

  public Boolean getCountPeriodsWhenServicePointIsClosed() {
    return countPeriodsWhenServicePointIsClosed;
  }

  public static OverdueFinePolicy unknown(String id) {
    return new OverdueFinePolicy.UnknownOverdueFinePolicy(id);
  }

  private static class UnknownOverdueFinePolicy extends OverdueFinePolicy {
    UnknownOverdueFinePolicy(String id) {
      super(id, null, null, null, null, null, null, null);
    }
  }

  public enum OverdueFineInterval {
    MINUTE("minute"),
    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    private final String value;

    private static final Map<String, OverdueFineInterval> CONSTANTS =
      new HashMap<>();

    static {
      for (OverdueFinePolicy.OverdueFineInterval c: values()) {
        CONSTANTS.put(c.value, c);
      }
    }

    OverdueFineInterval(String value) {
      this.value = value;
    }

    public static OverdueFinePolicy.OverdueFineInterval fromValue(String value) {
      OverdueFinePolicy.OverdueFineInterval constant = CONSTANTS.get(value);
      if (constant == null) {
        throw new IllegalArgumentException(value);
      } else {
        return constant;
      }
    }
  }
}
