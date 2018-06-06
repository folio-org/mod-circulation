package api.support.builders;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class FixedDueDateSchedule {
  final DateTime from;
  final DateTime to;
  public final DateTime due;

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
      new DateTime(year, 1, 1, 0, 0, 0, DateTimeZone.UTC),
      new DateTime(year, 12, 31, 23, 59, 59, DateTimeZone.UTC));
  }

  public static FixedDueDateSchedule wholeMonth(int year, int month) {
    final DateTime firstOfMonth = new DateTime(year, month, 1, 0, 0, 0, DateTimeZone.UTC);

    final DateTime lastOfMonth = firstOfMonth
      .withDayOfMonth(firstOfMonth.dayOfMonth().getMaximumValue())
      .withHourOfDay(23)
      .withMinuteOfHour(59)
      .withSecondOfMinute(59);

    return dueAtEnd(firstOfMonth, lastOfMonth);
  }

  public static FixedDueDateSchedule wholeMonth(int year, int month, DateTime dueDate) {
    final DateTime firstOfMonth = new DateTime(year, month, 1, 0, 0, 0, DateTimeZone.UTC);

    final DateTime lastOfMonth = firstOfMonth
      .withDayOfMonth(firstOfMonth.dayOfMonth().getMaximumValue())
      .withHourOfDay(23)
      .withMinuteOfHour(59)
      .withSecondOfMinute(59);

    return new FixedDueDateSchedule(firstOfMonth, lastOfMonth, dueDate);
  }

  public static FixedDueDateSchedule todayOnly() {
    return forDay(DateTime.now(DateTimeZone.UTC));
  }

  public static FixedDueDateSchedule yesterdayOnly() {
    return forDay(DateTime.now(DateTimeZone.UTC).minusDays(1));
  }

  private static FixedDueDateSchedule forDay(DateTime day) {
    final DateTime beginningOfDay = day.withTimeAtStartOfDay();

    final DateTime endOfDay = beginningOfDay
      .withHourOfDay(23)
      .withMinuteOfHour(59)
      .withSecondOfMinute(59);

    return new FixedDueDateSchedule(beginningOfDay, endOfDay, endOfDay);
  }
}
