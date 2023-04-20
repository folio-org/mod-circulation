package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.DateTimeUtil;

import io.vertx.core.json.JsonObject;

public class FixedDueDateSchedules {
  private final List<JsonObject> schedules;
  private final String id;
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  FixedDueDateSchedules(String id, List<JsonObject> schedules) {
    this.id = id;
    this.schedules = schedules;
  }

  public static FixedDueDateSchedules from(JsonObject representation) {
    log.debug("from:: parameters representation: {}", representation);
    if (representation == null) {
      log.info("from:: representation is null");
      return new NoFixedDueDateSchedules();
    } else {
      return new FixedDueDateSchedules(getProperty(representation, "id"),
        toList(toStream(representation, "schedules")));
    }
  }

  public Optional<ZonedDateTime> findDueDateFor(ZonedDateTime date) {
    log.debug("findDueDateFor:: parameters date: {}", date);
    return findScheduleFor(date)
      .map(this::getDueDate)
      .map(dateTime -> {
        log.info("findDueDateFor:: result: {}", dateTime);
        return dateTime;
      });
  }

  private Optional<JsonObject> findScheduleFor(ZonedDateTime date) {
    log.debug("findScheduleFor:: parameters date: {}", date);
    return schedules
      .stream()
      .filter(isWithin(date))
      .findFirst();
  }

  private Predicate<? super JsonObject> isWithin(ZonedDateTime date) {
    log.debug("isWithin:: parameters date: {}", date);
    return schedule -> {
      ZonedDateTime from = parseDateTime(schedule.getString("from"));
      ZonedDateTime to = parseDateTime(schedule.getString("to"));

      return DateTimeUtil.isWithinMillis(date, from, to);
    };
  }

  private ZonedDateTime getDueDate(JsonObject schedule) {
    log.debug("getDueDate:: parameters schedule: {}", schedule);
    return parseDateTime(schedule.getString("due"));
  }

  public boolean isEmpty() {
    return schedules.isEmpty();
  }

  Result<ZonedDateTime> truncateDueDate(
    ZonedDateTime dueDate,
    ZonedDateTime loanDate,
    Supplier<ValidationError> noApplicableScheduleError) {

    log.debug("truncateDueDate:: parameters dueDate: {}, loanDate: {}", dueDate, loanDate);

    return findDueDateFor(loanDate)
      .map(limit -> earliest(dueDate, limit))
      .map(dateTime -> {
        log.info("truncateDueDate:: result: {}", dateTime);
        return succeeded(dateTime);
      })
      .orElseGet(() -> failedValidation(noApplicableScheduleError.get()));
  }

  private ZonedDateTime earliest(ZonedDateTime rollingDueDate, ZonedDateTime limit) {
    return isBeforeMillis(limit, rollingDueDate)
      ? limit
      : rollingDueDate;
  }

  public String getId() {
    return id;
  }
}
