package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class OverdueFinePolicy extends Policy {
  private final Double overdueFine;
  private final OverdueFineInterval overdueFineInterval;
  private final OverdueFinePolicyLimitInfo limitInfo;
  private final Boolean ignoreGracePeriodForRecalls;
  private final Boolean countPeriodsWhenServicePointIsClosed;

  private OverdueFinePolicy(String id, String name, Double overdueFine,
    OverdueFineInterval overdueFineInterval, OverdueFinePolicyLimitInfo limitInfo,
    Boolean ignoreGracePeriodForRecalls, Boolean countPeriodsWhenServicePointIsClosed) {
    super(id, name);
    this.overdueFine = overdueFine;
    this.overdueFineInterval = overdueFineInterval;
    this.limitInfo = limitInfo;
    this.ignoreGracePeriodForRecalls = ignoreGracePeriodForRecalls;
    this.countPeriodsWhenServicePointIsClosed = countPeriodsWhenServicePointIsClosed;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      json.getJsonObject("overdueFine").getDouble("quantity"),
      OverdueFineInterval.fromValue(json.getJsonObject("overdueFine").getString("intervalId")),
      new OverdueFinePolicyLimitInfo(json.getDouble("maxOverdueFine"),
        json.getDouble("maxOverdueRecallFine")),
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
    return limitInfo.getMaxOverdueFine();
  }

  public Double getMaxOverdueRecallFine() {
    return limitInfo.getMaxOverdueRecallFine();
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
      super(id, null, null, null, null, null, null);
    }
  }
}
