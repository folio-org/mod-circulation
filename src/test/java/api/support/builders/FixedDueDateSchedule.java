package api.support.builders;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class FixedDueDateSchedule {
  final DateTime from;
  final DateTime to;
  final DateTime due;

  private static FixedDueDateSchedule dueAtEnd(DateTime from, DateTime to) {
    return new FixedDueDateSchedule(from, to, to);
  }

  public FixedDueDateSchedule(DateTime from, DateTime to, DateTime due) {
    this.from = from;
    this.to = to;
    this.due = due;
  }

  public static FixedDueDateSchedule wholeYear(int year) {
    return dueAtEnd(
      new DateTime(year, 1, 31, 0, 0, 0, DateTimeZone.UTC),
      new DateTime(year, 12, 31, 23, 59, 59, DateTimeZone.UTC));
  }
}
