package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDoubleProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
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
    String intervalId = getNestedStringProperty(json, "overdueFine", "intervalId");

    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      getDoubleProperty(getObjectProperty(json, "overdueFine"), "quantity", null),
      intervalId == null ? null : OverdueFineInterval.fromValue(intervalId),
      new OverdueFinePolicyLimitInfo(getDoubleProperty(json, "maxOverdueFine", null),
        getDoubleProperty(json, "maxOverdueRecallFine", null)),
      getBooleanProperty(json, "gracePeriodRecall"),
      getBooleanProperty(json, "countClosed"));
  }

  public Double getOverdueFine() {
    return overdueFine;
  }

  public OverdueFineInterval getOverdueFineInterval() {
    return overdueFineInterval;
  }

  public Double getMaxOverdueFine() {
    return limitInfo == null ? null : limitInfo.getMaxOverdueFine();
  }

  public Double getMaxOverdueRecallFine() {
    return limitInfo == null ? null : limitInfo.getMaxOverdueRecallFine();
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
      super(id, null, null, null, new OverdueFinePolicyLimitInfo(null, null), false, false);
    }
  }
}
