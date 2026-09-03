package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.domain.anonymization.config.ClosingType.IMMEDIATELY;
import static org.folio.circulation.domain.anonymization.config.ClosingType.INTERVAL;
import static org.folio.circulation.domain.anonymization.config.ClosingType.NEVER;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.Clock;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Unit tests for {@link AnonymizationEligibility}.
 *
 * <p>{@link AnonymizeLoansTests} covers only the boolean bucketing; these pin
 * the due instant itself, which is what the scheduled job stamps. The last test
 * asserts that {@link AnonymizationEligibility#isDue} and
 * {@link AnonymizationEligibility#dueAt} agree across the policy matrix.</p>
 */
class AnonymizationEligibilityTest {

  private static final ZonedDateTime NOW =
    ZonedDateTime.of(2021, 5, 15, 8, 15, 43, 0, ZoneId.of("UTC"));
  private static final ZonedDateTime RETURNED = NOW.minusDays(30);
  private static final ZonedDateTime FEE_CLOSED = NOW.minusDays(10);

  private final Clock clock = () -> NOW;

  // ---- standard segment ----

  @Test
  void immediatelyIsDueAtTheReturnDate() {
    assertEquals(RETURNED.toInstant(),
      dueAt(closedLoan(RETURNED), standard(IMMEDIATELY)).toInstant());
  }

  /** No return date gives {@code dueAt == now}; the inclusive boundary keeps it eligible. */
  @Test
  void immediatelyWithoutReturnDateIsDueNow() {
    final Loan loan = closedLoan(null);

    assertEquals(NOW.toInstant(), dueAt(loan, standard(IMMEDIATELY)).toInstant());
    assertThat(AnonymizationEligibility.isDue(loan, standard(IMMEDIATELY), clock), is(true));
  }

  @Test
  void intervalIsDueAtTheReturnDatePlusThePeriod() {
    final Period period = Period.weeks(2);

    assertEquals(period.plusDate(RETURNED).toInstant(),
      dueAt(closedLoan(RETURNED), standardInterval(period)).toInstant());
  }

  /** No return date means the interval has no anchor: never eligible. */
  @Test
  void intervalWithoutReturnDateIsNeverDue() {
    assertNever(closedLoan(null), standardInterval(Period.weeks(2)));
  }

  @Test
  void neverPolicyIsNeverDue() {
    assertNever(closedLoan(RETURNED), standard(NEVER));
  }

  @Test
  void unknownClosingTypeIsNeverDue() {
    assertNever(closedLoan(RETURNED), standard(ClosingType.UNKNOWN));
  }

  @Test
  void openLoanIsNeverDue() {
    assertNever(loan("Open", RETURNED), standard(IMMEDIATELY));
  }

  @Test
  void missingLoanOrPolicyIsNeverDue() {
    assertTrue(AnonymizationEligibility.dueAt(null, standard(IMMEDIATELY), clock).isEmpty());
    assertTrue(AnonymizationEligibility.dueAt(closedLoan(RETURNED), null, clock).isEmpty());
  }

  /** A future systemReturnDate is retained until that instant, not stripped early. */
  @Test
  void immediatelyWithAFutureReturnDateIsNotYetDue() {
    final ZonedDateTime future = NOW.plusDays(1);
    final Loan loan = closedLoan(future);

    assertEquals(future.toInstant(), dueAt(loan, standard(IMMEDIATELY)).toInstant());
    assertThat(AnonymizationEligibility.isDue(loan, standard(IMMEDIATELY), clock), is(false));
  }

  // ---- fee/fine segment ----

  @Test
  void feeSegmentImmediatelyIsDueAtTheFeeCloseDate() {
    assertEquals(FEE_CLOSED.toInstant(),
      dueAt(closedLoanWithClosedFees(FEE_CLOSED), fee(IMMEDIATELY)).toInstant());
  }

  @Test
  void feeSegmentImmediatelyUsesTheLatestOfSeveralCloseDates() {
    final ZonedDateTime latest = NOW.minusDays(2);
    final Loan loan = closedLoanWithClosedFees(NOW.minusDays(20), latest, NOW.minusDays(9));

    assertEquals(latest.toInstant(), dueAt(loan, fee(IMMEDIATELY)).toInstant());
  }

  @Test
  void feeSegmentIntervalIsDueAtTheFeeCloseDatePlusThePeriod() {
    final Period period = Period.weeks(1);

    assertEquals(period.plusDate(FEE_CLOSED).toInstant(),
      dueAt(closedLoanWithClosedFees(FEE_CLOSED), feeInterval(period)).toInstant());
  }

  /** Not computable while fees are open; the fee-close hook re-evaluates it. */
  @Test
  void feeSegmentWithOpenFeesIsNeverDue() {
    assertNever(closedLoanWithOpenFee(), fee(IMMEDIATELY));
    assertNever(closedLoanWithOpenFee(), feeInterval(Period.weeks(1)));
  }

  @Test
  void feeSegmentNeverIsNeverDue() {
    assertNever(closedLoanWithClosedFees(FEE_CLOSED), fee(NEVER));
  }

  // ---- segment selection ----

  @Test
  void feesAreIgnoredWhenThePolicyDoesNotTreatThemDifferently() {
    // Fee closing type is NEVER, but treatEnabled is false, so the standard
    // segment governs: the loan is due at its return date, not never.
    final LoanAnonymizationConfiguration policy =
      new LoanAnonymizationConfiguration(IMMEDIATELY, NEVER, false, null, null);

    assertEquals(RETURNED.toInstant(),
      dueAt(closedLoanWithClosedFees(FEE_CLOSED), policy).toInstant());
  }

  @Test
  void loanWithoutFeesUsesTheStandardSegmentEvenWhenTreatingFeesDifferently() {
    // Fee closing type is NEVER and treatEnabled is true, but the loan carries
    // no fees, so the standard segment still governs.
    final LoanAnonymizationConfiguration policy =
      new LoanAnonymizationConfiguration(IMMEDIATELY, NEVER, true, null, null);

    assertEquals(RETURNED.toInstant(),
      dueAt(closedLoan(RETURNED), policy).toInstant());
  }

  // ---- retention reasons ----

  /** The six reason strings are the API's not-anonymized buckets; pin them against a rename. */
  @Test
  void reasonsMatchTheGoverningSegment() {
    final Loan plainLoan = closedLoan(RETURNED);
    final Loan feeLoan = closedLoanWithClosedFees(FEE_CLOSED);

    assertThat(AnonymizationEligibility.reason(plainLoan, standard(IMMEDIATELY)),
      is("anonymizeImmediately"));
    assertThat(AnonymizationEligibility.reason(plainLoan, standardInterval(Period.weeks(1))),
      is("loanClosedPeriodNotPassed"));
    assertThat(AnonymizationEligibility.reason(plainLoan, standard(NEVER)),
      is("neverAnonymizeLoans"));

    assertThat(AnonymizationEligibility.reason(feeLoan, fee(IMMEDIATELY)),
      is("feesAndFinesOpen"));
    assertThat(AnonymizationEligibility.reason(feeLoan, feeInterval(Period.weeks(1))),
      is("intervalAfterFeesAndFinesCloseNotPassed"));
    assertThat(AnonymizationEligibility.reason(feeLoan, fee(NEVER)),
      is("neverAnonymizeLoansWithFeesAndFines"));
  }

  /** {@code isDue} is true exactly when {@code dueAt} is present and has arrived. */
  @Test
  void isDueAgreesWithDueAtAcrossThePolicyMatrix() {
    final List<Loan> loans = List.of(
      closedLoan(RETURNED),
      closedLoan(NOW.minusMinutes(1)),
      closedLoan(NOW.plusDays(1)),
      closedLoanWithClosedFees(FEE_CLOSED),
      closedLoanWithOpenFee(),
      loan("Open", RETURNED));

    final List<LoanAnonymizationConfiguration> policies = List.of(
      standard(IMMEDIATELY),
      standardInterval(Period.weeks(1)),
      standard(NEVER),
      fee(IMMEDIATELY),
      feeInterval(Period.weeks(1)),
      fee(NEVER));

    for (Loan loan : loans) {
      for (LoanAnonymizationConfiguration policy : policies) {
        final Optional<ZonedDateTime> due =
          AnonymizationEligibility.dueAt(loan, policy, clock);

        final boolean stampSaysDue = due.isPresent() && !NOW.isBefore(due.get());
        final boolean kernelSaysDue = AnonymizationEligibility.isDue(loan, policy, clock);

        assertEquals(kernelSaysDue, stampSaysDue,
          "stamp/strip disagree for loan " + loan.getId() + " under " + policy);
      }
    }
  }

  // ---- helpers ----

  private ZonedDateTime dueAt(Loan loan, LoanAnonymizationConfiguration policy) {
    final Optional<ZonedDateTime> due = AnonymizationEligibility.dueAt(loan, policy, clock);

    assertTrue(due.isPresent(), "expected a due instant");

    return due.get();
  }

  private void assertNever(Loan loan, LoanAnonymizationConfiguration policy) {
    assertTrue(AnonymizationEligibility.dueAt(loan, policy, clock).isEmpty(),
      "expected no due instant (never)");
    assertThat(AnonymizationEligibility.isDue(loan, policy, clock), is(false));
  }

  private LoanAnonymizationConfiguration standard(ClosingType loanClosing) {
    return new LoanAnonymizationConfiguration(loanClosing, NEVER, false, null, null);
  }

  private LoanAnonymizationConfiguration standardInterval(Period loanPeriod) {
    return new LoanAnonymizationConfiguration(INTERVAL, NEVER, false, loanPeriod, null);
  }

  private LoanAnonymizationConfiguration fee(ClosingType feeClosing) {
    return new LoanAnonymizationConfiguration(NEVER, feeClosing, true, null, null);
  }

  private LoanAnonymizationConfiguration feeInterval(Period feePeriod) {
    return new LoanAnonymizationConfiguration(NEVER, INTERVAL, true, null, feePeriod);
  }

  private Loan closedLoan(ZonedDateTime returnDate) {
    return loan("Closed", returnDate);
  }

  private Loan closedLoanWithClosedFees(ZonedDateTime... feeCloseDates) {
    return loan("Closed", RETURNED)
      .withAccounts(Arrays.stream(feeCloseDates)
        .map(this::closedFee)
        .toList());
  }

  private Loan closedLoanWithOpenFee() {
    return loan("Closed", RETURNED).withAccounts(List.of(openFee()));
  }

  private Loan loan(String status, ZonedDateTime systemReturnDate) {
    final JsonObject json = new JsonObject();

    write(json, "id", UUID.randomUUID());
    writeByPath(json, status, "status", "name");
    write(json, "systemReturnDate", systemReturnDate);

    return Loan.from(json);
  }

  private Account openFee() {
    return account("Open", List.of());
  }

  private Account closedFee(ZonedDateTime closedDate) {
    final JsonObject json = new JsonObject();

    write(json, "balance", 0.0);
    write(json, "dateAction", closedDate);

    return account("Closed", List.of(new FeeFineAction(json)));
  }

  private Account account(String status, List<FeeFineAction> actions) {
    return new Account(null, null, null, null, status, null, actions, null, null);
  }
}
