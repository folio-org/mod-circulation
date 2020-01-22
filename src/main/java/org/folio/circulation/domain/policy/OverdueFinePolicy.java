package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import static org.folio.circulation.support.JsonPropertyFetcher.*;

public class OverdueFinePolicy extends Policy {
  public enum OverdueFineInterval {
    MINUTE("minute"),
    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    private final String value;

    private final static Map<String, OverdueFineInterval> CONSTANTS =
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

  private Double overdueFine;
  private OverdueFineInterval overdueFineInterval;
  private Double maxOverdueFine;
  private Double maxOverdueRecallFine;
  private Boolean ignoreGracePeriodForRecalls;
  private Boolean countClosed;

  private OverdueFinePolicy(String id) {
    this(id, null, null, null);
  }

  private OverdueFinePolicy(
    String id, String name, Boolean ignoreGracePeriodForRecalls, Boolean countClosed) {
    super(id, name);
    this.ignoreGracePeriodForRecalls = ignoreGracePeriodForRecalls;
    this.countClosed = countClosed;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    OverdueFinePolicy overdueFinePolicy = new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      getBooleanProperty(json, "gracePeriodRecall"),
      getBooleanProperty(json, "countClosed")
    );

    overdueFinePolicy.setOverdueFine(json.getJsonObject("overdueFine").getDouble("quantity"));
    overdueFinePolicy.setOverdueFineInterval(OverdueFineInterval.fromValue(
      json.getJsonObject("overdueFine").getString("intervalId")));
    overdueFinePolicy.setMaxOverdueFine(json.getDouble("maxOverdueFine"));
    overdueFinePolicy.setMaxOverdueRecallFine(json.getDouble("maxOverdueRecallFine"));

    return overdueFinePolicy;
  }

  public Double getOverdueFine() {
    return overdueFine;
  }

  public void setOverdueFine(Double overdueFine) {
    this.overdueFine = overdueFine;
  }

  public OverdueFineInterval getOverdueFineInterval() {
    return overdueFineInterval;
  }

  public void setOverdueFineInterval(OverdueFineInterval overdueFineInterval) {
    this.overdueFineInterval = overdueFineInterval;
  }

  public Double getMaxOverdueFine() {
    return maxOverdueFine;
  }

  public void setMaxOverdueFine(Double maxOverdueFine) {
    this.maxOverdueFine = maxOverdueFine;
  }

  public Double getMaxOverdueRecallFine() {
    return maxOverdueRecallFine;
  }

  public void setMaxOverdueRecallFine(Double maxOverdueRecallFine) {
    this.maxOverdueRecallFine = maxOverdueRecallFine;
  }


  public Boolean getIgnoreGracePeriodForRecalls() {
    return ignoreGracePeriodForRecalls;
  }

  public Boolean getCountClosed() {
    return countClosed;
  }

  public static OverdueFinePolicy unknown(String id) {
    return new OverdueFinePolicy.UnknownOverdueFinePolicy(id);
  }

  private static class UnknownOverdueFinePolicy extends OverdueFinePolicy {
    UnknownOverdueFinePolicy(String id) {
      super(id);
    }
  }
}
