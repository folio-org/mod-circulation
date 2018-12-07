package api.support.fixtures;

import api.support.builders.CalendarBuilder;

import java.util.UUID;

public class CalendarExamples {

  public static final String CASE_1_SERVICE_POINT_ID = "11111111-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_2_SERVICE_POINT_ID = "22222222-2f09-4bc9-8924-3734882d44a3";

  public static CalendarBuilder getCalendarById(String serviceId) {
    switch (serviceId) {
      case CASE_1_SERVICE_POINT_ID:
        return new CalendarBuilder(getId(), CASE_1_SERVICE_POINT_ID,
          "Case 1 calendar",
          "2018-11-30T22:00:00.000+0000",
          "2019-01-30T00:00:00.000+0000",
          "[]",
          1);
      case CASE_2_SERVICE_POINT_ID:
        return new CalendarBuilder(getId(), CASE_2_SERVICE_POINT_ID,
          "Case 1 calendar",
          "2018-11-30T22:00:00.000+0000",
          "2019-01-30T00:00:00.000+0000",
          "[]",
          1);
      default:
        return new CalendarBuilder(getId(), serviceId,
          "Default calendar",
          "2018-11-30T22:00:00.000+0000",
          "2019-01-30T00:00:00.000+0000",
          "[]",
          1);
    }
  }

  private static String getId() {
    return UUID.randomUUID().toString();
  }
}
