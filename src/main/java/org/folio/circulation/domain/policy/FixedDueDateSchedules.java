package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import io.vertx.core.json.JsonObject;

public class FixedDueDateSchedules {
  private final List<JsonObject> schedules;

  FixedDueDateSchedules(List<JsonObject> schedules) {
    this.schedules = schedules;
  }

  static FixedDueDateSchedules from(JsonObject representation) {
    if (representation == null) {
      return new NoFixedDueDateSchedules();
    } else {
      return new FixedDueDateSchedules(JsonArrayHelper.toList(
        representation.getJsonArray("schedules")));
    }
  }

  public List<JsonObject> getSchedules() {
    return schedules;
  }

  public Optional<DateTime> findDueDateFor(DateTime date) {
    return findScheduleFor(date)
      .map(this::getDueDate);
  }

  public Optional<DateTime> findEarliestDueDateFor(DateTime date) {
    return findEarliestScheduleFor(date)
      .map(this::getDueDate);
  }

  private Optional<JsonObject> findScheduleFor(DateTime date) {
    return schedules
      .stream()
      .filter(isWithin(date))
      .findFirst();
  }

  private Optional<JsonObject> findEarliestScheduleFor(DateTime date) {
    return schedules
      .stream()
      .filter(isWithin(date))
      .min(new ScheduleDueDateComparator());
  }

  private Predicate<? super JsonObject> isWithin(DateTime date) {
    return schedule -> {
      DateTime from = DateTime.parse(schedule.getString("from"));
      DateTime to = DateTime.parse(schedule.getString("to"));

      return date.isAfter(from) && date.isBefore(to);
    };
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
}
