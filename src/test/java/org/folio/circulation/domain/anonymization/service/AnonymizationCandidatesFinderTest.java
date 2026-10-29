package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.service.AnonymizationCandidatesFinder.returnedBefore;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.checkers.LoanClosePeriodChecker;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonObject;

class AnonymizationCandidatesFinderTest {
  private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 3, 31, 12, 0, 0, 0, ZoneOffset.UTC);

  @ParameterizedTest
  @MethodSource("periodsAndDates")
  void dateFilterIsNeverStricterThanTheChecker(Period period, ZonedDateTime now) {
    assertDateFilterIsNeverStricterThanTheChecker(period, now, 10);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 13})
  void dateFilterIsNeverStricterThanTheCheckerOnAnyDayForMonths(int months) {
    // 2024 is a leap year
    for (var now = ZonedDateTime.of(2024, 1, 1, 23, 30, 0, 0, ZoneOffset.UTC);
      now.getYear() < 2026; now = now.plusDays(1)) {

      assertDateFilterIsNeverStricterThanTheChecker(Period.months(months), now, 30);
    }
  }

  /**
   * Loans returned before the window are before the cutoff anyway, the checker only accepts
   * loans returned before the date the period was subtracted from now.
   */
  private static void assertDateFilterIsNeverStricterThanTheChecker(Period period,
    ZonedDateTime now, int stepMinutes) {

    var config = LoanAnonymizationConfiguration.from(setting("interval", period.asJson(), false, null));
    var checker = new LoanClosePeriodChecker(period, () -> now);
    ZonedDateTime cutoff = returnedBefore(config, now);

    for (var returned = period.minusDate(now).minusDays(1); returned.isBefore(now)
      && returned.isBefore(period.minusDate(now).plusDays(6));
      returned = returned.plusMinutes(stepMinutes)) {

      if (checker.canBeAnonymized(closedLoan(returned))) {
        assertThat("now " + now + ", returned " + returned + ", cutoff " + cutoff,
          returned.isBefore(cutoff), is(true));
      }
    }
  }

  static Stream<Object[]> periodsAndDates() {
    var periods = Stream.of(Period.minutes(1), Period.hours(5), Period.days(1), Period.weeks(2),
      Period.months(1), Period.months(3), Period.months(13));

    return periods.flatMap(period -> Stream.of(
      NOW,
      ZonedDateTime.of(2026, 3, 1, 0, 30, 0, 0, ZoneOffset.UTC),
      ZonedDateTime.of(2024, 2, 29, 23, 0, 0, 0, ZoneOffset.UTC),
      ZonedDateTime.of(2026, 5, 31, 6, 0, 0, 0, ZoneOffset.UTC),
      ZonedDateTime.of(2025, 2, 28, 23, 59, 0, 0, ZoneOffset.UTC))
      .map(now -> new Object[] { period, now }));
  }

  @ParameterizedTest
  @ValueSource(strings = {"immediately", "interval"})
  void noDateFilterWhenFeesAndFinesAreTimedByTheirOwnRule(String feeFineClosingType) {
    var config = LoanAnonymizationConfiguration.from(
      setting("interval", Period.weeks(1).asJson(), true, feeFineClosingType));

    assertThat(returnedBefore(config, NOW), nullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"never", "Unknown"})
  void dateFilterWhenFeesAndFinesAreNeverAnonymized(String feeFineClosingType) {
    var config = LoanAnonymizationConfiguration.from(
      setting("interval", Period.weeks(1).asJson(), true, feeFineClosingType));

    assertThat(returnedBefore(config, NOW), is(NOW.minusWeeks(1)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"immediately", "never", "sometimes"})
  void noDateFilterWithoutInterval(String loanClosingType) {
    var config = LoanAnonymizationConfiguration.from(
      setting(loanClosingType, Period.weeks(1).asJson(), false, null));

    assertThat(returnedBefore(config, NOW), nullValue());
  }

  @ParameterizedTest
  @MethodSource("unusablePeriods")
  void noDateFilterWithoutUsablePeriod(JsonObject period) {
    var config = LoanAnonymizationConfiguration.from(setting("interval", period, false, null));

    assertThat(returnedBefore(config, NOW), nullValue());
  }

  static Stream<JsonObject> unusablePeriods() {
    return Stream.of(
      new JsonObject(),
      new JsonObject().put("intervalId", "Months"));
  }

  @Test
  void asEarlyAsPossibleUsesItsOwnPeriod() {
    var config = LoanAnonymizationConfiguration.from(
      setting("never", Period.weeks(1).asJson(), false, null)
        .put("userSpecific", Period.days(3).asJson())
        .put("closingType", new JsonObject().put("loan", "never").put("userSpecific", "interval")))
      .forAsEarlyAsPossible();

    assertThat(returnedBefore(config, NOW), is(NOW.minusDays(3)));
  }

  @Test
  void noMarginWithoutMonths() {
    var config = LoanAnonymizationConfiguration.from(
      setting("interval", Period.weeks(2).asJson(), false, null));

    assertThat(returnedBefore(config, NOW), is(NOW.minusWeeks(2)));
  }

  @Test
  void marginOfThreeDaysForMonths() {
    var config = LoanAnonymizationConfiguration.from(
      setting("interval", Period.months(1).asJson(), false, null));

    assertThat(returnedBefore(config, NOW), is(NOW.minusMonths(1).plusDays(3)));
  }

  private static JsonObject setting(String loanClosingType, JsonObject loanPeriod,
    boolean treatEnabled, String feeFineClosingType) {

    return new JsonObject()
      .put("closingType", new JsonObject()
        .put("loan", loanClosingType)
        .put("feeFine", feeFineClosingType))
      .put("loan", loanPeriod)
      .put("feeFine", Period.days(1).asJson())
      .put("treatEnabled", treatEnabled);
  }

  private static Loan closedLoan(ZonedDateTime systemReturnDate) {
    return Loan.from(new JsonObject()
      .put("id", "6d9a0e8f-1e65-4b0e-9e1d-3f2f8c0b6a11")
      .put("status", new JsonObject().put("name", "Closed"))
      .put("systemReturnDate", formatDateTime(systemReturnDate)));
  }
}
