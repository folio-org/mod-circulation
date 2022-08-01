package org.folio.circulation.domain;

import static api.support.fixtures.OpeningHourExamples.afternoon;
import static api.support.fixtures.OpeningHourExamples.allDay;
import static api.support.fixtures.OpeningHourExamples.morning;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class OpeningDayTest {

  @Test
  void testGetOpeningDayFromOpeningPeriodJson() {
    LocalDate date = LocalDate.parse("2020-04-08");
    OpeningDay original = createOpeningDay(false, date, UTC);
    OpeningDay fromJson = new OpeningDay(original.toJson(), UTC);

    assertOpeningDaysEqual(original, fromJson);
  }

  private OpeningDay createOpeningDay(
    boolean allDay,
    LocalDate date,
    ZoneId dateTimeZone
  ) {
    return new OpeningDay(
      allDay
        ? Collections.singletonList(allDay())
        : Arrays.asList(morning(), afternoon()),
      date,
      allDay,
      true,
      dateTimeZone
    );
  }

  private void assertOpeningDaysEqual(OpeningDay first, OpeningDay second) {
    assertEquals(first.isAllDay(), second.isAllDay());
    assertEquals(
      first.getDayWithTimeZone().toString(),
      second.getDayWithTimeZone().toString()
    );
    assertEquals(first.isOpen(), second.isOpen());
    assertEquals(first.getOpeningHour().size(), second.getOpeningHour().size());

    for (OpeningHour firstHour : first.getOpeningHour()) {
      boolean matched = false;

      for (OpeningHour secondHour : second.getOpeningHour()) {
        if (firstHour.getStartTime().equals(secondHour.getStartTime())) {
          if (firstHour.getEndTime().equals(secondHour.getEndTime())) {
            matched = true;
            break;
          }
        }
      }

      assertTrue(matched);
    }
  }
}
