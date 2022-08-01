package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.support.Interval;

public class LibraryTimetableConverter {

  private LibraryTimetableConverter() {
  }

  public static LibraryTimetable convertToLibraryTimetable(
    AdjacentOpeningDays adjacentOpeningDays, ZoneId zone) {
    if (adjacentOpeningDays == null) {
      return new LibraryTimetable();
    }
    List<OpeningDay> openingDays = new ArrayList<>();
    openingDays.add(adjacentOpeningDays.getPreviousDay());
    openingDays.add(adjacentOpeningDays.getRequestedDay());
    openingDays.add(adjacentOpeningDays.getNextDay());

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

  private static List<Interval> getOpenIntervalsForDay(OpeningDay day, ZoneId zone) {
    if (!day.isOpen()) {
      return Collections.emptyList();
    }
    if (day.isAllDay()) {
      return Collections.singletonList(buildAllDayOpenInterval(day, zone));
    }
    return day.getOpeningHour().stream()
      .map(hour -> buildIntervalFromOpeningHour(day, hour, zone))
      .collect(Collectors.toList());
  }

  private static Interval buildAllDayOpenInterval(OpeningDay day, ZoneId zone) {
    ZonedDateTime startDateTime = atStartOfDay(day.getDate(), zone);
    ZonedDateTime endDateTime = atStartOfDay(startDateTime.plusDays(1));
    return new Interval(startDateTime, endDateTime);
  }

  private static Interval buildIntervalFromOpeningHour(
      OpeningDay day, OpeningHour hour, ZoneId zone) {
    ZonedDateTime startDateTime =
      ZonedDateTime.of(day.getDate(), hour.getStartTime(), zone);
    ZonedDateTime endDateTime =
      ZonedDateTime.of(day.getDate(), hour.getEndTime(), zone);
    return new Interval(startDateTime, endDateTime);
  }

}
