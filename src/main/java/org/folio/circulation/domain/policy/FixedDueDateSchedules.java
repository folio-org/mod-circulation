package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.DateTimeUtil;

import io.vertx.core.json.JsonObject;

public class FixedDueDateSchedules {
  private final List<JsonObject> schedules;
  private final String id;

  FixedDueDateSchedules(String id, List<JsonObject> schedules) {
    this.id = id;
    this.schedules = schedules;
  }

  public static FixedDueDateSchedules from(JsonObject representation) {
    if (representation == null) {
      return new NoFixedDueDateSchedules();
    } else {
      return new FixedDueDateSchedules(getProperty(representation, "id"),
        toList(toStream(representation, "schedules")));
    }
  }

  public Optional<ZonedDateTime> findDueDateFor(ZonedDateTime date) {
    return findScheduleFor(date).map(this::getDueDate);
  }

  private Optional<JsonObject> findScheduleFor(ZonedDateTime date) {
    return schedules
      .stream()
      .filter(isWithin(date))
      .findFirst();
  }

  private Predicate<? super JsonObject> isWithin(ZonedDateTime date) {
    return schedule -> {
      ZonedDateTime from = parseDateTime(schedule.getString("from"));
      ZonedDateTime to = parseDateTime(schedule.getString("to"));

      return DateTimeUtil.isWithinMillis(date, from, to);
    };
  }

  private ZonedDateTime getDueDate(JsonObject schedule) {
    return parseDateTime(schedule.getString("due"));
  }

  public boolean isEmpty() {
    return schedules.isEmpty();
  }

  Result<ZonedDateTime> truncateDueDate(
    ZonedDateTime dueDate,
    ZonedDateTime loanDate,
    Supplier<ValidationError> noApplicableScheduleError) {

    return findDueDateFor(loanDate)
      .map(limit -> earliest(dueDate, limit))
      .map(Result::succeeded)
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
