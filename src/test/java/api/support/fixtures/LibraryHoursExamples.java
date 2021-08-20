package api.support.fixtures;

import api.support.builders.LibraryHoursBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static api.support.fixtures.CalendarExamples.*;

public class LibraryHoursExamples {

  public static final String CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID = "55555555-2f09-4bc9-8924-3734882d44a3";

  public static final String CASE_CLOSED_LIBRARY_SERVICE_POINT_ID = "66666666-2f09-4bc9-8924-3734882d44a3";
  public static final String CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID = "99999999-2f09-4bc9-8924-3734882d44a3";

  private static final Map<String, LibraryHoursBuilder> fakeLibraryHours = new HashMap<>();

  static {
    fakeLibraryHours.put(CASE_CLOSED_LIBRARY_SERVICE_POINT_ID,
      new LibraryHoursBuilder(Collections.singletonList(getCalendarById(CASE_START_DATE_MONTHS_AGO_AND_END_DATE_THU))));

    fakeLibraryHours.put(CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID,
      new LibraryHoursBuilder(
        Arrays.asList(
          getCalendarById(CASE_START_DATE_MONTHS_AGO_AND_END_DATE_WED),
          getCalendarById(CASE_START_DATE_FRI_AND_END_DATE_NEXT_MONTHS)
        )));
  }

  public static LibraryHoursBuilder getLibraryHoursById(String serviceId) {
    switch (serviceId) {
      case CASE_CLOSED_LIBRARY_SERVICE_POINT_ID:
        return fakeLibraryHours.get(serviceId);
      case CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID:
        return fakeLibraryHours.get(serviceId);
      default:
        return new LibraryHoursBuilder(new ArrayList<>());
    }
  }
}
