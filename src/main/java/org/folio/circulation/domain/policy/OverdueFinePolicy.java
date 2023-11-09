package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class OverdueFinePolicy extends Policy {
  private final OverdueFinePolicyFineInfo fineInfo;
  private final OverdueFinePolicyLimitInfo limitInfo;
  private final RemindersPolicy remindersPolicy;
  private final Flags flags;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private OverdueFinePolicy(String id, String name, OverdueFinePolicyFineInfo fineInfo,
                            OverdueFinePolicyLimitInfo limitInfo, RemindersPolicy remindersPolicy, Flags flags) {
    super(id, name);
    this.fineInfo = fineInfo;
    this.limitInfo = limitInfo;
    this.remindersPolicy = remindersPolicy;
    this.flags = flags;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    log.debug("from:: parameters json: {}", json);
    String intervalId = getNestedStringProperty(json, "overdueFine", "intervalId");
    String recallIntervalId = getNestedStringProperty(json, "overdueRecallFine", "intervalId");

    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      new OverdueFinePolicyFineInfo(
        getBigDecimalProperty(getObjectProperty(json, "overdueFine"), "quantity"),
        intervalId == null ? null : OverdueFineInterval.fromValue(intervalId),
        getBigDecimalProperty(getObjectProperty(json, "overdueRecallFine"), "quantity"),
        recallIntervalId == null ? null : OverdueFineInterval.fromValue(recallIntervalId)
      ),
      new OverdueFinePolicyLimitInfo(getBigDecimalProperty(json, "maxOverdueFine"),
        getBigDecimalProperty(json, "maxOverdueRecallFine")),
      RemindersPolicy.from(getObjectProperty(json, "reminderFeesPolicy")),
      new Flags(
        getBooleanProperty(json, "gracePeriodRecall"),
        getBooleanProperty(json, "countClosed"),
        getBooleanProperty(json, "forgiveOverdueFine")));
  }

  public OverdueFineCalculationParameters getCalculationParameters(boolean dueDateChangedByRecall) {
    log.debug("getCalculationParameters:: parameters dueDateChangedByRecall: {}",
      dueDateChangedByRecall);
    if (dueDateChangedByRecall) {
      log.info("dueDateChangedByRecalld:: dueDate was changed by recall");
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
    log.info("getCalculationParameters:: result: null");
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

  public boolean isReminderFeesPolicy() {
    return remindersPolicy.hasReminderSchedule();
  }

  public RemindersPolicy getRemindersPolicy() {
    return remindersPolicy;
  }

  private static class UnknownOverdueFinePolicy extends OverdueFinePolicy {
    UnknownOverdueFinePolicy(String id) {
      super(id, null, new OverdueFinePolicyFineInfo(null, null, null, null),
        new OverdueFinePolicyLimitInfo(null, null),
        RemindersPolicy.from(null),
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
