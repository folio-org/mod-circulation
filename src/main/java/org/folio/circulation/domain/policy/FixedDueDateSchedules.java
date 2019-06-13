package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import org.joda.time.LocalDate;

public class FixedDueDateSchedules {
  private final List<JsonObject> schedules;
  private final String id;

  FixedDueDateSchedules(String id, List<JsonObject> schedules) {
    this.id = id;
    this.schedules = schedules;
  }

  static FixedDueDateSchedules from(JsonObject representation) {
    if (representation == null) {
      return new NoFixedDueDateSchedules();
    } else {
      return new FixedDueDateSchedules(getProperty(representation, "id"),
        JsonArrayHelper.toList(representation.getJsonArray("schedules")));
    }
  }

  public Optional<DateTime> findDueDateFor(DateTime date) {
    return findScheduleFor(date)
      .map(this::getDueDate);
  }

  private Optional<JsonObject> findScheduleFor(DateTime date) {
    return schedules
      .stream()
      .filter(isWithin(date))
      .findFirst();
  }

  private Predicate<? super JsonObject> isWithin(DateTime date) {
    return schedule -> {
      LocalDate from = DateTime.parse(schedule.getString("from")).toLocalDate();
      LocalDate to = DateTime.parse(schedule.getString("to")).toLocalDate();

      return !isDateOutOfRange(from, to, date.toLocalDate());
    };
  }

  private boolean isDateOutOfRange(
    LocalDate from,
    LocalDate to,
    LocalDate date) {

    return date.isAfter(to) || date.isBefore(from);
  }

  private DateTime getDueDate(JsonObject schedule) {
    return DateTime.parse(schedule.getString("due"));
  }

  public boolean isEmpty() {
    return schedules.isEmpty();
  }

  Result<DateTime> truncateDueDate(
    DateTime dueDate,
    DateTime loanDate,
    Supplier<ValidationError> noApplicableScheduleError) {

    return findDueDateFor(loanDate)
      .map(limit -> earliest(dueDate, limit))
      .map(Result::succeeded)
      .orElseGet(() -> failedValidation(noApplicableScheduleError.get()));
  }

  private DateTime earliest(DateTime rollingDueDate, DateTime limit) {
    return limit.isBefore(rollingDueDate)
      ? limit
      : rollingDueDate;
  }

  public String getId() {
    return id;
  }
}
