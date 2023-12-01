package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.apache.commons.lang.WordUtils;
import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


import static java.util.Collections.emptyMap;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.json.JsonPropertyFetcher.*;
import static org.folio.circulation.support.results.Result.*;
import static org.folio.circulation.support.results.Result.succeeded;

public class RemindersPolicy {

  public final static String REMINDER_SCHEDULE = "reminderSchedule";
  public final static String COUNT_CLOSED = "countClosed";
  public final static String IGNORE_GRACE_PERIOD_RECALL = "ignoreGracePeriodRecall";
  public final static String IGNORE_GRACE_PERIOD_HOLDS = "ignoreGracePeriodHolds";
  public final static String ALLOW_RENEWAL_OF_ITEMS_WITH_REMINDER_FEES = "allowRenewalOfItemsWithReminderFees";
  public final static String CLEAR_PATRON_BLOCK_WHEN_PAID = "clearPatronBlockWhenPaid";
  private final Sequence sequence;
  @Getter
  private Boolean countClosed = true; // Means "can send reminder upon closed day"
  @Getter
  private Boolean ignoreGracePeriodRecall = true;
  @Getter
  private Boolean ignoreGracePeriodHolds = true;
  @Getter
  private Boolean allowRenewalOfItemsWithReminderFees = true;
  @Getter
  private Boolean clearPatronBlockWhenPaid = true;

  public static RemindersPolicy from (JsonObject json) {
    Sequence sequence =
      Sequence.from(getArrayProperty(json, REMINDER_SCHEDULE));
    return new RemindersPolicy(sequence)
      .withCountClosed(getBooleanProperty(json,COUNT_CLOSED))
      .withAllowRenewalOfItemsWithReminderFees(getBooleanProperty(json,ALLOW_RENEWAL_OF_ITEMS_WITH_REMINDER_FEES))
      .withIgnoreGracePeriodRecall(getBooleanProperty(json,IGNORE_GRACE_PERIOD_RECALL))
      .withIgnoreGracePeriodHolds(getBooleanProperty(json,IGNORE_GRACE_PERIOD_HOLDS))
      .withClearPatronBlockWhenPaid(getBooleanProperty(json,CLEAR_PATRON_BLOCK_WHEN_PAID));
  }

  public RemindersPolicy withCountClosed(Boolean countClosed) {
    this.countClosed = countClosed;
    return this;
  }

  public RemindersPolicy withIgnoreGracePeriodRecall(Boolean ignoreGracePeriodRecall) {
    this.ignoreGracePeriodRecall = ignoreGracePeriodRecall;
    return this;
  }

  public RemindersPolicy withIgnoreGracePeriodHolds(Boolean ignoreGracePeriodHolds) {
    this.ignoreGracePeriodHolds = ignoreGracePeriodHolds;
    return this;
  }

  public RemindersPolicy withAllowRenewalOfItemsWithReminderFees(Boolean allowRenewalOfItemsWithReminderFees) {
    this.allowRenewalOfItemsWithReminderFees = allowRenewalOfItemsWithReminderFees;
    return this;
  }

  public RemindersPolicy withClearPatronBlockWhenPaid(Boolean clearPatronBlockWhenPaid) {
    this.clearPatronBlockWhenPaid = clearPatronBlockWhenPaid;
    return this;
  }

  public boolean canScheduleReminderUponClosedDay() {
    return countClosed;
  }

  private RemindersPolicy(Sequence sequence) {
    this.sequence = sequence.withPolicy(this);

  }

  public boolean hasReminderSchedule () {
    return !sequence.isEmpty();
  }

  public Sequence getReminderSchedule() {
    return sequence.withPolicy(this);
  }

  public ReminderConfig getReminderSequenceEntry (int reminderNumber) {
    return sequence.getEntry(reminderNumber).withPolicy(this);
  }

  public ReminderConfig getFirstReminder () {
    return getReminderSequenceEntry(1);
  }

  @Getter
  public static class Sequence {
    private RemindersPolicy policy;
    private final Map<Integer, ReminderConfig> reminderSequenceEntries;

    private Sequence() {
      reminderSequenceEntries = new HashMap<>();
    }

    public Sequence withPolicy(RemindersPolicy policy) {
      this.policy = policy;
      return this;
    }

    /**
     * Creates schedule of reminder entries ordered by sequence numbers starting with 1 (not zero)
     * @param remindersArray JsonArray 'reminderSchedule' from the reminder fees policy
     */
    public static Sequence from (JsonArray remindersArray) {
      Sequence sequence = new Sequence();
      for (int i = 1; i<=remindersArray.size(); i++) {
        sequence.reminderSequenceEntries.put(
          i, ReminderConfig.from(i, remindersArray.getJsonObject(i-1)));
      }
      return sequence;
    }

