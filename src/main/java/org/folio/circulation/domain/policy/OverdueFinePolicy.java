package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDoubleProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import org.apache.commons.lang3.ObjectUtils;

import io.vertx.core.json.JsonObject;

public class OverdueFinePolicy extends Policy {
  private final OverdueFinePolicyFineInfo fineInfo;
  private final OverdueFinePolicyLimitInfo limitInfo;
  private final Flags flags;

  private OverdueFinePolicy(String id, String name, OverdueFinePolicyFineInfo fineInfo,
    OverdueFinePolicyLimitInfo limitInfo, Flags flags) {
    super(id, name);
    this.fineInfo = fineInfo;
    this.limitInfo = limitInfo;
    this.flags = flags;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    String intervalId = getNestedStringProperty(json, "overdueFine", "intervalId");
    String recallIntervalId = getNestedStringProperty(json, "overdueRecallFine", "intervalId");

    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      new OverdueFinePolicyFineInfo(
        getDoubleProperty(getObjectProperty(json, "overdueFine"), "quantity", null),
        intervalId == null ? null : OverdueFineInterval.fromValue(intervalId),
        getDoubleProperty(getObjectProperty(json, "overdueRecallFine"), "quantity", null),
        recallIntervalId == null ? null : OverdueFineInterval.fromValue(recallIntervalId)
      ),
      new OverdueFinePolicyLimitInfo(getDoubleProperty(json, "maxOverdueFine", null),
        getDoubleProperty(json, "maxOverdueRecallFine", null)),
      new Flags(
        getBooleanProperty(json, "gracePeriodRecall"),
        getBooleanProperty(json, "countClosed"),
        getBooleanProperty(json, "forgiveOverdueFine")));
  }

  public OverdueFineCalculationParameters getCalculationParameters(boolean dueDateChangedByRecall) {
    if (dueDateChangedByRecall) {
      if (ObjectUtils.allNotNull(fineInfo.getOverdueRecallFine(),
        fineInfo.getOverdueRecallFineInterval(), limitInfo.getMaxOverdueRecallFine())) {

        return new OverdueFineCalculationParameters(fineInfo.getOverdueRecallFine(),
          fineInfo.getOverdueRecallFineInterval(), limitInfo.getMaxOverdueRecallFine());
      }
    }
    else {
      if (ObjectUtils.allNotNull(fineInfo.getOverdueFine(), fineInfo.getOverdueFineInterval(),
        limitInfo.getMaxOverdueFine())) {

        return new OverdueFineCalculationParameters(fineInfo.getOverdueFine(),
          fineInfo.getOverdueFineInterval(), limitInfo.getMaxOverdueFine());
      }
    }
    return null;
  }

  public OverdueFinePolicyFineInfo getFineInfo() {
    return fineInfo;
  }

  public OverdueFinePolicyLimitInfo getLimitInfo() {
    return limitInfo;
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
      super(id, null, new OverdueFinePolicyFineInfo(null, null, null, null),
        new OverdueFinePolicyLimitInfo(null, null),
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
