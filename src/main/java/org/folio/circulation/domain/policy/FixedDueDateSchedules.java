package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.JsonArrayHelper;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

class FixedDueDateSchedules extends JsonObject {
  FixedDueDateSchedules(JsonObject representation) {
    super(representation.getMap());
  }

  Optional<DateTime> findDueDateFor(DateTime date) {
    return findScheduleFor(date)
      .map(this::getDueDate);
  }

  private Optional<JsonObject> findScheduleFor(DateTime date) {
    final List<JsonObject> schedules = JsonArrayHelper.toList(
      getJsonArray("schedules"));

    return schedules
      .stream()
      .filter(isWithin(date))
      .findFirst();
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
}
