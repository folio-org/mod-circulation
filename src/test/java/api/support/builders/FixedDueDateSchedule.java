package api.support.builders;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfMonth;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfYear;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfMonth;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfYear;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class FixedDueDateSchedule {
  final ZonedDateTime from;
  final ZonedDateTime to;
  public final ZonedDateTime due;

  private static FixedDueDateSchedule dueAtEnd(ZonedDateTime from, ZonedDateTime to) {
    return new FixedDueDateSchedule(from, to, to);
  }

  public FixedDueDateSchedule(ZonedDateTime from, ZonedDateTime to, ZonedDateTime due) {
    this.from = from;
    this.to = to;
    this.due = due;
  }

  public static FixedDueDateSchedule wholeYear(int year) {
    final LocalDate date = LocalDate.of(year, 1, 1);

    return dueAtEnd(atStartOfYear(date, UTC), atEndOfYear(date, UTC));
  }

  public static FixedDueDateSchedule wholeMonth(int year, int month) {
    final LocalDate date = LocalDate.of(year, month, 1);

    return dueAtEnd(atStartOfMonth(date, UTC), atEndOfMonth(date, UTC));
  }

  public static FixedDueDateSchedule wholeMonth(int year, int month, ZonedDateTime dueDate) {
    final LocalDate date = LocalDate.of(year, month, 1);

    return new FixedDueDateSchedule(atStartOfMonth(date, UTC),
      atEndOfMonth(date, UTC), dueDate);
  }

  public static FixedDueDateSchedule todayOnly() {
    return forDay(getZonedDateTime());
  }

  public static FixedDueDateSchedule yesterdayOnly() {
    return forDay(getZonedDateTime().minusDays(1));
  }

  public static FixedDueDateSchedule forDay(ZonedDateTime day) {
    return new FixedDueDateSchedule(atStartOfDay(day, UTC),
      atEndOfDay(day, UTC), atEndOfDay(day, UTC));
  }
}
