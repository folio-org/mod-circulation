package api.support.fixtures;

import java.time.LocalTime;

import org.folio.circulation.domain.OpeningHour;

public class OpeningHourExamples {
  public static OpeningHour allDay() {
    return new OpeningHour(LocalTime.of(0, 0), LocalTime.of(23, 59));
  }

  public static OpeningHour morning() {
    return new OpeningHour(LocalTime.of(7, 0), LocalTime.of(12, 0));
  }

  public static OpeningHour afternoon() {
    return new OpeningHour(LocalTime.of(13, 30), LocalTime.of(18, 30));
  }
}
