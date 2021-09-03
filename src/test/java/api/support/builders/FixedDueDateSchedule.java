package api.support.builders;

import static org.folio.circulation.support.utils.ClockUtil.getDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfMonth;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfYear;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfMonth;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfYear;
import static org.joda.time.DateTimeZone.UTC;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

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
    final LocalDate date = new LocalDate(year, 1, 1);

    return dueAtEnd(atStartOfYear(date, UTC), atEndOfYear(date, UTC));
  }

  public static FixedDueDateSchedule wholeMonth(int year, int month) {
    final LocalDate date = new LocalDate(year, month, 1);

    return dueAtEnd(atStartOfMonth(date, UTC), atEndOfMonth(date, UTC));
  }

  public static FixedDueDateSchedule wholeMonth(int year, int month, DateTime dueDate) {
    final LocalDate date = new LocalDate(year, month, 1);

    return new FixedDueDateSchedule(atStartOfMonth(date, UTC),
      atEndOfMonth(date, UTC), dueDate);
  }

  public static FixedDueDateSchedule todayOnly() {
    return forDay(getDateTime());
  }

  public static FixedDueDateSchedule yesterdayOnly() {
    return forDay(getDateTime().minusDays(1));
  }

  public static FixedDueDateSchedule forDay(DateTime day) {
    return new FixedDueDateSchedule(atStartOfDay(day, UTC),
      atEndOfDay(day, UTC), atEndOfDay(day, UTC));
  }
}