    public boolean isEmpty() {
      return reminderSequenceEntries.isEmpty();
    }

    public ReminderConfig getEntry(int sequenceNumber) {
      if (reminderSequenceEntries.size() >= sequenceNumber) {
        return reminderSequenceEntries.get(sequenceNumber).withPolicy(policy);
      } else {
        return null;
      }
    }

    public boolean hasEntryAfter(int sequenceNumber) {
      return reminderSequenceEntries.size() >= sequenceNumber+1;
    }

    public ReminderConfig getEntryAfter(int sequenceNumber) {
      return hasEntryAfter(sequenceNumber) ? getEntry(sequenceNumber+1) : null;
    }

  }

  @Getter
  public static class ReminderConfig {
    private static final String INTERVAL = "interval";
    private static final String TIME_UNIT_ID = "timeUnitId";
    private static final String REMINDER_FEE = "reminderFee";
    private static final String NOTICE_FORMAT = "noticeFormat";
    private static final String NOTICE_TEMPLATE_ID = "noticeTemplateId";
    private static final String BLOCK_TEMPLATE_ID = "blockTemplateId";

    private final int sequenceNumber;
    private final Period period;
    private final BigDecimal reminderFee;
    private final String noticeFormat;
    private final String noticeTemplateId;
    private final String blockTemplateId;
    private RemindersPolicy policy;

    public ReminderConfig(
      int sequenceNumber,
      Period period,
      BigDecimal reminderFee,
      String noticeFormat,
      String noticeTemplateId,
      String blockTemplateId) {
      this.sequenceNumber = sequenceNumber;
      this.period = period;
      this.reminderFee = reminderFee;
      this.noticeFormat = noticeFormat;
      this.noticeTemplateId= noticeTemplateId;
      this.blockTemplateId = blockTemplateId;
    }
    public static ReminderConfig from (int sequenceNumber, JsonObject entry) {
      Period period = Period.from(
        entry.getInteger(INTERVAL),
        normalizeTimeUnit(entry.getString(TIME_UNIT_ID)));
      BigDecimal fee = getBigDecimalProperty(entry,REMINDER_FEE);
      return new ReminderConfig(
        sequenceNumber, period, fee,
        entry.getString(NOTICE_FORMAT),
        entry.getString(NOTICE_TEMPLATE_ID),
        entry.getString(BLOCK_TEMPLATE_ID));
    }

    public ReminderConfig withPolicy(RemindersPolicy policy) {
      this.policy = policy;
      return this;
    }

    public NoticeFormat getNoticeFormat () {
      return NoticeFormat.from(noticeFormat);
    }

    public boolean hasZeroFee () {
      return reminderFee.doubleValue() == 0.0;
    }

    /**
     * Normalizes "HOUR", "HOURS", "hour", "hours" to "Hours"
     */
    private static String normalizeTimeUnit (String timeUnitId) {
      String capitalized = WordUtils.capitalizeFully(timeUnitId);
      return (capitalized.endsWith("s") ? capitalized : capitalized + "s");
    }


    public CompletableFuture<Result<ZonedDateTime>> nextNoticeDueOn(
      ZonedDateTime offsetDate, ZoneId tenantTimeZone, String servicePointId, CalendarRepository calendars) {
      ZonedDateTime scheduledForDateTime = getPeriod().plusDate(offsetDate);
      if (policy.canScheduleReminderUponClosedDay()) {
        return ofAsync(scheduledForDateTime);
      } else {
        return firstOpenDayFrom(scheduledForDateTime, tenantTimeZone, servicePointId, calendars);
      }
    }

    private CompletableFuture<Result<ZonedDateTime>> firstOpenDayFrom(
      ZonedDateTime scheduledDate, ZoneId tenantTimeZone, String servicePointId, CalendarRepository calendars)  {
      LocalDate scheduledDayInTenantTimeZone = scheduledDate.withZoneSameInstant(tenantTimeZone).toLocalDate();
      return calendars.lookupOpeningDays(scheduledDayInTenantTimeZone, servicePointId)
        .thenApply(adjacentOpeningDaysResult -> countDaysUntilOpen(adjacentOpeningDaysResult.value()))
        .thenCompose(daysUntilOpen -> ofAsync(scheduledDate.plusDays(daysUntilOpen.value())));
    }

    private Result<Long> countDaysUntilOpen(AdjacentOpeningDays openingDays) {
      if (openingDays.getRequestedDay().isOpen()) {
        return succeeded(0L);
      } else {
        OpeningDay nextDay = openingDays.getNextDay();
        if (!nextDay.isOpen()) {
          return failed(singleValidationError(
            new ValidationError("No calendar time table found for requested date", emptyMap())
          ));
        }
        return succeeded(ChronoUnit.DAYS.between(openingDays.getRequestedDay().getDate(),nextDay.getDate()));
      }
    }

  }
}
