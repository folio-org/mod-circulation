package org.folio.circulation.domain.policy.library;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.folio.circulation.support.Interval;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LibraryTimetableConverterTest {

  /**
   * Each pair in the Integer list is an hour range.
   * List.of(2, 4,  4, 6,  10, 12) is 02:00-04:00, 04:00-06:00, 10:00-12:00.
   */
  @ParameterizedTest
  @MethodSource
  void mergeOpenPeriods(List<Integer> hours, List<Integer> expectedOpenHours) {
    List<Interval> intervals = new ArrayList<>();
    for (int i = 0; i < hours.size(); i += 2) {
      intervals.add(new Interval(
          LocalDateTime.of(2009, 9, 9, hours.get(i), 0),
          LocalDateTime.of(2009, 9, 9, hours.get(i + 1), 0)));
    }

    var libraryTimetable = LibraryTimetableConverter.mergeOpenPeriods(intervals);

    List<Integer> actualOpenHours = new ArrayList<>();
    var libraryInterval = libraryTimetable.getHead();
    while (libraryInterval != null) {
      if (libraryInterval.isOpen()) {
        actualOpenHours.add(libraryInterval.getStartTime().getHour());
        actualOpenHours.add(libraryInterval.getEndTime().getHour());
      }
      libraryInterval = libraryInterval.getNext();
    }
    assertThat(actualOpenHours, is(expectedOpenHours));
  }

  static Stream<Arguments> mergeOpenPeriods() {
    return Stream.of(
        arguments(List.of(), List.of()),
        arguments(List.of(2, 4), List.of(2, 4)),
        arguments(List.of(2, 4,  4, 6), List.of(2, 6)),
        arguments(List.of(2, 4,  4, 6,  6, 8), List.of(2, 8)),  // three periods that are adjacent to each other
        arguments(List.of(2, 4,  4, 6,  7, 8), List.of(2, 6,  7, 8)),
        arguments(List.of(2, 4,  5, 6,  6, 8), List.of(2, 4,  5, 8)),
        arguments(List.of(2, 3,  4, 5,  6, 8), List.of(2, 3,  4, 5,  6, 8)),
        arguments(List.of(2, 3,  3, 4,  5, 6,  7, 8,  8, 9), List.of(2, 4,  5, 6,  7, 9))
        );
  }
}
