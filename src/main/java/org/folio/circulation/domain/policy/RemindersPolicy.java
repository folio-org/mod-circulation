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

@Getter
public class RemindersPolicy {

  public static final String REMINDER_SCHEDULE = "reminderSchedule";
  public static final String COUNT_CLOSED = "countClosed";
  public static final String IGNORE_GRACE_PERIOD_RECALL = "ignoreGracePeriodRecall";
  public static final String IGNORE_GRACE_PERIOD_HOLDS = "ignoreGracePeriodHolds";
  public static final String ALLOW_RENEWAL_OF_ITEMS_WITH_REMINDER_FEES = "allowRenewalOfItemsWithReminderFees";
  public static final String CLEAR_PATRON_BLOCK_WHEN_PAID = "clearPatronBlockWhenPaid";

  private final Schedule schedule;
  @Getter
  private final Boolean countClosed; // Means "can make reminder due on closed day"
  @Getter
  private final Boolean ignoreGracePeriodRecall;
  @Getter
  private final Boolean ignoreGracePeriodHolds;
  @Getter
  private final Boolean allowRenewalOfItemsWithReminderFees;
  @Getter
  private final Boolean clearPatronBlockWhenPaid;

  public RemindersPolicy (JsonObject reminderFeesPolicy) {
    this.schedule = new Schedule(this, getArrayProperty(reminderFeesPolicy, REMINDER_SCHEDULE));
    this.countClosed = getBooleanProperty(reminderFeesPolicy,COUNT_CLOSED);
    this.ignoreGracePeriodRecall = getBooleanProperty(reminderFeesPolicy,ALLOW_RENEWAL_OF_ITEMS_WITH_REMINDER_FEES);
    this.ignoreGracePeriodHolds = getBooleanProperty(reminderFeesPolicy,IGNORE_GRACE_PERIOD_RECALL);
    this.allowRenewalOfItemsWithReminderFees = getBooleanProperty(reminderFeesPolicy,IGNORE_GRACE_PERIOD_HOLDS);
    this.clearPatronBlockWhenPaid = getBooleanProperty(reminderFeesPolicy,CLEAR_PATRON_BLOCK_WHEN_PAID);
  }

  public boolean canScheduleReminderUponClosedDay() {
    return countClosed;
  }

  public boolean hasReminderSchedule () {
    return !schedule.isEmpty();
  }

  public ReminderConfig getFirstReminder () {
    return schedule.getEntry(1);
  }

  public ReminderConfig getNextReminderAfter(int sequenceNumber) {
    return schedule.getEntryAfter(sequenceNumber);
  }

  private static class Schedule {
    private final Map<Integer, ReminderConfig> reminderSequenceEntries;

    /**
     * Creates schedule of reminder entries ordered by sequence numbers starting with 1 (not zero)
     * @param remindersArray JsonArray 'reminderSchedule' from the reminder fees policy
     */
    private Schedule(RemindersPolicy policy, JsonArray remindersArray) {
      reminderSequenceEntries = new HashMap<>();
      for (int i = 1; i<=remindersArray.size(); i++) {
        reminderSequenceEntries.put(
          i, new ReminderConfig(i, remindersArray.getJsonObject(i-1)).withPolicy(policy));
      }
    }

    private boolean isEmpty() {
      return reminderSequenceEntries.isEmpty();
    }

    private ReminderConfig getEntry(int sequenceNumber) {
      if (reminderSequenceEntries.size() >= sequenceNumber) {
        return reminderSequenceEntries.get(sequenceNumber);
      } else {
        return null;
      }
    }

    private boolean hasEntryAfter(int sequenceNumber) {
      return reminderSequenceEntries.size() >= sequenceNumber+1;
    }

    private ReminderConfig getEntryAfter(int sequenceNumber) {
      return hasEntryAfter(sequenceNumber) ? getEntry(sequenceNumber+1) : null;
    }

  }

  /**
   *  Represents single entry in a sequence of reminder configurations.
   */
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

    private ReminderConfig (int sequenceNumber, JsonObject entry) {
      this.sequenceNumber = sequenceNumber;
      this.period = Period.from(
        entry.getInteger(INTERVAL),
        normalizeTimeUnit(entry.getString(TIME_UNIT_ID)));
      this.reminderFee = getBigDecimalProperty(entry,REMINDER_FEE);
      this.noticeFormat =  entry.getString(NOTICE_FORMAT);
      this.noticeTemplateId= entry.getString(NOTICE_TEMPLATE_ID);
      this.blockTemplateId = entry.getString(BLOCK_TEMPLATE_ID);
    }

    private ReminderConfig withPolicy(RemindersPolicy policy) {
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
     * Calculates when the next reminder will become due, potentially avoiding closed days depending on the closed days setting.
     * Takes a date in UTC, returns the result in UTC, and retains the time part of the offset date.
     *
     * @param offsetDate The loan due date/time, or the date/time of the most recent reminder, in UTC
     * @param tenantTimeZone The zone ID for checking the dates in the right timezone
     * @param servicePointId For retrieving the calendar with open/closed days
     * @param calendars Access to stored calendars
     * @return The resulting date/time in UTC.
     */
    public CompletableFuture<Result<ZonedDateTime>> nextNoticeDueOn(
      ZonedDateTime offsetDate, ZoneId tenantTimeZone, String servicePointId, CalendarRepository calendars) {
      ZonedDateTime scheduledForDateTime = getPeriod().plusDate(offsetDate);
      if (policy.canScheduleReminderUponClosedDay()) {
        return ofAsync(scheduledForDateTime);
      } else {
        return getFirstComingOpenDay(scheduledForDateTime, tenantTimeZone, servicePointId, calendars);
      }
    }

    private CompletableFuture<Result<ZonedDateTime>> getFirstComingOpenDay(
      ZonedDateTime scheduledDate, ZoneId tenantTimeZone, String servicePointId, CalendarRepository calendars)  {
      LocalDate scheduledDayInTenantTimeZone = scheduledDate.withZoneSameInstant(tenantTimeZone).toLocalDate();
      return calendars.lookupOpeningDays(scheduledDayInTenantTimeZone, servicePointId)
        .thenApply(adjacentOpeningDaysResult -> daysUntilNextOpenDay(adjacentOpeningDaysResult.value()))
        .thenCompose(daysUntilOpen -> ofAsync(scheduledDate.plusDays(daysUntilOpen.value())));
    }

    private Result<Long> daysUntilNextOpenDay(AdjacentOpeningDays openingDays) {
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

    /**
     * Normalizes "HOUR", "HOURS", "hour", "hours" to "Hours"
     */
    private static String normalizeTimeUnit (String timeUnitId) {
      String capitalized = WordUtils.capitalizeFully(timeUnitId);
      return (capitalized.endsWith("s") ? capitalized : capitalized + "s");
    }


  }
}
