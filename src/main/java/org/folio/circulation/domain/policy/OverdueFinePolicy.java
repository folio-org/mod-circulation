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
  private final Flags flags;

  private OverdueFinePolicy(String id, String name, Double overdueFine,
    OverdueFineInterval overdueFineInterval, OverdueFinePolicyLimitInfo limitInfo, Flags flags) {
    super(id, name);
    this.overdueFine = overdueFine;
    this.overdueFineInterval = overdueFineInterval;
    this.limitInfo = limitInfo;
    this.flags = flags;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    String intervalId = getNestedStringProperty(json, "overdueFine", "intervalId");

    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      getDoubleProperty(getObjectProperty(json, "overdueFine"), "quantity", null),
      intervalId == null ? null : OverdueFineInterval.fromValue(intervalId),
      new OverdueFinePolicyLimitInfo(
        getDoubleProperty(json, "maxOverdueFine", null),
        getDoubleProperty(json, "maxOverdueRecallFine", null)),
      new Flags(
        getBooleanProperty(json, "gracePeriodRecall"),
        getBooleanProperty(json, "countClosed"),
        getBooleanProperty(json, "forgiveOverdueFine")));
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
    return flags.ignoreGracePeriodForRecalls;
  }

  public Boolean getCountPeriodsWhenServicePointIsClosed() {
    return flags.countPeriodsWhenServicePointIsClosed;
  }

  public Boolean getForgiveFineForRenewals() {
    return flags.forgiveFineForRenewals;
  }

  public static OverdueFinePolicy unknown(String id) {
    return new OverdueFinePolicy.UnknownOverdueFinePolicy(id);
  }

  public boolean isUnknown() {
    return this instanceof UnknownOverdueFinePolicy;
  }

  private static class UnknownOverdueFinePolicy extends OverdueFinePolicy {
    UnknownOverdueFinePolicy(String id) {
      super(id, null, null, null, new OverdueFinePolicyLimitInfo(null, null),
        new Flags(false, false, false));
    }
  }

  private static class Flags {
    private final boolean ignoreGracePeriodForRecalls;
    private final boolean countPeriodsWhenServicePointIsClosed;
    private final boolean forgiveFineForRenewals;

    public Flags(boolean ignoreGracePeriodForRecalls, boolean countPeriodsWhenServicePointIsClosed,
        boolean forgiveFineForRenewals) {
      this.ignoreGracePeriodForRecalls = ignoreGracePeriodForRecalls;
      this.countPeriodsWhenServicePointIsClosed = countPeriodsWhenServicePointIsClosed;
      this.forgiveFineForRenewals = forgiveFineForRenewals;
    }
  }
}
