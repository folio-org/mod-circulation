package api.support.fixtures;

import org.folio.circulation.domain.OpeningHour;
import org.joda.time.LocalTime;

public class OpeningHourExamples {
  public static OpeningHour allDay() {
    return new OpeningHour(new LocalTime(0, 0), new LocalTime(23, 59));
  }

  public static OpeningHour beforeNoon() {
    return new OpeningHour(new LocalTime(7, 0), new LocalTime(12, 0));
  }

  public static OpeningHour afterNoon() {
    return new OpeningHour(new LocalTime(13, 30), new LocalTime(18, 30));
  }
}
