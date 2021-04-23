package org.folio.circulation.domain;

import static api.support.fixtures.OpeningHourExamples.afternoon;
import static api.support.fixtures.OpeningHourExamples.allDay;
import static api.support.fixtures.OpeningHourExamples.morning;
import static org.folio.circulation.domain.OpeningDay.fromOpeningPeriodJson;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.OpeningPeriod;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;

@RunWith(JUnitParamsRunner.class)
public class OpeningDayTest {

  @Test
  public void getOpeningDayFromOpeningPeriodJsonTest() {
    LocalDate date = new LocalDate("2020-04-08");
    OpeningDay original = createOpeningDay(false, date, UTC);
    OpeningPeriod period = new OpeningPeriod(date, original);
    OpeningDay fromJson = fromOpeningPeriodJson(period.toJson(), UTC);

    assertOpeningDaysEqual(original, fromJson);
  }

  @Test
  public void getOpeningDayFromOpeningPeriodJsonWithNullDateTest() {
    LocalDate date = new LocalDate("2020-04-08");
    OpeningDay original = createOpeningDay(false, date, UTC);
    OpeningPeriod period = new OpeningPeriod(date, original);
    JsonObject originalJson = original.toJson();
    JsonObject periodJson = period.toJson();
    originalJson.remove("date");
    periodJson.remove("openingDay");
    periodJson.put("openingDay", originalJson);
    OpeningDay fromJson = fromOpeningPeriodJson(periodJson, UTC);

    assertOpeningDaysEqual(original, fromJson);
  }

  private OpeningDay createOpeningDay(boolean allDay, LocalDate date,
    DateTimeZone dateTimeZone) {

    return OpeningDay.createOpeningDay(
      allDay ? Collections.singletonList(allDay()) : Arrays.asList(morning(), afternoon()),
      date, allDay, true, dateTimeZone);
  }

  private void assertOpeningDaysEqual(OpeningDay first, OpeningDay second) {
    assertEquals(first.getAllDay(), second.getAllDay());
    assertEquals(first.getDayWithTimeZone().toString(),
      second.getDayWithTimeZone().toString());
    assertEquals(first.getOpen(), second.getOpen());
    assertEquals(first.getOpeningHour().size(),
      second.getOpeningHour().size());

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
