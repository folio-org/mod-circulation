package org.folio.circulation.infrastructure.storage.loans;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Pins the {@code due_at} wire format against the regex mod-circulation-storage
 * validates it with. The two modules share no artifact, and the in-process API
 * tests would not catch a drift (the fake parses with {@code ZonedDateTime.parse}).
 */
class AnonymizationDueDateStorageRepositoryTest {

  /** Copied from {@code AnonymizationDueDateService.ISO_INSTANT}. */
  private static final Pattern STORAGE_ISO_INSTANT =
    Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?(Z|[+-]\\d{2}:?\\d{2})");

  @Test
  void formatsAsCanonicalUtcMillis() {
    final ZonedDateTime dueAt =
      ZonedDateTime.of(2021, 5, 15, 8, 15, 43, 123_000_000, ZoneOffset.UTC);

    assertThat(AnonymizationDueDateStorageRepository.formatDueAt(dueAt),
      is("2021-05-15T08:15:43.123Z"));
  }

  @Test
  void convertsToUtcRatherThanEmittingAnOffset() {
    final ZonedDateTime berlin =
      ZonedDateTime.of(2021, 5, 15, 10, 15, 43, 0, ZoneId.of("Europe/Berlin"));

    // 10:15 CEST is 08:15 UTC; the offset must not survive into the wire form.
    assertThat(AnonymizationDueDateStorageRepository.formatDueAt(berlin),
      is("2021-05-15T08:15:43.000Z"));
  }

  @Test
  void everyProducedValueIsAcceptedByTheStorageValidator() {
    final List<ZonedDateTime> dueDates = List.of(
      ZonedDateTime.of(2021, 5, 15, 8, 15, 43, 0, ZoneOffset.UTC),
      ZonedDateTime.of(2021, 5, 15, 8, 15, 43, 999_000_000, ZoneOffset.UTC),
      ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
      ZonedDateTime.of(2021, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC),
      ZonedDateTime.of(2021, 5, 15, 10, 15, 43, 0, ZoneId.of("Europe/Berlin")),
      ZonedDateTime.of(2021, 5, 15, 3, 15, 43, 0, ZoneId.of("America/New_York")),
      // Nanosecond precision must be truncated, not widened past three digits.
      ZonedDateTime.of(2021, 5, 15, 8, 15, 43, 123_456_789, ZoneOffset.UTC));

    for (ZonedDateTime dueAt : dueDates) {
      final String formatted = AnonymizationDueDateStorageRepository.formatDueAt(dueAt);

      assertTrue(STORAGE_ISO_INSTANT.matcher(formatted).matches(),
        "storage would reject " + formatted + " with a 422, failing the whole sweep");
    }
  }
}
