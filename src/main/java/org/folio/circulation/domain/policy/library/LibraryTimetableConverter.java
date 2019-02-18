package org.folio.circulation.domain.policy.library;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class LibraryTimetableConverter {

  private LibraryTimetableConverter() {
  }

  public static LibraryTimetable convertToLibraryTimetable(
    AdjustingOpeningDays adjustingOpeningDays, DateTimeZone zone) {
    if (adjustingOpeningDays == null) {
      return new LibraryTimetable();
    }
    List<OpeningDay> openingDays = new ArrayList<>();
    openingDays.add(adjustingOpeningDays.getPreviousDay());
    openingDays.add(adjustingOpeningDays.getRequestedDay());
    openingDays.add(adjustingOpeningDays.getNextDay());

    List<Interval> openIntervals =
      openingDays.stream()
        .flatMap(day -> getOpenIntervalsForDay(day, zone).stream())
        .collect(Collectors.toList());

    return mergeOpenPeriods(openIntervals);
  }

  private static LibraryTimetable mergeOpenPeriods(List<Interval> openIntervals) {
    if (openIntervals.isEmpty()) {
      return new LibraryTimetable();
    }
    if (openIntervals.size() == 1) {
      return new LibraryTimetable(
        new LibraryInterval(openIntervals.get(0), true)
      );
    }

    List<Interval> mergedOpenIntervals = new LinkedList<>(openIntervals);
    for (int i = 0; i < mergedOpenIntervals.size() - 1; i++) {
      Interval curr = mergedOpenIntervals.get(i);
      Interval next = mergedOpenIntervals.get(i + 1);
      if (curr.abuts(next)) {
        Interval newInterval = mergeIntervals(curr, next);
        mergedOpenIntervals.set(i, newInterval);
        mergedOpenIntervals.remove(i + 1);
      }
    }

    List<LibraryInterval> libraryIntervals = new ArrayList<>();
    for (int i = 0; i < mergedOpenIntervals.size() - 1; i++) {
      Interval curr = mergedOpenIntervals.get(i);
      Interval next = mergedOpenIntervals.get(i + 1);

      libraryIntervals.add(new LibraryInterval(curr, true));
      Interval gap = curr.gap(next);
      if (gap != null) {
        libraryIntervals.add(new LibraryInterval(gap, false));
      }
    }
    Interval lastOpenInterval =
      mergedOpenIntervals.get(mergedOpenIntervals.size() - 1);
    libraryIntervals.add(new LibraryInterval(lastOpenInterval, true));

    return new LibraryTimetable(libraryIntervals);
  }

  private static Interval mergeIntervals(Interval first, Interval second) {
    return new Interval(first.getStart(), second.getEnd());
  }

  private static List<Interval> getOpenIntervalsForDay(OpeningDay day, DateTimeZone zone) {
    if (!day.getOpen()) {
      return Collections.emptyList();
    }
    if (day.getAllDay()) {
      return Collections.singletonList(buildAllDayOpenInterval(day, zone));
    }
    return day.getOpeningHour().stream()
      .map(hour -> buildIntervalFromOpeningHour(day, hour, zone))
      .collect(Collectors.toList());
  }

  private static Interval buildAllDayOpenInterval(
    OpeningDay day, DateTimeZone zone) {
    DateTime startDateTime =
      day.getDate().toDateTime(LocalTime.MIDNIGHT, zone);
    DateTime endDateTime = startDateTime.plusDays(1);
    return new Interval(startDateTime, endDateTime);
  }

  private static Interval buildIntervalFromOpeningHour(
    OpeningDay day, OpeningHour hour, DateTimeZone zone) {
    DateTime startDateTime =
      day.getDate().toDateTime(hour.getStartTime(), zone);
    DateTime endDateTime =
      day.getDate().toDateTime(hour.getEndTime(), zone);
    return new Interval(startDateTime, endDateTime);
  }

}
