package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class AnonymizeLoansTests {
  @Nested
  class WhenAnonymizingAllLoansImmediatelyTests {
    private final AnonymizationCheckersService checker = checker();

    @Test
    void anonymizeClosedLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan(
        when(2021, 5, 1, 15, 11, 27))));

      assertThat(segregatedLoans.size(), is(1));
      // Partition for loans that should be anonymized
      assertThat(anonymizedLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void anonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithClosedFee(
        when(2021, 5, 11, 11, 54, 32))));

      assertThat(segregatedLoans.size(), is(1));
      // Partition for loans that should be annonymized
      assertThat(anonymizedLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansNotAnonymizedImmediately(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansNotAnonymizedImmediately(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansNotAnonymizedImmediately(segregatedLoans).size(), is(1));
    }

    private Set<String> loansNotAnonymizedImmediately(Map<String, Set<String>> segregatedLoans) {
      return segregatedLoans.get("anonymizeImmediately");
    }

    private AnonymizationCheckersService checker() {
      // Fee fines closing type is deliberately different to make sure that it is ignored when
      // loans with fees should not be treated differently
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(ClosingType.IMMEDIATELY, ClosingType.NEVER,
          false, null, null), ClockUtil::getZonedDateTime);
    }
  }

  @Nested
  class WhenAnonymizingLoansWithFeesImmediatelyTests {
    private final AnonymizationCheckersService checker = checker();

    @Test
    void anonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithClosedFee(
        when(2021, 5, 11, 11, 54, 32))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(anonymizedLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    private Set<String> loansWithFeesOrFinesNotAnonymized(Map<String, Set<String>> segregatedLoans) {
      return segregatedLoans.get("feesAndFinesOpen");
    }

    private AnonymizationCheckersService checker() {
      // General closing type is deliberately different to make sure that the
      // loans with fees closing type is definitely used
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(ClosingType.NEVER, ClosingType.IMMEDIATELY,
          true, null, null), ClockUtil::getZonedDateTime);
    }
  }

  @Nested
  class WhenNeverAnonymizingLoansTests {
    private final AnonymizationCheckersService checker = checker();

    @Test
    void doNotAnonymizeLoanClosedWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan(
        when(2021, 5, 1, 15, 11, 27))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(neverAnonymizeLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(neverAnonymizeLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithClosedFee(when(2021, 5, 11, 11, 54, 32))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    private Set<String> loansWithFeesOrFinesNotAnonymized(Map<String, Set<String>> segregatedLoans) {
      return segregatedLoans.get("neverAnonymizeLoansWithFeesAndFines");
    }

    private Set<String> neverAnonymizeLoans(Map<String, Set<String>> segregatedLoans) {
      return segregatedLoans.get("neverAnonymizeLoans");
    }

    private AnonymizationCheckersService checker() {
      // Fee fines closing type is deliberately different to make sure that it is ignored when
      // loans with fees should not be treated differently
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(ClosingType.NEVER, ClosingType.NEVER,
          true, null, null), ClockUtil::getZonedDateTime);
    }
  }

  @Nested
  class WhenManuallyAnonymizingLoansTests {
    private final AnonymizationCheckersService checker = checker();

    @Test
    void anonymizeLoanClosedWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan(
        when(2021, 5, 1, 15, 11, 27))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(anonymizedLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(anonymizedLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithClosedFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithClosedFee(when(2021, 5, 11, 11, 54, 32))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(),
        is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    private Set<String> loansWithFeesOrFinesNotAnonymized(Map<String, Set<String>> segregatedLoans) {
      return segregatedLoans.get("haveAssociatedFeesAndFines");
    }

    private AnonymizationCheckersService checker() {
      // Manual anonymization is triggered by providing no config
      return new AnonymizationCheckersService(null, ClockUtil::getZonedDateTime);
    }
  }

  @Nested
  class WhenAnonymizingLoansClosedEarlierTests {
    private final AnonymizationCheckersService checker = checker();

    @Test
    void anonymizeLoanClosedMoreThanOneWeekAgo() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan(
        when(2021, 5, 3, 10, 23, 55))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(anonymizedLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeLoanClosedLessThanOneWeekAgo() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan(
        when(2021, 5, 9, 7, 1, 45))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansClosedToSoonToAnonymize(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithNoReturnDate() {
      final var segregatedLoans = checker.segregateLoans(List.of(closedLoan(null)));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansClosedToSoonToAnonymize(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithNoFees() {
      final var segregatedLoans = checker.segregateLoans(List.of(openLoan()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansClosedToSoonToAnonymize(segregatedLoans).size(), is(1));
    }

    @Test
    void anonymizeClosedLoanWithFeeClosedEarlierThanLastWeek() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithClosedFee(when(2021, 5, 1, 15, 11, 27))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(anonymizedLoans(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithFeesClosedWithinTheLastWeek() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithClosedFee(when(2021, 5, 11, 11, 54, 32))));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithClosedFeeWithoutAClosedDate() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithClosedFee(null)));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeClosedLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(closedLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    @Test
    void doNotAnonymizeOpenLoanWithOpenFees() {
      final var segregatedLoans = checker.segregateLoans(
        List.of(openLoanWithOpenFee()));

      assertThat(segregatedLoans.size(), is(1));
      assertThat(loansWithFeesOrFinesNotAnonymized(segregatedLoans).size(), is(1));
    }

    private Set<String> loansWithFeesOrFinesNotAnonymized(Map<String, Set<String>> segregatedLoans) {
      return segregatedLoans.get("intervalAfterFeesAndFinesCloseNotPassed");
    }

    private Set<String> loansClosedToSoonToAnonymize(Map<String, Set<String>> segregatedLoans) {
      return segregatedLoans.get("loanClosedPeriodNotPassed");
    }

    private AnonymizationCheckersService checker() {
      // Fee fines closing type is deliberately different to make sure that it is ignored when
      // loans with fees should not be treated differently
      return new AnonymizationCheckersService(
        new LoanAnonymizationConfiguration(ClosingType.INTERVAL, ClosingType.INTERVAL,
          true, Period.weeks(1),  Period.weeks(1)),
        () -> ZonedDateTime.of(2021, 5, 15, 8, 15, 43, 0, ZoneId.of("UTC")));
    }
  }

  private Set<String> anonymizedLoans(Map<String, Set<String>> segregatedLoans) {
    return segregatedLoans.get("_");
  }

  private ZonedDateTime when(int year, int month, int day, int hour, int minute, int second) {
    return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.of("UTC"));
  }

  private Loan openLoan() {
    return loan("Open", null);
  }

  private Loan openLoanWithOpenFee() {
    return loan("Open", null)
      .withAccounts(List.of(openFee()));
  }

  private Loan closedLoan(ZonedDateTime returnDate) {
    return loan("Closed", returnDate);
  }

  private Loan closedLoanWithClosedFee(ZonedDateTime feeClosedDate) {
    return loan("Closed", null)
      .withAccounts(List.of(closedFee(feeClosedDate)));
  }

  private Loan closedLoanWithOpenFee() {
    return loan("Open", null)
      .withAccounts(List.of(openFee()));
  }

  private Loan loan(String status, ZonedDateTime systemReturnDate) {
    final var json = new JsonObject();

    write(json, "id", UUID.randomUUID());
    writeByPath(json, status, "status", "name");
    write(json, "systemReturnDate", systemReturnDate);

    return Loan.from(json);
  }

  private Account openFee() {
    return fee("Open", List.of());
  }

  private Account closedFee(ZonedDateTime feeClosedDate) {
    final var json = new JsonObject();

    write(json, "balance", 0.0);
    write(json, "dateAction", feeClosedDate);

    final var closureAction = new FeeFineAction(json);

    return fee("Closed", List.of(closureAction));
  }

  private Account fee(String status, List<FeeFineAction> actions) {
    return new Account(null, null, null, null, status, null, actions, null);
  }
}
