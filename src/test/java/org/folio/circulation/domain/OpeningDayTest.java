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
import java.util.List;

import org.junit.jupiter.api.Test;

class OpeningDayTest {

  @Test
  void testGetOpeningDayFromOpeningPeriodJson() {
    LocalDate date = LocalDate.parse("2020-04-08");
    OpeningDay original = createOpeningDay(false, date, UTC);
    OpeningDay fromJson = new OpeningDay(original.toJson(), UTC);

    assertOpeningDaysEqual(original, fromJson);
  }

  private OpeningDay createOpeningDay(boolean allDay, LocalDate date, ZoneId dateTimeZone) {
    List<OpeningHour> openings = allDay
      ? Collections.singletonList(allDay())
      : Arrays.asList(morning(), afternoon());
    
    return new OpeningDay(openings, date, allDay, true, dateTimeZone);
  }

  private void assertOpeningDaysEqual(OpeningDay first, OpeningDay second) {
    assertEquals(first.isAllDay(), second.isAllDay());
    assertEquals(first.getDayWithTimeZone().toString(), second.getDayWithTimeZone().toString());
    assertEquals(first.isOpen(), second.isOpen());
    assertEquals(first.getOpenings().size(), second.getOpenings().size());

    for (OpeningHour firstHour : first.getOpenings()) {
      boolean matched = false;

      for (OpeningHour secondHour : second.getOpenings()) {
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
